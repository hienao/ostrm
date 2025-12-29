package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.constant.AppConstants;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.util.UrlEncoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * OpenList API服务类
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenlistApiService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  /** OpenList API响应数据结构 */
  @Data
  public static class OpenlistApiResponse {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private OpenlistData data;
  }

  /** OpenList数据结构 */
  @Data
  public static class OpenlistData {
    @JsonProperty("files")
    private List<OpenlistFile> files;

    @JsonProperty("readme")
    private String readme;
  }

  /** OpenList文件信息 */
  @Data
  public static class OpenlistFile {
    @JsonProperty("name")
    private String name;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("type")
    private String type; // "file" 或 "folder"

    @JsonProperty("url")
    private String url;

    @JsonProperty("path")
    private String path;

    @JsonProperty("modified")
    private String modified;

    @JsonProperty("sign")
    private String sign;
  }

  /** Alist API响应数据结构 */
  @Data
  public static class AlistApiResponse {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private AlistData data;
  }

  /** Alist数据结构 */
  @Data
  public static class AlistData {
    @JsonProperty("content")
    private List<AlistFile> content;

    @JsonProperty("total")
    private Integer total;

    @JsonProperty("readme")
    private String readme;

    @JsonProperty("header")
    private String header;

    @JsonProperty("write")
    private Boolean write;

    @JsonProperty("provider")
    private String provider;
  }

  /** Alist文件信息 */
  @Data
  public static class AlistFile {
    @JsonProperty("name")
    private String name;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("is_dir")
    private Boolean isDir;

    @JsonProperty("modified")
    private String modified;

    @JsonProperty("created")
    private String created;

    @JsonProperty("sign")
    private String sign;

    @JsonProperty("thumb")
    private String thumb;

    @JsonProperty("type")
    private Integer type;

    @JsonProperty("hashinfo")
    private String hashinfo;

    @JsonProperty("hash_info")
    private Object hashInfo;
  }

  /**
   * 递归获取目录下的所有文件和目录
   *
   * @param config OpenList配置
   * @param path 目录路径
   * @return 所有文件和目录列表
   */
  public List<OpenlistFile> getAllFilesRecursively(OpenlistConfig config, String path) {
    List<OpenlistFile> allFiles = new ArrayList<>();
    getAllFilesRecursively(config, path, allFiles);
    return allFiles;
  }

  /**
   * 递归获取目录下的所有文件和目录（内部方法）
   *
   * @param config OpenList配置
   * @param path 目录路径
   * @param allFiles 累积的文件列表
   */
  private void getAllFilesRecursively(
      OpenlistConfig config, String path, List<OpenlistFile> allFiles) {
    try {
      log.info("正在获取目录: {}", path);

      // 调用OpenList API获取当前目录内容
      List<OpenlistFile> files = getDirectoryContents(config, path);

      for (OpenlistFile file : files) {
        // 添加到结果列表
        allFiles.add(file);

        // 如果是目录，递归获取子目录内容
        if ("folder".equals(file.getType())) {
          String subPath = file.getPath();
          if (subPath == null || subPath.isEmpty()) {
            subPath = path + "/" + file.getName();
          }
          getAllFilesRecursively(config, subPath, allFiles);
        }
      }

    } catch (Exception e) {
      log.error("获取目录内容失败: {}, 错误: {}", path, e.getMessage(), e);
      throw new BusinessException("获取目录内容失败: " + path + ", 错误: " + e.getMessage(), e);
    }
  }

  /**
   * 获取指定目录的内容
   *
   * @param config OpenList配置
   * @param path 目录路径
   * @return 目录内容列表
   */
  public List<OpenlistFile> getDirectoryContents(OpenlistConfig config, String path) {
    try {
      // 构建请求URL - 使用OpenList配置中的baseUrl作为API服务器地址
      String apiUrl = config.getBaseUrl();
      if (!apiUrl.endsWith("/")) {
        apiUrl += "/";
      }
      apiUrl += "api/fs/list";

      UriComponentsBuilder builder =
          UriComponentsBuilder.fromHttpUrl(apiUrl).queryParam("path", path);

      String requestUrl = builder.toUriString();
      log.debug("请求URL: {}", requestUrl);

      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("User-Agent", AppConstants.USER_AGENT);
      headers.set("Authorization", config.getToken());

      // 构建请求体
      String requestBody =
          String.format(
              "{\"path\":\"%s\",\"password\":\"\",\"page\":1,\"per_page\":0,\"refresh\":false}",
              path);

      HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

      // 发送请求 - 使用POST方法
      ResponseEntity<String> response =
          restTemplate.exchange(requestUrl, HttpMethod.POST, entity, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("OpenList API请求失败，状态码: " + response.getStatusCode());
      }

      String responseBody = response.getBody();
      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("OpenList API返回空响应");
      }

      log.debug("API响应: {}", responseBody);

      // 解析响应
      AlistApiResponse apiResponse = objectMapper.readValue(responseBody, AlistApiResponse.class);

      if (apiResponse.getCode() == null || !apiResponse.getCode().equals(200)) {
        throw new BusinessException("OpenList API返回错误: " + apiResponse.getMessage());
      }

      if (apiResponse.getData() == null || apiResponse.getData().getContent() == null) {
        log.warn("目录为空或无文件: {}", path);
        return new ArrayList<>();
      }

      // 转换Alist格式到OpenlistFile格式
      List<OpenlistFile> files = new ArrayList<>();
      for (AlistFile alistFile : apiResponse.getData().getContent()) {
        OpenlistFile file = new OpenlistFile();
        file.setName(alistFile.getName());
        file.setSize(alistFile.getSize());
        file.setType(alistFile.getIsDir() ? "folder" : "file");
        file.setModified(alistFile.getModified());
        file.setSign(alistFile.getSign());

        // 构建文件路径
        String filePath = path;
        if (!filePath.endsWith("/")) {
          filePath += "/";
        }
        filePath += alistFile.getName();
        file.setPath(filePath);

        // 构建文件URL - 使用URI类进行智能URL编码
        String fileUrl = buildFileUrl(config.getBaseUrl(), filePath);
        file.setUrl(fileUrl);

        files.add(file);
      }

      log.info("获取到 {} 个文件/目录: {}", files.size(), path);

      return files;

    } catch (Exception e) {
      log.error("调用OpenList API失败: {}, 错误: {}", path, e.getMessage(), e);
      throw new BusinessException("调用OpenList API失败: " + e.getMessage(), e);
    }
  }

  /**
   * 检查文件是否存在
   *
   * @param config OpenList配置
   * @param filePath 文件路径
   * @return 文件是否存在
   */
  public boolean checkFileExists(OpenlistConfig config, String filePath) {
    try {
      // 获取文件所在目录和文件名
      String dirPath = filePath.substring(0, filePath.lastIndexOf('/'));
      String fileName = filePath.substring(filePath.lastIndexOf('/') + 1);

      // 获取目录内容
      List<OpenlistFile> files = getDirectoryContents(config, dirPath);

      // 检查文件是否存在
      return files.stream()
          .anyMatch(file -> "file".equals(file.getType()) && fileName.equals(file.getName()));

    } catch (Exception e) {
      log.debug("检查文件存在性失败: {}, 错误: {}", filePath, e.getMessage());
      return false;
    }
  }

  /**
   * 获取文件内容（使用OpenlistFile对象，包含sign参数）
   *
   * @param config OpenList配置
   * @param file OpenlistFile对象
   * @return 文件内容字节数组
   */
  public byte[] getFileContent(OpenlistConfig config, OpenlistFile file) {
    // 默认使用URL编码（兼容STRM文件写入场景）
    return getFileContent(config, file, true);
  }

  /**
   * 获取文件内容（使用OpenlistFile对象，包含sign参数）
   *
   * @param config OpenList配置
   * @param file OpenlistFile对象
   * @param enableUrlEncoding 是否启用URL编码（false适用于刮削文件下载场景）
   * @return 文件内容字节数组
   */
  public byte[] getFileContent(OpenlistConfig config, OpenlistFile file, boolean enableUrlEncoding) {
    try {
      // 使用OpenlistFile中的url字段，已包含sign参数
      String fileUrl = file.getUrl();
      if (file.getSign() != null && !file.getSign().isEmpty()) {
        // 构建完整URL
        String completeUrl = file.getUrl() + "?sign=" + file.getSign();
        if (enableUrlEncoding) {
          // 使用统一的智能编码，避免双重编码（适用于STRM文件写入场景）
          fileUrl = UrlEncoder.encodeUrlSmart(completeUrl);
        } else {
          // 不进行URL编码（适用于刮削文件下载场景，避免认证问题）
          fileUrl = completeUrl;
        }
      } else {
        if (enableUrlEncoding) {
          // 使用统一编码标准确保中文路径正确处理
          fileUrl = UrlEncoder.encodeUrlSmart(fileUrl);
        } else {
          // 不进行编码，直接使用原始URL
          fileUrl = file.getUrl();
        }
      }

      log.debug("下载文件请求 - 文件名: {}, 完整URL: {}", file.getName(), fileUrl);

      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      if (config.getToken() != null && !config.getToken().isEmpty()) {
        headers.set("Authorization", config.getToken());
        log.debug(
            "[DEBUG] 使用认证Token: {}...",
            config.getToken().substring(0, Math.min(10, config.getToken().length())));
      }

      HttpEntity<String> entity = new HttpEntity<>(headers);

      // 发送GET请求获取文件内容 - 使用URI对象避免Spring将{...}解析为模板变量
      ResponseEntity<byte[]> response =
          restTemplate.exchange(java.net.URI.create(fileUrl), HttpMethod.GET, entity, byte[].class);

      log.debug(
          "文件下载响应 - 状态码: {}, Content-Type: {}, Headers: {}",
          response.getStatusCode(),
          response.getHeaders().getContentType(),
          response.getHeaders());

      // 特殊处理302状态码
      if (response.getStatusCode().value() == 302) {
        log.info(
            "文件下载收到302重定向: {}, URL: {}, Location: {}",
            file.getName(),
            fileUrl,
            response.getHeaders().getLocation());

        // 尝试跟随重定向
        try {
          if (response.getHeaders().getLocation() != null) {
            String redirectUrl = response.getHeaders().getLocation().toString();
            log.info("跟随302重定向到: {}", redirectUrl);

            // 检查重定向URL是否是外部CDN/存储服务
            boolean isExternalRedirect = redirectUrl.contains("ctyunxs.cn") ||
                                       redirectUrl.contains("amazonaws.com") ||
                                       redirectUrl.contains("aliyuncs.com") ||
                                       !redirectUrl.contains(config.getBaseUrl());

          // 重新构建请求头
            HttpHeaders redirectHeaders = new HttpHeaders();
            redirectHeaders.set("User-Agent", AppConstants.USER_AGENT);

            // 只有重定向到同一域名时才发送认证头
            if (!isExternalRedirect && config.getToken() != null && !config.getToken().isEmpty()) {
              redirectHeaders.set("Authorization", config.getToken());
              log.debug("重定向到同一域名，携带认证头");
            } else {
              log.debug("重定向到外部CDN/存储服务，不携带认证头");
            }

            HttpEntity<String> redirectEntity = new HttpEntity<>(redirectHeaders);

          // 发送重定向请求 - 对于外部CDN，直接使用URI避免RestTemplate自动编码
            ResponseEntity<byte[]> redirectResponse;
            if (isExternalRedirect) {
              // 对于外部CDN，使用URI直接请求，避免RestTemplate自动编码导致签名失效
              try {
                java.net.URI uri = java.net.URI.create(redirectUrl);
                redirectResponse = restTemplate.exchange(uri, HttpMethod.GET, redirectEntity, byte[].class);
                log.debug("使用URI直接请求外部CDN，避免自动编码: {}", uri);
              } catch (Exception e) {
                log.error("外部CDN重定向URL构建URI失败，标记下载失败: {}", redirectUrl, e);
                return null;
              }
            } else {
              redirectResponse = restTemplate.exchange(redirectUrl, HttpMethod.GET, redirectEntity, byte[].class);
            }

            log.debug(
                "重定向下载响应 - 状态码: {}, Content-Type: {}",
                redirectResponse.getStatusCode(),
                redirectResponse.getHeaders().getContentType());

            if (redirectResponse.getStatusCode().is2xxSuccessful()) {
              byte[] content = redirectResponse.getBody();
              if (content != null && content.length > 0) {
                log.info("重定向下载成功: {}, 大小: {} bytes", file.getName(), content.length);
                return content;
              } else {
                log.warn("重定向下载内容为空: {}", file.getName());
                return null;
              }
            } else {
              log.warn("重定向下载失败: {}, 状态码: {}", file.getName(), redirectResponse.getStatusCode());
              return null;
            }
          } else {
            log.warn("收到302重定向但没有Location头: {}", file.getName());
            return null;
          }
        } catch (Exception redirectException) {
          log.error(
              "处理302重定向失败: {}, 错误: {}",
              file.getName(),
              redirectException.getMessage(),
              redirectException);
          return null;
        }
      }

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.warn("文件下载失败: {}, 状态码: {}, URL: {}", file.getName(), response.getStatusCode(), fileUrl);
        return null;
      }

      byte[] content = response.getBody();
      if (content == null || content.length == 0) {
        log.warn("文件内容为空: {}, URL: {}", file.getName(), fileUrl);
        return null;
      }

      // 检测文件内容类型（前几个字节）
      String contentPreview = "";
      if (content.length > 0) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(20, content.length); i++) {
          sb.append(String.format("%02X ", content[i] & 0xFF));
        }
        contentPreview = sb.toString().trim();
      }

      log.debug(
          "文件下载成功 - 文件名: {}, 大小: {} bytes, 前20字节: {}",
          file.getName(),
          content.length,
          contentPreview);
      return content;

    } catch (Exception e) {
      log.error("下载文件异常: {}, 错误: {}", file.getName(), e.getMessage(), e);
      return null;
    }
  }

  /**
   * 获取文件内容（使用文件路径）
   *
   * @param config OpenList配置
   * @param filePath 文件路径
   * @return 文件内容字节数组
   */
  public byte[] getFileContent(OpenlistConfig config, String filePath) {
    try {
      // 构建文件下载URL - 使用URI类进行智能URL编码
      String fileUrl = buildFileUrl(config.getBaseUrl(), filePath);

      log.debug("下载文件请求 - 文件路径: {}, 完整URL: {}", filePath, fileUrl);

      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      if (config.getToken() != null && !config.getToken().isEmpty()) {
        headers.set("Authorization", config.getToken());
        log.debug(
            "[DEBUG] 使用认证Token: {}...",
            config.getToken().substring(0, Math.min(10, config.getToken().length())));
      }

      HttpEntity<String> entity = new HttpEntity<>(headers);

      // 发送GET请求获取文件内容 - 使用URI对象避免Spring将{...}解析为模板变量
      ResponseEntity<byte[]> response =
          restTemplate.exchange(java.net.URI.create(fileUrl), HttpMethod.GET, entity, byte[].class);

      log.debug(
          "文件下载响应 - 状态码: {}, Content-Type: {}, Headers: {}",
          response.getStatusCode(),
          response.getHeaders().getContentType(),
          response.getHeaders());

      // 特殊处理302状态码
      if (response.getStatusCode().value() == 302) {
        log.info(
            "文件下载收到302重定向: {}, URL: {}, Location: {}",
            filePath,
            fileUrl,
            response.getHeaders().getLocation());

        // 尝试跟随重定向
        try {
          if (response.getHeaders().getLocation() != null) {
            String redirectUrl = response.getHeaders().getLocation().toString();
            log.info("跟随302重定向到: {}", redirectUrl);

            // 检查重定向URL是否是外部CDN/存储服务
            boolean isExternalRedirect = redirectUrl.contains("ctyunxs.cn") ||
                                       redirectUrl.contains("amazonaws.com") ||
                                       redirectUrl.contains("aliyuncs.com") ||
                                       !redirectUrl.contains(config.getBaseUrl());

          // 重新构建请求头
            HttpHeaders redirectHeaders = new HttpHeaders();
            redirectHeaders.set("User-Agent", AppConstants.USER_AGENT);

            // 只有重定向到同一域名时才发送认证头
            if (!isExternalRedirect && config.getToken() != null && !config.getToken().isEmpty()) {
              redirectHeaders.set("Authorization", config.getToken());
              log.debug("重定向到同一域名，携带认证头");
            } else {
              log.debug("重定向到外部CDN/存储服务，不携带认证头");
            }

            HttpEntity<String> redirectEntity = new HttpEntity<>(redirectHeaders);

          // 发送重定向请求 - 对于外部CDN，直接使用URI避免RestTemplate自动编码
            ResponseEntity<byte[]> redirectResponse;
            if (isExternalRedirect) {
              // 对于外部CDN，使用URI直接请求，避免RestTemplate自动编码导致签名失效
              try {
                java.net.URI uri = java.net.URI.create(redirectUrl);
                redirectResponse = restTemplate.exchange(uri, HttpMethod.GET, redirectEntity, byte[].class);
                log.debug("使用URI直接请求外部CDN，避免自动编码: {}", uri);
              } catch (Exception e) {
                log.error("外部CDN重定向URL构建URI失败，标记下载失败: {}", redirectUrl, e);
                return null;
              }
            } else {
              redirectResponse = restTemplate.exchange(redirectUrl, HttpMethod.GET, redirectEntity, byte[].class);
            }

            log.debug(
                "重定向下载响应 - 状态码: {}, Content-Type: {}",
                redirectResponse.getStatusCode(),
                redirectResponse.getHeaders().getContentType());

            if (redirectResponse.getStatusCode().is2xxSuccessful()) {
              byte[] content = redirectResponse.getBody();
              if (content != null && content.length > 0) {
                log.info("重定向下载成功: {}, 大小: {} bytes", filePath, content.length);
                return content;
              } else {
                log.warn("重定向下载内容为空: {}", filePath);
                return null;
              }
            } else {
              log.warn("重定向下载失败: {}, 状态码: {}", filePath, redirectResponse.getStatusCode());
              return null;
            }
          } else {
            log.warn("收到302重定向但没有Location头: {}", filePath);
            return null;
          }
        } catch (Exception redirectException) {
          log.error(
              "处理302重定向失败: {}, 错误: {}",
              filePath,
              redirectException.getMessage(),
              redirectException);
          return null;
        }
      }

      if (!response.getStatusCode().is2xxSuccessful()) {
        log.warn("文件下载失败: {}, 状态码: {}, URL: {}", filePath, response.getStatusCode(), fileUrl);
        return null;
      }

      byte[] content = response.getBody();
      if (content == null || content.length == 0) {
        log.warn("文件内容为空: {}, URL: {}", filePath, fileUrl);
        return null;
      }

      // 检测文件内容类型（前几个字节）
      String contentPreview = "";
      if (content.length > 0) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(20, content.length); i++) {
          sb.append(String.format("%02X ", content[i] & 0xFF));
        }
        contentPreview = sb.toString().trim();
      }

      log.debug(
          "文件下载成功 - 路径: {}, 大小: {} bytes, 前20字节: {}", filePath, content.length, contentPreview);
      return content;

    } catch (Exception e) {
      log.error("下载文件异常: {}, 错误: {}", filePath, e.getMessage(), e);
      return null;
    }
  }

  /**
   * 验证OpenList配置信息（用于前端保存配置时的验证）
   *
   * @param baseUrl OpenList服务地址
   * @param token   认证Token
   * @return 验证结果，包含用户信息
   */
  public ValidateConfigResult validateConfig(String baseUrl, String token) {
    try {
      // 构建API URL
      String apiUrl = baseUrl;
      if (!apiUrl.endsWith("/")) {
        apiUrl += "/";
      }
      apiUrl += "api/me";

      log.info("验证OpenList配置: {}", apiUrl);

      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("User-Agent", AppConstants.USER_AGENT);
      headers.set("Authorization", token);

      HttpEntity<String> entity = new HttpEntity<>(headers);

      // 发送GET请求
      ResponseEntity<String> response =
          restTemplate.exchange(apiUrl, HttpMethod.GET, entity, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("OpenList API请求失败，状态码: " + response.getStatusCode());
      }

      String responseBody = response.getBody();
      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("OpenList API返回空响应");
      }

      log.debug("验证配置响应: {}", responseBody);

      // 解析响应
      MeApiResponse meResponse = objectMapper.readValue(responseBody, MeApiResponse.class);

      if (meResponse.getCode() == null || !meResponse.getCode().equals(200)) {
        throw new BusinessException("OpenList API返回错误: " + meResponse.getMessage());
      }

      if (meResponse.getData() == null) {
        throw new BusinessException("OpenList API返回数据为空");
      }

      MeData userData = meResponse.getData();

      // 检查用户是否被禁用
      if (userData.getDisabled() != null && userData.getDisabled()) {
        throw new BusinessException("该账号已被禁用，无法添加配置");
      }

      ValidateConfigResult result = new ValidateConfigResult();
      result.setUsername(userData.getUsername());
      result.setBasePath(userData.getBasePath() != null ? userData.getBasePath() : "/");

      log.info("验证OpenList配置成功: username={}, basePath={}", result.getUsername(), result.getBasePath());
      return result;

    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("验证OpenList配置失败: {}", e.getMessage(), e);
      if (e.getMessage() != null && e.getMessage().contains("401")) {
        throw new BusinessException("Token无效或已过期");
      } else if (e.getMessage() != null && e.getMessage().contains("403")) {
        throw new BusinessException("没有权限访问该API");
      } else if (e.getMessage() != null && e.getMessage().contains("404")) {
        throw new BusinessException("API接口不存在，请检查Base URL");
      }
      throw new BusinessException("网络连接失败，请检查Base URL和Token: " + e.getMessage());
    }
  }

  /**
   * 验证任务路径是否存在
   *
   * @param baseUrl  OpenList服务地址
   * @param token    认证Token
   * @param basePath 用户的basePath
   * @param taskPath 任务路径
   * @return 是否为有效目录
   */
  public boolean validatePath(String baseUrl, String token, String basePath, String taskPath) {
    try {
      // 拼接完整路径
      String fullPath = basePath;
      if (fullPath.endsWith("/") && taskPath.startsWith("/")) {
        fullPath = fullPath.substring(0, fullPath.length() - 1) + taskPath;
      } else if (!fullPath.endsWith("/") && !taskPath.startsWith("/")) {
        fullPath = fullPath + "/" + taskPath;
      } else {
        fullPath = fullPath + taskPath;
      }

      // 构建API URL
      String apiUrl = baseUrl;
      if (!apiUrl.endsWith("/")) {
        apiUrl += "/";
      }
      apiUrl += "api/fs/get";

      log.info("验证任务路径: {}, fullPath: {}", apiUrl, fullPath);

      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.set("User-Agent", AppConstants.USER_AGENT);
      headers.set("Authorization", token);

      // 构建请求体
      String requestBody = String.format(
          "{\"path\":\"%s\",\"password\":\"\",\"page\":1,\"per_page\":0,\"refresh\":false}",
          fullPath);

      HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

      // 发送POST请求
      ResponseEntity<String> response =
          restTemplate.exchange(apiUrl, HttpMethod.POST, entity, String.class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("OpenList API请求失败，状态码: " + response.getStatusCode());
      }

      String responseBody = response.getBody();
      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("OpenList API返回空响应");
      }

      log.debug("验证路径响应: {}", responseBody);

      // 解析响应
      FsGetApiResponse fsResponse = objectMapper.readValue(responseBody, FsGetApiResponse.class);

      if (fsResponse.getCode() == null || !fsResponse.getCode().equals(200)) {
        throw new BusinessException("路径验证失败: " + fsResponse.getMessage());
      }

      if (fsResponse.getData() == null) {
        throw new BusinessException("指定路径不存在");
      }

      // 检查是否为目录
      if (fsResponse.getData().getIsDir() == null || !fsResponse.getData().getIsDir()) {
        throw new BusinessException("指定路径不是一个目录");
      }

      log.info("验证任务路径成功: {}", fullPath);
      return true;

    } catch (BusinessException e) {
      throw e;
    } catch (Exception e) {
      log.error("验证任务路径失败: {}", e.getMessage(), e);
      if (e.getMessage() != null && e.getMessage().contains("401")) {
        throw new BusinessException("OpenList Token无效或已过期");
      } else if (e.getMessage() != null && e.getMessage().contains("403")) {
        throw new BusinessException("没有权限访问该路径");
      } else if (e.getMessage() != null && e.getMessage().contains("404")) {
        throw new BusinessException("指定路径不存在");
      }
      throw new BusinessException("路径验证失败，请检查路径是否正确: " + e.getMessage());
    }
  }

  /** /api/me 接口响应结构 */
  @Data
  public static class MeApiResponse {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private MeData data;
  }

  /** /api/me 用户数据结构 */
  @Data
  public static class MeData {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("username")
    private String username;

    @JsonProperty("password")
    private String password;

    @JsonProperty("base_path")
    private String basePath;

    @JsonProperty("role")
    private Integer role;

    @JsonProperty("disabled")
    private Boolean disabled;

    @JsonProperty("permission")
    private Integer permission;

    @JsonProperty("sso_id")
    private String ssoId;

    @JsonProperty("authn")
    private String authn;
  }

  /** /api/fs/get 接口响应结构 */
  @Data
  public static class FsGetApiResponse {
    @JsonProperty("code")
    private Integer code;

    @JsonProperty("message")
    private String message;

    @JsonProperty("data")
    private FsGetData data;
  }

  /** /api/fs/get 数据结构 */
  @Data
  public static class FsGetData {
    @JsonProperty("name")
    private String name;

    @JsonProperty("size")
    private Long size;

    @JsonProperty("is_dir")
    private Boolean isDir;

    @JsonProperty("modified")
    private String modified;

    @JsonProperty("created")
    private String created;

    @JsonProperty("sign")
    private String sign;

    @JsonProperty("thumb")
    private String thumb;

    @JsonProperty("type")
    private Integer type;

    @JsonProperty("hashinfo")
    private String hashinfo;

    @JsonProperty("hash_info")
    private Object hashInfo;

    @JsonProperty("raw_url")
    private String rawUrl;

    @JsonProperty("readme")
    private String readme;

    @JsonProperty("header")
    private String header;

    @JsonProperty("provider")
    private String provider;

    @JsonProperty("related")
    private Object related;
  }

  /** 验证配置结果 */
  @Data
  public static class ValidateConfigResult {
    private String username;
    private String basePath;
  }

  /**
   * 构建文件URL，使用UriComponentsBuilder进行正确的URL编码
   *
   * <p>使用Spring的UriComponentsBuilder进行URL编码，正确处理包含中文字符的路径
   *
   * @param baseUrl 基础URL
   * @param filePath 文件路径
   * @return 完整的文件URL
   */
  private String buildFileUrl(String baseUrl, String filePath) {
    // 确保baseUrl以/结尾
    if (!baseUrl.endsWith("/")) {
      baseUrl += "/";
    }

    // 使用UriComponentsBuilder构建URL，自动处理路径编码
    String result =
        UriComponentsBuilder.fromHttpUrl(baseUrl)
            .pathSegment("d")
            .path(filePath)
            .build()
            .toUriString();

    log.debug("URL构建编码: {}{}d{} -> {}", baseUrl, "d", filePath, result);
    return result;
  }
}
