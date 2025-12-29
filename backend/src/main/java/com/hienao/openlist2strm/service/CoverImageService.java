package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.constant.AppConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * 封面图片下载服务 负责从 TMDB 下载封面图片并保存到指定位置
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoverImageService {

  private final RestTemplate restTemplate;
  private final SystemConfigService systemConfigService;

  /**
   * 下载海报图片
   *
   * @param posterUrl 海报URL
   * @param saveDirectory 保存目录
   * @param fileName 文件名（不含扩展名）
   * @return 保存的文件路径
   */
  public String downloadPoster(String posterUrl, String saveDirectory, String fileName) {
    if (posterUrl == null || posterUrl.trim().isEmpty()) {
      log.warn("海报URL为空，跳过下载");
      return null;
    }

    String posterFileName = fileName + "-poster.jpg";
    String posterFilePath = Paths.get(saveDirectory, posterFileName).toString();

    try {
      downloadImage(posterUrl, posterFilePath);
      log.info("海报下载成功: {}", posterFilePath);
      return posterFilePath;
    } catch (Exception e) {
      log.error("下载海报失败: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 下载背景图片
   *
   * @param backdropUrl 背景图片URL
   * @param saveDirectory 保存目录
   * @param fileName 文件名（不含扩展名）
   * @return 保存的文件路径
   */
  public String downloadBackdrop(String backdropUrl, String saveDirectory, String fileName) {
    if (backdropUrl == null || backdropUrl.trim().isEmpty()) {
      log.warn("背景图片URL为空，跳过下载");
      return null;
    }

    String backdropFileName = fileName + "-backdrop.jpg";
    String backdropFilePath = Paths.get(saveDirectory, backdropFileName).toString();

    try {
      downloadImage(backdropUrl, backdropFilePath);
      log.info("背景图片下载成功: {}", backdropFilePath);
      return backdropFilePath;
    } catch (Exception e) {
      log.error("下载背景图片失败: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 下载缩略图（用于电视剧季）
   *
   * @param thumbUrl 缩略图URL
   * @param saveDirectory 保存目录
   * @param fileName 文件名（不含扩展名）
   * @return 保存的文件路径
   */
  public String downloadThumb(String thumbUrl, String saveDirectory, String fileName) {
    if (thumbUrl == null || thumbUrl.trim().isEmpty()) {
      log.warn("缩略图URL为空，跳过下载");
      return null;
    }

    String thumbFileName = fileName + "-thumb.jpg";
    String thumbFilePath = Paths.get(saveDirectory, thumbFileName).toString();

    try {
      downloadImage(thumbUrl, thumbFilePath);
      log.info("缩略图下载成功: {}", thumbFilePath);
      return thumbFilePath;
    } catch (Exception e) {
      log.error("下载缩略图失败: {}", e.getMessage(), e);
      return null;
    }
  }

  /**
   * 通用图片下载方法
   *
   * @param imageUrl 图片URL
   * @param saveFilePath 保存文件路径
   */
  private void downloadImage(String imageUrl, String saveFilePath) throws IOException {
    Path savePath = Paths.get(saveFilePath);

    // 确保目录存在
    Path parentDir = savePath.getParent();
    if (parentDir != null && !Files.exists(parentDir)) {
      Files.createDirectories(parentDir);
    }

    // 检查同名文件是否已存在
    if (Files.exists(savePath)) {
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean overwriteExisting = (Boolean) scrapingConfig.getOrDefault("overwriteExisting", false);

      if (!overwriteExisting) {
        log.info("检测到同名图片文件已存在，跳过下载: {}", saveFilePath);
        return;
      } else {
        log.info("同名图片文件已存在，但允许覆盖，继续下载: {}", saveFilePath);
      }
    }

    try {
      // 设置请求头
      HttpHeaders headers = new HttpHeaders();
      headers.set("User-Agent", AppConstants.USER_AGENT);
      headers.set("Accept", "image/*");
      HttpEntity<String> entity = new HttpEntity<>(headers);

      // 下载图片
      ResponseEntity<byte[]> response =
          restTemplate.exchange(imageUrl, HttpMethod.GET, entity, byte[].class);

      if (!response.getStatusCode().is2xxSuccessful()) {
        throw new IOException("HTTP请求失败，状态码: " + response.getStatusCode());
      }

      byte[] imageData = response.getBody();
      if (imageData == null || imageData.length == 0) {
        throw new IOException("下载的图片数据为空");
      }

      // 保存图片
      Files.write(savePath, imageData);

      log.debug("图片下载完成: {} -> {}", imageUrl, saveFilePath);

    } catch (Exception e) {
      log.error("下载图片失败: {} -> {}", imageUrl, saveFilePath, e);
      throw new IOException("下载图片失败: " + e.getMessage(), e);
    }
  }

  /**
   * 获取图片文件扩展名
   *
   * @param imageUrl 图片URL
   * @return 文件扩展名
   */
  private String getImageExtension(String imageUrl) {
    if (imageUrl == null || imageUrl.isEmpty()) {
      return ".jpg"; // 默认扩展名
    }

    // 从URL中提取扩展名
    String lowerUrl = imageUrl.toLowerCase();
    if (lowerUrl.contains(".png")) {
      return ".png";
    } else if (lowerUrl.contains(".gif")) {
      return ".gif";
    } else if (lowerUrl.contains(".webp")) {
      return ".webp";
    } else {
      return ".jpg"; // 默认为jpg
    }
  }

  /**
   * 清理文件名，移除不合法字符
   *
   * @param fileName 原始文件名
   * @return 清理后的文件名
   */
  private String sanitizeFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return "unknown";
    }

    // 移除或替换不合法字符，移除空格而不是替换为下划线
    return fileName.replaceAll("[\\\\/:*?\"<>|]", "_").replaceAll("\\s+", "").trim();
  }

  /**
   * 检查图片是否需要下载
   *
   * @param imageUrl 图片URL
   * @param saveFilePath 保存文件路径
   * @return 是否需要下载
   */
  private boolean shouldDownloadImage(String imageUrl, String saveFilePath) {
    if (imageUrl == null || imageUrl.trim().isEmpty()) {
      return false;
    }

    Path savePath = Paths.get(saveFilePath);

    // 如果文件不存在，需要下载
    if (!Files.exists(savePath)) {
      return true;
    }

    // 检查配置是否允许覆盖
    Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
    boolean overwriteExisting = (Boolean) scrapingConfig.getOrDefault("overwriteExisting", false);

    return overwriteExisting;
  }

  /**
   * 批量下载图片
   *
   * @param posterUrl 海报URL
   * @param backdropUrl 背景图片URL
   * @param saveDirectory 保存目录
   * @param fileName 文件名前缀
   */
  public void downloadImages(
      String posterUrl, String backdropUrl, String saveDirectory, String fileName) {
    Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
    boolean downloadPoster = (Boolean) scrapingConfig.getOrDefault("downloadPoster", true);
    boolean downloadBackdrop = (Boolean) scrapingConfig.getOrDefault("downloadBackdrop", false);

    // 下载海报
    if (downloadPoster && posterUrl != null && !posterUrl.trim().isEmpty()) {
      downloadPoster(posterUrl, saveDirectory, fileName);
    }

    // 下载背景图片
    if (downloadBackdrop && backdropUrl != null && !backdropUrl.trim().isEmpty()) {
      downloadBackdrop(backdropUrl, saveDirectory, fileName);
    }
  }

  /**
   * 获取标准化的文件名
   *
   * @param originalFileName 原始文件名
   * @return 标准化的文件名（不含扩展名）
   */
  public String getStandardizedFileName(String originalFileName) {
    if (originalFileName == null || originalFileName.isEmpty()) {
      return "unknown";
    }

    // 移除文件扩展名
    String nameWithoutExt = originalFileName;
    int lastDotIndex = nameWithoutExt.lastIndexOf('.');
    if (lastDotIndex > 0) {
      nameWithoutExt = nameWithoutExt.substring(0, lastDotIndex);
    }

    // 清理文件名
    return sanitizeFileName(nameWithoutExt);
  }
}
