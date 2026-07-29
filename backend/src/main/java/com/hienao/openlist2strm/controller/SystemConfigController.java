package com.hienao.openlist2strm.controller;

import com.hienao.openlist2strm.dto.ApiResponse;
import com.hienao.openlist2strm.service.AiFileNameRecognitionService;
import com.hienao.openlist2strm.service.SystemConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 系统配置管理控制器
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
@Tag(name = "系统配置管理", description = "系统配置的读取和保存接口")
public class SystemConfigController {

  private final SystemConfigService systemConfigService;
  private final AiFileNameRecognitionService aiFileNameRecognitionService;

  /** 获取系统配置 */
  @GetMapping("/config")
  @Operation(summary = "获取系统配置", description = "获取当前系统配置信息")
  public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemConfig() {
    try {
      Map<String, Object> config = systemConfigService.getSystemConfig();
      return ResponseEntity.ok(ApiResponse.success(config));
    } catch (Exception e) {
      log.error("获取系统配置失败", e);
      return ResponseEntity.ok(ApiResponse.error("获取系统配置失败: " + e.getMessage()));
    }
  }

  /** 保存系统配置 */
  @PostMapping("/config")
  @Operation(summary = "保存系统配置", description = "保存系统配置信息")
  public ResponseEntity<ApiResponse<String>> saveSystemConfig(
      @RequestBody Map<String, Object> config) {
    try {
      // 验证媒体文件后缀配置
      if (config.containsKey("mediaExtensions")) {
        Object mediaExtensions = config.get("mediaExtensions");
        if (!(mediaExtensions instanceof List)) {
          return ResponseEntity.ok(ApiResponse.error("mediaExtensions必须是数组类型"));
        }

        @SuppressWarnings("unchecked")
        List<String> extensions = (List<String>) mediaExtensions;

        // 验证后缀格式
        for (String ext : extensions) {
          if (!ext.startsWith(".")) {
            return ResponseEntity.ok(ApiResponse.error("文件后缀必须以.开头: " + ext));
          }
        }
      }

      // 验证TMDB配置
      if (config.containsKey("tmdb")) {
        Object tmdbConfig = config.get("tmdb");
        if (!(tmdbConfig instanceof Map)) {
          return ResponseEntity.ok(ApiResponse.error("tmdb配置必须是对象类型"));
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> tmdb = (Map<String, Object>) tmdbConfig;

        // 验证API Key
        if (tmdb.containsKey("apiKey")) {
          String apiKey = (String) tmdb.get("apiKey");
          if (apiKey != null
              && !apiKey.trim().isEmpty()
              && !systemConfigService.validateTmdbApiKey(apiKey)) {
            return ResponseEntity.ok(ApiResponse.error("TMDB API Key格式不正确"));
          }
        }
      }

      systemConfigService.saveSystemConfig(config);
      return ResponseEntity.ok(ApiResponse.success("配置保存成功"));
    } catch (Exception e) {
      log.error("保存系统配置失败", e);
      return ResponseEntity.ok(ApiResponse.error("保存系统配置失败: " + e.getMessage()));
    }
  }

  /** 测试 AI 配置 */
  @PostMapping("/test-ai-config")
  @Operation(summary = "测试 AI 配置", description = "测试 AI 识别配置是否有效")
  public ResponseEntity<ApiResponse<String>> testAiConfig(
      @RequestBody Map<String, Object> testConfig) {
    try {
      String baseUrl = (String) testConfig.get("baseUrl");
      String apiKey = (String) testConfig.get("apiKey");
      String model = (String) testConfig.get("model");

      if (baseUrl == null || baseUrl.trim().isEmpty()) {
        return ResponseEntity.ok(ApiResponse.error("API 基础 URL 不能为空"));
      }

      if (apiKey == null || apiKey.trim().isEmpty()) {
        return ResponseEntity.ok(ApiResponse.error("API Key 不能为空"));
      }

      if (model == null || model.trim().isEmpty()) {
        return ResponseEntity.ok(ApiResponse.error("模型名称不能为空"));
      }

      // 调用 AI 服务验证配置
      boolean isValid = aiFileNameRecognitionService.validateAiConfig(baseUrl, apiKey, model);

      if (isValid) {
        return ResponseEntity.ok(ApiResponse.success("AI 配置测试成功"));
      } else {
        return ResponseEntity.ok(ApiResponse.error("AI 配置测试失败，请检查配置信息"));
      }

    } catch (Exception e) {
      log.error("测试 AI 配置失败", e);
      return ResponseEntity.ok(ApiResponse.error("测试 AI 配置失败: " + e.getMessage()));
    }
  }

  /** 获取默认 AI 提示词 */
  @GetMapping("/default-ai-prompt")
  @Operation(summary = "获取默认 AI 提示词", description = "获取系统默认的 AI 识别提示词")
  public ResponseEntity<ApiResponse<String>> getDefaultAiPrompt() {
    try {
      String defaultPrompt = systemConfigService.getDefaultAiPrompt();
      return ResponseEntity.ok(ApiResponse.success(defaultPrompt));
    } catch (Exception e) {
      log.error("获取默认 AI 提示词失败", e);
      return ResponseEntity.ok(ApiResponse.error("获取默认 AI 提示词失败: " + e.getMessage()));
    }
  }
}
