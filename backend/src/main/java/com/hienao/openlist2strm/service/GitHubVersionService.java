package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.config.cache.CacheConfig;
import com.hienao.openlist2strm.constant.AppConstants;
import com.hienao.openlist2strm.dto.version.GitHubRelease;
import com.hienao.openlist2strm.dto.version.VersionCheckResponse;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * GitHub版本检查服务
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GitHubVersionService {

  private final RestTemplate restTemplate;
  private final ObjectMapper objectMapper;

  @Value("${github.repo.owner:hienao}")
  private String repoOwner;

  @Value("${github.repo.name:ostrm}")
  private String repoName;

  @Value("${github.api.timeout:30}")
  private int apiTimeout;

  private static final String GITHUB_API_URL = "https://api.github.com";

  /**
   * 检查版本更新
   *
   * @param currentVersion 当前版本
   * @return 版本检查响应
   */
  @Cacheable(value = CacheConfig.VERSION_CHECK, key = "{#currentVersion}")
  public VersionCheckResponse checkVersionUpdate(String currentVersion) {
    try {
      log.info("检查版本更新: {}", currentVersion);

      // 获取最新release
      GitHubRelease latestRelease = getLatestRelease();
      if (latestRelease == null) {
        log.warn("无法获取最新版本信息，返回错误响应");
        return createErrorResponse("无法获取最新版本信息");
      }

      // 构建响应
      VersionCheckResponse response = buildVersionCheckResponse(currentVersion, latestRelease);

      return response;
    } catch (Exception e) {
      log.error("检查版本更新失败", e);
      return createErrorResponse("检查版本更新失败: " + e.getMessage());
    }
  }

  /**
   * 获取最新release
   *
   * @return 最新release信息
   */
  @Cacheable(value = CacheConfig.GITHUB_RELEASES, key = "'latestRelease'")
  public GitHubRelease getLatestRelease() {
    try {
      String url = String.format("%s/repos/%s/%s/releases", GITHUB_API_URL, repoOwner, repoName);

      HttpHeaders headers = new HttpHeaders();
      headers.set("Accept", "application/vnd.github.v3+json");
      headers.set("User-Agent", AppConstants.USER_AGENT);

      HttpEntity<?> entity = new HttpEntity<>(headers);
      ResponseEntity<String> response =
          restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

      // 调试日志：打印原始响应
      log.info("GitHub API原始响应: {}", response.getBody());

      // 尝试解析JSON
      GitHubRelease[] releases = objectMapper.readValue(response.getBody(), GitHubRelease[].class);

      if (releases == null || releases.length == 0) {
        log.warn("未找到任何release");
        return null;
      }

      // 调试日志：打印所有release信息
      log.info("获取到 {} 个release", releases.length);
      for (int i = 0; i < Math.min(releases.length, 3); i++) {
        GitHubRelease release = releases[i];
        log.info(
            "Release {}: tagName={}, name={}, draft={}, prerelease={}, publishedAt={}",
            i,
            release.getTagName(),
            release.getName(),
            release.isDraft(),
            release.isPrerelease(),
            release.getPublishedAt());
      }

      // 过滤掉draft和prerelease，按发布时间排序
      List<GitHubRelease> filteredReleases =
          Arrays.stream(releases)
              .filter(release -> !release.isDraft() && !release.isPrerelease())
              .sorted(Comparator.comparing(GitHubRelease::getPublishedAt).reversed())
              .toList();

      if (filteredReleases.isEmpty()) {
        log.warn("未找到有效的release (过滤后为空)");
        return null;
      }

      GitHubRelease latestRelease = filteredReleases.get(0);
      log.info(
          "获取到最新release: {} (发布时间: {})",
          latestRelease.getTagName(),
          latestRelease.getPublishedAt());

      return latestRelease;
    } catch (Exception e) {
      log.error("获取GitHub release失败", e);
      return null;
    }
  }

  /**
   * 比较版本号
   *
   * @param version1 版本1
   * @param version2 版本2
   * @return 比较结果：1表示version1更新，-1表示version2更新，0表示相同
   */
  public int compareVersions(String version1, String version2) {
    try {
      log.info("比较版本: {} vs {}", version1, version2);

      // 处理null值
      if (version2 == null) {
        log.info("版本2为null，认为版本1更新");
        return 1; // version2为null，version1更新
      }

      // 移除'v'前缀
      String v1 = version1.startsWith("v") ? version1.substring(1) : version1;
      String v2 = version2.startsWith("v") ? version2.substring(1) : version2;

      log.info("移除前缀后比较: {} vs {}", v1, v2);

      // 处理开发版本：开发版本被认为比任何正式版本都旧
      if (isDevelopmentVersion(v1) && !isDevelopmentVersion(v2)) {
        log.info("v1是开发版本，v2是正式版本，v2更新");
        return -1; // version1是开发版本，version2是正式版本，version2更新
      }
      if (!isDevelopmentVersion(v1) && isDevelopmentVersion(v2)) {
        log.info("v1是正式版本，v2是开发版本，v1更新");
        return 1; // version1是正式版本，version2是开发版本，version1更新
      }
      if (isDevelopmentVersion(v1) && isDevelopmentVersion(v2)) {
        log.info("两个都是开发版本，认为相同");
        return 0; // 两个都是开发版本，认为相同
      }

      // 两个都是正式版本，进行正常比较
      String[] parts1 = v1.split("\\.");
      String[] parts2 = v2.split("\\.");

      log.info("分割版本号: parts1={}, parts2={}", Arrays.toString(parts1), Arrays.toString(parts2));

      int length = Math.max(parts1.length, parts2.length);

      for (int i = 0; i < length; i++) {
        int num1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
        int num2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

        log.info("比较第{}部分: {} vs {}", i, num1, num2);

        if (num1 > num2) {
          log.info("第{}部分比较结果: {} > {}, version1更新", i, num1, num2);
          return 1;
        } else if (num1 < num2) {
          log.info("第{}部分比较结果: {} < {}, version2更新", i, num1, num2);
          return -1;
        }
      }

      log.info("版本完全相同");
      return 0;
    } catch (Exception e) {
      log.warn("版本号比较失败: {} vs {}", version1, version2, e);
      return 0;
    }
  }

  /**
   * 判断是否为开发版本
   *
   * @param version 版本号
   * @return 是否为开发版本
   */
  private boolean isDevelopmentVersion(String version) {
    if (version == null || version.trim().isEmpty()) {
      log.info("版本号为空或空字符串，认为是开发版本");
      return true;
    }

    // 常见的开发版本标识
    String lowerVersion = version.toLowerCase();
    boolean isDev =
        lowerVersion.equals("dev")
            || lowerVersion.equals("development")
            || lowerVersion.equals("snapshot")
            || lowerVersion.equals("alpha")
            || lowerVersion.equals("beta")
            || lowerVersion.equals("rc")
            || lowerVersion.contains("-dev")
            || lowerVersion.contains("-snapshot")
            || lowerVersion.contains("-alpha")
            || lowerVersion.contains("-beta")
            || lowerVersion.contains("-rc");

    log.info("版本 {} 是否为开发版本: {}", version, isDev);
    return isDev;
  }

  /** 构建版本检查响应 */
  private VersionCheckResponse buildVersionCheckResponse(
      String currentVersion, GitHubRelease release) {
    VersionCheckResponse response = new VersionCheckResponse();
    response.setCurrentVersion(currentVersion);

    // 调试日志：检查release的tagName
    String tagName = release.getTagName();
    log.info("构建版本检查响应 - tagName: {}", tagName);
    response.setLatestVersion(tagName);

    response.setReleaseUrl(release.getHtmlUrl());
    response.setReleaseNotes(release.getBody());
    response.setPrerelease(release.isPrerelease());
    response.setPublishedAt(release.getPublishedAt());
    response.setCheckTime(LocalDateTime.now());

    // 比较版本
    int comparison = compareVersions(currentVersion, tagName);
    response.setHasUpdate(comparison < 0);

    log.info("版本检查结果: 当前={}, 最新={}, 有更新={}", currentVersion, tagName, response.isHasUpdate());

    return response;
  }

  /** 创建错误响应 */
  private VersionCheckResponse createErrorResponse(String error) {
    VersionCheckResponse response = new VersionCheckResponse();
    response.setError(error);
    response.setCheckTime(LocalDateTime.now());
    return response;
  }
}
