package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.dto.media.AiRecognitionResult;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * AI 文件名识别服务 使用 OpenAI 格式的接口来识别和标准化影视文件名
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiFileNameRecognitionService {

  private final SystemConfigService systemConfigService;
  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  // QPM 限制跟踪
  private final Map<String, AtomicInteger> requestCounts = new ConcurrentHashMap<>();
  private final Map<String, LocalDateTime> lastResetTimes = new ConcurrentHashMap<>();

  /**
   * 使用 AI 识别文件名
   *
   * @param originalFileName 原始文件名
   * @param directoryPath 目录路径（可选，用于提供上下文）
   * @return 识别后的标准化文件名，如果识别失败或不可用则返回 null
   */
  public AiRecognitionResult recognizeFileName(String originalFileName, String directoryPath) {
    try {
      Map<String, Object> aiConfig = systemConfigService.getAiConfig();

      // 检查是否启用 AI 识别
      boolean enabled = (Boolean) aiConfig.getOrDefault("enabled", false);
      if (!enabled) {
        log.debug("AI 识别功能未启用，跳过文件名识别: {}", originalFileName);
        return null;
      }

      // 检查必要配置
      String baseUrl = (String) aiConfig.get("baseUrl");
      String apiKey = (String) aiConfig.get("apiKey");
      String model = (String) aiConfig.getOrDefault("model", "gpt-3.5-turbo");

      if (baseUrl == null
          || baseUrl.trim().isEmpty()
          || apiKey == null
          || apiKey.trim().isEmpty()) {
        log.warn(
            "AI 识别配置不完整，跳过文件名识别: baseUrl={}, apiKey={}", baseUrl, apiKey != null ? "***" : null);
        return null;
      }

      // 等待 QPM 限制
      waitForQpmLimit(aiConfig);

      // 构建输入文本
      String inputText = buildInputText(originalFileName, directoryPath);

      // 调用 AI 接口
      AiRecognitionResult result = callAiApi(baseUrl, apiKey, model, aiConfig, inputText);

      if (result != null && result.isSuccess()) {
        log.info("AI 识别成功: {} -> {}", originalFileName, result);
        return result;
      } else {
        log.info(
            "AI 无法识别文件名: {} -> {}", originalFileName, result != null ? result.getReason() : "未知错误");
        return result;
      }

    } catch (Exception e) {
      log.error("AI 文件名识别失败: {}", originalFileName, e);
      return null;
    }
  }

  /** 等待 QPM 限制，智能控制请求速度 */
  private void waitForQpmLimit(Map<String, Object> aiConfig) {
    int qpmLimit = (Integer) aiConfig.getOrDefault("qpmLimit", 60);
    String key = "ai_requests";

    LocalDateTime now = LocalDateTime.now();
    LocalDateTime lastReset = lastResetTimes.get(key);

    // 如果超过一分钟，重置计数器
    if (lastReset == null || ChronoUnit.MINUTES.between(lastReset, now) >= 1) {
      requestCounts.put(key, new AtomicInteger(0));
      lastResetTimes.put(key, now);
      lastReset = now;
    }

    AtomicInteger count = requestCounts.get(key);
    long secondsElapsed = ChronoUnit.SECONDS.between(lastReset, now);

    // 如果达到限制，等待到下一分钟
    if (count.get() >= qpmLimit) {
      long secondsToWait = 60 - secondsElapsed;
      if (secondsToWait > 0) {
        log.info("已达到 QPM 限制 ({}/{}), 等待 {} 秒后继续刮削", count.get(), qpmLimit, secondsToWait);
        try {
          Thread.sleep(secondsToWait * 1000);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          log.warn("等待 QPM 限制时被中断", e);
          return;
        }

        // 重置计数器
        requestCounts.put(key, new AtomicInteger(0));
        lastResetTimes.put(key, LocalDateTime.now());
      }
    } else {
      // 智能速度控制：如果请求过快，适当延迟
      double expectedRate = (double) qpmLimit / 60.0; // 每秒期望请求数
      double actualRate = secondsElapsed > 0 ? (double) count.get() / secondsElapsed : 0;

      if (actualRate > expectedRate * 1.2) { // 如果超过期望速度的120%
        long delayMs = (long) (1000 / expectedRate); // 计算延迟时间
        if (delayMs > 100) { // 最小延迟100ms
          log.debug("请求速度过快，延迟 {} ms 以控制速度", delayMs);
          try {
            Thread.sleep(delayMs);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("速度控制延迟时被中断", e);
            return;
          }
        }
      }
    }

    // 增加请求计数
    count.incrementAndGet();
    log.debug("AI 请求计数: {}/{}, 已用时: {}s", count.get(), qpmLimit, secondsElapsed);
  }

  /** 构建输入文本 */
  private String buildInputText(String originalFileName, String directoryPath) {
    StringBuilder input = new StringBuilder();

    if (directoryPath != null && !directoryPath.trim().isEmpty()) {
      input.append("目录路径: ").append(directoryPath).append("\n");
    }

    input.append("文件名: ").append(originalFileName);

    return input.toString();
  }

  /** 调用 AI API */
  private AiRecognitionResult callAiApi(
      String baseUrl, String apiKey, String model, Map<String, Object> aiConfig, String inputText) {
    try {
      // 构建请求 URL
      String apiUrl =
          baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

      // 构建请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(apiKey);

      // 构建请求体
      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", model);
      requestBody.put("max_tokens", 300); // 增加 token 数量以适应 JSON 格式
      requestBody.put("temperature", 0.1);
      requestBody.put("response_format", Map.of("type", "json_object")); // 强制 JSON 格式（如果模型支持）

      // 构建消息
      Map<String, Object> systemMessage = new HashMap<>();
      systemMessage.put("role", "system");
      systemMessage.put("content", aiConfig.get("prompt"));

      Map<String, Object> userMessage = new HashMap<>();
      userMessage.put("role", "user");
      userMessage.put("content", inputText);

      requestBody.put("messages", new Object[] {systemMessage, userMessage});

      // 发送请求
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
      ResponseEntity<String> response =
          restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.error("AI API 请求失败，状态码: {}, 响应: {}", response.getStatusCode(), response.getBody());
        return null;
      }

      // 解析响应
      JsonNode responseJson = objectMapper.readTree(response.getBody());
      JsonNode choices = responseJson.get("choices");

      if (choices != null && choices.isArray() && choices.size() > 0) {
        JsonNode firstChoice = choices.get(0);
        JsonNode message = firstChoice.get("message");
        if (message != null) {
          JsonNode content = message.get("content");
          if (content != null) {
            String result = content.asText().trim();
            log.debug("AI API 原始响应: {}", result);

            // 解析 JSON 响应
            AiRecognitionResult parsedResult = parseJsonResponse(result);
            log.debug("AI API 解析后响应: {}", parsedResult);

            return parsedResult;
          }
        }
      }

      log.warn("AI API 响应格式异常: {}", response.getBody());
      return null;

    } catch (Exception e) {
      log.error("调用 AI API 失败", e);
      return null;
    }
  }

  /**
   * 解析 AI 的 JSON 响应
   *
   * @param rawResponse AI 的原始响应
   * @return 解析后的AiRecognitionResult对象，如果失败则返回 null
   */
  private AiRecognitionResult parseJsonResponse(String rawResponse) {
    if (rawResponse == null || rawResponse.trim().isEmpty()) {
      log.warn("AI 响应为空");
      return null;
    }

    String response = rawResponse.trim();

    try {
      // 尝试提取 JSON 部分
      String jsonContent = extractJsonFromResponse(response);
      if (jsonContent == null) {
        log.warn("无法从响应中提取 JSON，跳过处理: {}", response);
        return null;
      }

      // 解析 JSON
      JsonNode jsonNode = objectMapper.readTree(jsonContent);

      // 检查是否成功
      JsonNode successNode = jsonNode.get("success");
      if (successNode == null) {
        log.warn("JSON 响应缺少 success 字段，跳过处理: {}", jsonContent);
        return null;
      }

      boolean success = successNode.asBoolean();
      AiRecognitionResult result = new AiRecognitionResult().setSuccess(success);

      // 提取type字段
      JsonNode typeNode = jsonNode.get("type");
      if (typeNode != null && !typeNode.isNull()) {
        result.setType(typeNode.asText());
      }

      if (success) {
        // 成功情况，检查是新格式还是旧格式
        JsonNode titleNode = jsonNode.get("title");
        if (titleNode != null && !titleNode.isNull() && !titleNode.asText().trim().isEmpty()) {
          // 新格式：分离字段
          result.setTitle(titleNode.asText().trim());

          JsonNode yearNode = jsonNode.get("year");
          if (yearNode != null && !yearNode.isNull()) {
            result.setYear(yearNode.asText().trim());
          }

          JsonNode seasonNode = jsonNode.get("season");
          if (seasonNode != null && !seasonNode.isNull()) {
            result.setSeason(seasonNode.asInt());
          }

          JsonNode episodeNode = jsonNode.get("episode");
          if (episodeNode != null && !episodeNode.isNull()) {
            result.setEpisode(episodeNode.asInt());
          }

          log.debug("成功解析 JSON 响应（新格式）: {}", result);
          return result;
        } else {
          // 旧格式：filename字段
          JsonNode filenameNode = jsonNode.get("filename");
          if (filenameNode != null && !filenameNode.isNull()) {
            String filename = filenameNode.asText().trim();
            if (!filename.isEmpty()) {
              result.setFilename(filename);
              log.debug("成功解析 JSON 响应（旧格式）: {}", result);
              return result;
            }
          }
          log.warn("JSON 响应标记成功但缺少有效的title或filename字段，跳过处理: {}", jsonContent);
          return null;
        }
      } else {
        // 失败情况，提取失败原因
        JsonNode reasonNode = jsonNode.get("reason");
        String reason = reasonNode != null ? reasonNode.asText() : "未知原因";
        result.setReason(reason);
        log.info("AI 无法解析文件名: {}", reason);
        return result;
      }

    } catch (Exception e) {
      log.warn("解析 JSON 响应失败，跳过处理: {}, 错误: {}", response, e.getMessage());
      return null;
    }
  }

  /**
   * 从响应中提取 JSON 内容
   *
   * @param response 原始响应
   * @return JSON 字符串，如果未找到则返回 null
   */
  protected String extractJsonFromResponse(String response) {
    if (response == null || response.trim().isEmpty()) {
      return null;
    }

    String content = response.trim();

    // 处理 Markdown 代码块格式 ```json ... ``` 或 ``` ... ```
    if (content.contains("```")) {
      // 查找第一个代码块开始
      int codeBlockStart = content.indexOf("```");
      if (codeBlockStart != -1) {
        // 确定内容开始位置
        int contentStart;
        String afterTicks = content.substring(codeBlockStart + 3);

        // 检查是否是 ```json 格式
        if (afterTicks.startsWith("json")) {
          // 跳过 "json" 和可能的换行符
          contentStart = codeBlockStart + 7;
          if (contentStart < content.length() && content.charAt(contentStart) == '\n') {
            contentStart++;
          } else if (contentStart < content.length() && content.charAt(contentStart) == '\r') {
            contentStart++;
            if (contentStart < content.length() && content.charAt(contentStart) == '\n') {
              contentStart++;
            }
          }
        } else {
          // 普通的 ``` 格式，查找换行符
          int newlinePos = content.indexOf('\n', codeBlockStart + 3);
          if (newlinePos != -1) {
            contentStart = newlinePos + 1;
          } else {
            contentStart = codeBlockStart + 3;
          }
        }

        // 查找代码块结束
        int codeBlockEnd = content.indexOf("```", contentStart);
        if (codeBlockEnd != -1) {
          content = content.substring(contentStart, codeBlockEnd).trim();
        } else {
          // 如果没有找到结束标记，取到字符串末尾
          content = content.substring(contentStart).trim();
        }

        log.debug("从 Markdown 代码块中提取内容: {}", content);
      }
    }

    // 查找 JSON 开始和结束位置
    int jsonStart = content.indexOf('{');
    int jsonEnd = content.lastIndexOf('}');

    if (jsonStart == -1 || jsonEnd == -1 || jsonStart >= jsonEnd) {
      return null;
    }

    String jsonContent = content.substring(jsonStart, jsonEnd + 1);
    log.debug("提取的 JSON 内容: {}", jsonContent);

    return jsonContent;
  }

  /**
   * 验证 AI 配置
   *
   * @param baseUrl API 基础 URL
   * @param apiKey API Key
   * @param model 模型名称
   * @return 验证结果
   */
  public boolean validateAiConfig(String baseUrl, String apiKey, String model) {
    try {
      // 构建请求 URL
      String apiUrl =
          baseUrl.endsWith("/") ? baseUrl + "chat/completions" : baseUrl + "/chat/completions";

      // 构建请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setBearerAuth(apiKey);

      // 构建简单的测试请求体
      Map<String, Object> requestBody = new HashMap<>();
      requestBody.put("model", model);
      requestBody.put("max_tokens", 50);
      requestBody.put("temperature", 0.1);

      // 使用简单的测试消息
      Map<String, Object> systemMessage = new HashMap<>();
      systemMessage.put("role", "system");
      systemMessage.put("content", "请返回 JSON 格式: {\"test\": \"success\"}");

      Map<String, Object> userMessage = new HashMap<>();
      userMessage.put("role", "user");
      userMessage.put("content", "测试");

      requestBody.put("messages", new Object[] {systemMessage, userMessage});

      // 尝试添加 JSON 格式要求（某些模型支持）
      try {
        requestBody.put("response_format", Map.of("type", "json_object"));
      } catch (Exception e) {
        // 如果不支持，忽略这个参数
        log.debug("模型可能不支持 response_format 参数");
      }

      // 发送测试请求
      HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
      ResponseEntity<String> response =
          restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

      boolean success = response.getStatusCode().is2xxSuccessful();
      if (success) {
        log.info("AI 配置验证成功: {}", model);
      } else {
        log.warn("AI 配置验证失败，状态码: {}", response.getStatusCode());
      }

      return success;

    } catch (Exception e) {
      log.error("验证 AI 配置失败: {}", e.getMessage(), e);
      return false;
    }
  }
}
