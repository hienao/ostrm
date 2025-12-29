package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.constant.AppConstants;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import com.hienao.openlist2strm.exception.BusinessException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * TMDB API 服务
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbApiService {

  private final ObjectMapper objectMapper;
  private final SystemConfigService systemConfigService;

  /** 创建配置了代理的RestTemplate */
  private RestTemplate createRestTemplate() {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String proxyHost = (String) tmdbConfig.get("proxyHost");
    String proxyPortStr = (String) tmdbConfig.get("proxyPort");

    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();

    // 配置代理
    if (proxyHost != null
        && !proxyHost.trim().isEmpty()
        && proxyPortStr != null
        && !proxyPortStr.trim().isEmpty()) {
      try {
        int proxyPort = Integer.parseInt(proxyPortStr.trim());
        Proxy proxy =
            new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost.trim(), proxyPort));
        factory.setProxy(proxy);
        log.info("TMDB API 使用代理: {}:{}", proxyHost.trim(), proxyPort);
      } catch (NumberFormatException e) {
        log.warn("代理端口配置无效: {}, 将不使用代理", proxyPortStr);
      }
    }

    // 设置超时时间
    Integer timeout = (Integer) tmdbConfig.getOrDefault("timeout", 30);
    factory.setConnectTimeout(timeout * 1000);
    factory.setReadTimeout(timeout * 1000);

    return new RestTemplate(factory);
  }

  /** 记录请求详细信息 */
  private void logRequestDetails(String method, String url, Map<String, String> params) {
    log.info("TMDB API 请求 - 方法: {}, URL: {}", method, url);
    if (params != null && !params.isEmpty()) {
      log.info("TMDB API 请求参数: {}", params);
    }
  }

  /** 记录响应详细信息 */
  private void logResponseDetails(String method, int statusCode, String responseBody) {
    log.info("TMDB API 响应 - 方法: {}, 状态码: {}", method, statusCode);
    if (responseBody != null) {
      // 限制响应体日志长度，避免日志过长
      String logBody =
          responseBody.length() > 1000
              ? responseBody.substring(0, 1000) + "... (truncated)"
              : responseBody;
      log.info("TMDB API 响应体: {}", logBody);
    }
  }

  /** 记录错误详细信息 */
  private void logErrorDetails(String method, String url, Exception e, String responseBody) {
    log.error("TMDB API 请求失败 - 方法: {}, URL: {}, 错误: {}", method, url, e.getMessage());
    if (responseBody != null) {
      log.error("TMDB API 错误响应体: {}", responseBody);
    }
    log.error("TMDB API 错误堆栈:", e);
  }

  /** 记录搜索结果为空的情况（刮削失败） */
  private void logEmptySearchResult(String method, String query, String year, int resultCount) {
    log.warn(
        "TMDB 搜索结果为空 - 方法: {}, 查询: '{}', 年份: {}, 结果数量: {}",
        method,
        query,
        year != null ? year : "未指定",
        resultCount);
  }

  /**
   * 搜索电影
   *
   * @param query 搜索关键词
   * @param year 年份（可选）
   * @return 搜索结果
   */
  public TmdbSearchResponse searchMovies(String query, String year) {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String apiKey = (String) tmdbConfig.get("apiKey");

    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new BusinessException("TMDB API Key 未配置");
    }

    RestTemplate restTemplate = createRestTemplate();
    String responseBody = null;
    String url = null;

    try {
      String baseUrl = (String) tmdbConfig.getOrDefault("baseUrl", "https://api.themoviedb.org/3");
      String language = (String) tmdbConfig.getOrDefault("language", "zh-CN");

      UriComponentsBuilder builder =
          UriComponentsBuilder.fromHttpUrl(baseUrl + "/search/movie")
              .queryParam("api_key", apiKey)
              .queryParam("language", language)
              .queryParam("query", query);

      if (year != null && !year.trim().isEmpty()) {
        builder.queryParam("year", year);
      }

      url = builder.toUriString();

      // 记录请求参数
      Map<String, String> requestParams = new java.util.HashMap<>();
      requestParams.put("query", query);
      requestParams.put("language", language);
      if (year != null && !year.trim().isEmpty()) {
        requestParams.put("year", year);
      }
      logRequestDetails("GET", url, requestParams);

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      responseBody = response.getBody();

      // 记录响应详情
      logResponseDetails("GET", response.getStatusCode().value(), responseBody);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("TMDB API 请求失败，状态码: " + response.getStatusCode());
      }

      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("TMDB API 返回空响应");
      }

      TmdbSearchResponse searchResponse =
          objectMapper.readValue(responseBody, TmdbSearchResponse.class);
      int resultCount =
          searchResponse.getResults() != null ? searchResponse.getResults().size() : 0;

      if (resultCount == 0) {
        logEmptySearchResult("searchMovies", query, year, resultCount);
      } else {
        log.info("搜索电影 '{}' 找到 {} 个结果", query, resultCount);
      }

      return searchResponse;

    } catch (Exception e) {
      logErrorDetails("GET", url, e, responseBody);
      throw new BusinessException("搜索电影失败: " + e.getMessage());
    }
  }

  /**
   * 搜索电视剧
   *
   * @param query 搜索关键词
   * @param year 年份（可选）
   * @return 搜索结果
   */
  public TmdbSearchResponse searchTvShows(String query, String year) {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String apiKey = (String) tmdbConfig.get("apiKey");

    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new BusinessException("TMDB API Key 未配置");
    }

    RestTemplate restTemplate = createRestTemplate();
    String responseBody = null;
    String url = null;

    try {
      String baseUrl = (String) tmdbConfig.getOrDefault("baseUrl", "https://api.themoviedb.org/3");
      String language = (String) tmdbConfig.getOrDefault("language", "zh-CN");

      UriComponentsBuilder builder =
          UriComponentsBuilder.fromHttpUrl(baseUrl + "/search/tv")
              .queryParam("api_key", apiKey)
              .queryParam("language", language)
              .queryParam("query", query);

      if (year != null && !year.trim().isEmpty()) {
        builder.queryParam("first_air_date_year", year);
      }

      url = builder.toUriString();

      // 记录请求参数
      Map<String, String> requestParams = new java.util.HashMap<>();
      requestParams.put("query", query);
      requestParams.put("language", language);
      if (year != null && !year.trim().isEmpty()) {
        requestParams.put("first_air_date_year", year);
      }
      logRequestDetails("GET", url, requestParams);

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      responseBody = response.getBody();

      // 记录响应详情
      logResponseDetails("GET", response.getStatusCode().value(), responseBody);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("TMDB API 请求失败，状态码: " + response.getStatusCode());
      }

      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("TMDB API 返回空响应");
      }

      TmdbSearchResponse searchResponse =
          objectMapper.readValue(responseBody, TmdbSearchResponse.class);
      int resultCount =
          searchResponse.getResults() != null ? searchResponse.getResults().size() : 0;

      if (resultCount == 0) {
        logEmptySearchResult("searchTvShows", query, year, resultCount);
      } else {
        log.info("搜索电视剧 '{}' 找到 {} 个结果", query, resultCount);
      }

      return searchResponse;

    } catch (Exception e) {
      logErrorDetails("GET", url, e, responseBody);
      throw new BusinessException("搜索电视剧失败: " + e.getMessage());
    }
  }

  /**
   * 获取电影详情
   *
   * @param movieId 电影ID
   * @return 电影详情
   */
  public TmdbMovieDetail getMovieDetail(Integer movieId) {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String apiKey = (String) tmdbConfig.get("apiKey");

    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new BusinessException("TMDB API Key 未配置");
    }

    RestTemplate restTemplate = createRestTemplate();
    String responseBody = null;
    String url = null;

    try {
      String baseUrl = (String) tmdbConfig.getOrDefault("baseUrl", "https://api.themoviedb.org/3");
      String language = (String) tmdbConfig.getOrDefault("language", "zh-CN");

      url =
          UriComponentsBuilder.fromHttpUrl(baseUrl + "/movie/" + movieId)
              .queryParam("api_key", apiKey)
              .queryParam("language", language)
              .toUriString();

      // 记录请求参数
      Map<String, String> requestParams = new java.util.HashMap<>();
      requestParams.put("movieId", String.valueOf(movieId));
      requestParams.put("language", language);
      logRequestDetails("GET", url, requestParams);

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      responseBody = response.getBody();

      // 记录响应详情
      logResponseDetails("GET", response.getStatusCode().value(), responseBody);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("TMDB API 请求失败，状态码: " + response.getStatusCode());
      }

      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("TMDB API 返回空响应");
      }

      TmdbMovieDetail movieDetail = objectMapper.readValue(responseBody, TmdbMovieDetail.class);
      log.info("获取电影详情成功: {} ({})", movieDetail.getTitle(), movieDetail.getId());

      return movieDetail;

    } catch (Exception e) {
      logErrorDetails("GET", url, e, responseBody);
      throw new BusinessException("获取电影详情失败: " + e.getMessage());
    }
  }

  /**
   * 获取电视剧详情
   *
   * @param tvId 电视剧ID
   * @return 电视剧详情
   */
  public TmdbTvDetail getTvDetail(Integer tvId) {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String apiKey = (String) tmdbConfig.get("apiKey");

    if (apiKey == null || apiKey.trim().isEmpty()) {
      throw new BusinessException("TMDB API Key 未配置");
    }

    RestTemplate restTemplate = createRestTemplate();
    String responseBody = null;
    String url = null;

    try {
      String baseUrl = (String) tmdbConfig.getOrDefault("baseUrl", "https://api.themoviedb.org/3");
      String language = (String) tmdbConfig.getOrDefault("language", "zh-CN");

      url =
          UriComponentsBuilder.fromHttpUrl(baseUrl + "/tv/" + tvId)
              .queryParam("api_key", apiKey)
              .queryParam("language", language)
              .toUriString();

      // 记录请求参数
      Map<String, String> requestParams = new java.util.HashMap<>();
      requestParams.put("tvId", String.valueOf(tvId));
      requestParams.put("language", language);
      logRequestDetails("GET", url, requestParams);

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
      responseBody = response.getBody();

      // 记录响应详情
      logResponseDetails("GET", response.getStatusCode().value(), responseBody);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new BusinessException("TMDB API 请求失败，状态码: " + response.getStatusCode());
      }

      if (responseBody == null || responseBody.isEmpty()) {
        throw new BusinessException("TMDB API 返回空响应");
      }

      TmdbTvDetail tvDetail = objectMapper.readValue(responseBody, TmdbTvDetail.class);
      log.info("获取电视剧详情成功: {} ({})", tvDetail.getName(), tvDetail.getId());

      return tvDetail;

    } catch (Exception e) {
      logErrorDetails("GET", url, e, responseBody);
      throw new BusinessException("获取电视剧详情失败: " + e.getMessage());
    }
  }

  /**
   * 构建图片完整URL
   *
   * @param imagePath 图片路径
   * @param size 图片尺寸
   * @return 完整的图片URL
   */
  public String buildImageUrl(String imagePath, String size) {
    if (imagePath == null || imagePath.trim().isEmpty()) {
      return null;
    }

    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String imageBaseUrl =
        (String) tmdbConfig.getOrDefault("imageBaseUrl", "https://image.tmdb.org/t/p");

    // 确保路径以 / 开头
    if (!imagePath.startsWith("/")) {
      imagePath = "/" + imagePath;
    }

    return imageBaseUrl + "/" + size + imagePath;
  }

  /**
   * 构建海报图片URL
   *
   * @param posterPath 海报路径
   * @return 海报图片URL
   */
  public String buildPosterUrl(String posterPath) {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String posterSize = (String) tmdbConfig.getOrDefault("posterSize", "w500");
    return buildImageUrl(posterPath, posterSize);
  }

  /**
   * 构建背景图片URL
   *
   * @param backdropPath 背景图片路径
   * @return 背景图片URL
   */
  public String buildBackdropUrl(String backdropPath) {
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String backdropSize = (String) tmdbConfig.getOrDefault("backdropSize", "w1280");
    return buildImageUrl(backdropPath, backdropSize);
  }

  /**
   * 验证TMDB API Key是否有效
   *
   * @param apiKey API Key
   * @return 验证结果
   */
  public boolean validateApiKey(String apiKey) {
    if (apiKey == null || apiKey.trim().isEmpty()) {
      return false;
    }

    try {
      RestTemplate restTemplate = createRestTemplate();
      String baseUrl = "https://api.themoviedb.org/3";
      String url =
          UriComponentsBuilder.fromHttpUrl(baseUrl + "/configuration")
              .queryParam("api_key", apiKey)
              .toUriString();

      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      HttpEntity<String> entity = new HttpEntity<>(headers);

      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

      return response.getStatusCode().is2xxSuccessful();

    } catch (Exception e) {
      log.warn("验证TMDB API Key失败: {}", e.getMessage());
      return false;
    }
  }
}
