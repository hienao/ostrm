package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.service.OpenlistApiService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 图片文件下载处理器
 *
 * <p>负责图片文件（海报、背景图、缩略图）的三级优先级处理：
 *
 * <ol>
 *   <li>优先级 1 - 本地文件：检查本地是否存在对应图片文件
 *   <li>优先级 2 - OpenList 文件：本地不存在时从 OpenList 同级目录下载
 *   <li>优先级 3 - 刮削文件：前两级都不存在时执行 TMDB 刮削
 * </ol>
 *
 * <p>Order: 41
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@Order(41)
@RequiredArgsConstructor
public class ImageDownloadHandler implements FileProcessorHandler {

  private final FilePriorityResolver priorityResolver;
  private final OpenlistApiService openlistApiService;

  // 图片文件扩展名
  private static final Set<String> IMAGE_EXTENSIONS =
      Set.of(".jpg", ".jpeg", ".png", ".webp", ".bmp");

  // ==================== 接口实现 ====================

  @Override
  public ProcessingResult process(FileProcessingContext context) {
    try {
      // 1. 检查配置是否启用
      if (!isImageScrapingEnabled(context)) {
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      // 2. 处理海报文件
      ProcessingResult posterResult = processImage(context, "-poster.jpg", "海报");

      // 3. 处理背景图文件
      ProcessingResult backdropResult = processImage(context, "-fanart.jpg", "背景图");

      // 4. 处理缩略图文件
      ProcessingResult thumbResult = processImage(context, "-thumb.jpg", "缩略图");

      // 5. 降级处理：如果没有找到特定命名的图片，下载任意图片文件
      ProcessingResult arbitraryImageResult = processArbitraryImages(context);

      // 综合结果
      if (posterResult == ProcessingResult.FAILED
          || backdropResult == ProcessingResult.FAILED
          || thumbResult == ProcessingResult.FAILED
          || arbitraryImageResult == ProcessingResult.FAILED) {
        return ProcessingResult.FAILED;
      }

      return ProcessingResult.SUCCESS;

    } catch (Exception e) {
      log.error("图片文件处理失败: {}", context.getBaseFileName(), e);
      context.getStats().incrementFailed();
      return ProcessingResult.FAILED;
    }
  }

  @Override
  public Set<FileType> getHandledTypes() {
    return Set.of(FileType.IMAGE, FileType.VIDEO);
  }

  // ==================== 图片处理 ====================

  /** 处理单个图片文件 */
  private ProcessingResult processImage(
      FileProcessingContext context, String suffix, String description) {

    String imageFileName = context.getBaseFileName() + suffix;

    // 1. 检查本地文件是否存在
    Path localPath = Paths.get(context.getSaveDirectory(), imageFileName);
    if (Files.exists(localPath)) {
      log.debug("本地{}文件已存在，跳过: {}", description, imageFileName);
      context.getStats().incrementSkipped();
      return ProcessingResult.SKIPPED;
    }

    // 2. 从 OpenList 下载
    OpenlistApiService.OpenlistFile openlistImage =
        findImageInDirectory(context.getDirectoryFiles(), imageFileName);

    if (openlistImage != null) {
      try {
        if (openlistApiService.downloadToFile(
            context.getOpenlistConfig(), openlistImage, localPath)) {
          log.info("从 OpenList 下载{}文件成功: {}", description, imageFileName);
          context.getStats().incrementProcessed();
          return ProcessingResult.SUCCESS;
        }
      } catch (Exception e) {
        log.warn("从 OpenList 下载{}文件失败: {}", description, imageFileName, e);
      }
    }

    // 3. 图片文件不执行刮削（由 MediaScrapingHandler 处理）
    log.debug("本地和 OpenList 都不存在{}文件，跳过: {}", description, imageFileName);
    context.getStats().incrementSkipped();
    return ProcessingResult.SKIPPED;
  }

  /** 处理任意命名的图片文件（降级策略） 如果没有找到特定命名的图片文件，下载同目录下的任意图片文件 */
  private ProcessingResult processArbitraryImages(FileProcessingContext context) {
    try {
      java.util.List<OpenlistApiService.OpenlistFile> directoryFiles = context.getDirectoryFiles();
      if (directoryFiles == null || directoryFiles.isEmpty()) {
        return ProcessingResult.SKIPPED;
      }

      String saveDirectory = context.getSaveDirectory();
      String videoBaseName = context.getBaseFileName();

      // 获取当前目录路径
      String currentDirectory =
          context
              .getCurrentFile()
              .getPath()
              .substring(0, context.getCurrentFile().getPath().lastIndexOf('/') + 1);

      // 查找同目录下的图片文件（排除已经处理过的特定命名图片）
      java.util.List<OpenlistApiService.OpenlistFile> arbitraryImages =
          directoryFiles.stream()
              .filter(f -> "file".equals(f.getType()))
              .filter(f -> isImageFile(f.getName()))
              .filter(f -> f.getPath().startsWith(currentDirectory))
              .filter(f -> !isNamedImageFile(f.getName(), videoBaseName)) // 排除特定命名的图片
              .collect(java.util.stream.Collectors.toList());

      if (arbitraryImages.isEmpty()) {
        log.debug("没有找到任意命名的图片文件");
        return ProcessingResult.SKIPPED;
      }

      log.info("发现 {} 个任意命名的图片文件", arbitraryImages.size());

      int successCount = 0;
      for (OpenlistApiService.OpenlistFile imageFile : arbitraryImages) {
        if (downloadArbitraryImage(context, imageFile)) {
          successCount++;
        }
      }

      if (successCount > 0) {
        log.info("成功下载 {} 个任意命名的图片文件", successCount);
        context.getStats().incrementProcessed();
        return ProcessingResult.SUCCESS;
      }

      return ProcessingResult.SKIPPED;

    } catch (Exception e) {
      log.error("处理任意命名图片文件失败: {}", context.getBaseFileName(), e);
      return ProcessingResult.FAILED;
    }
  }

  /** 下载任意命名的图片文件，保留原文件名 */
  private boolean downloadArbitraryImage(
      FileProcessingContext context, OpenlistApiService.OpenlistFile imageFile) {

    String saveDirectory = context.getSaveDirectory();
    String fileName = imageFile.getName();

    try {
      // 检查本地是否已存在
      Path localPath = Paths.get(saveDirectory, fileName);
      if (Files.exists(localPath)) {
        log.debug("本地图片文件已存在，跳过: {}", fileName);
        return true;
      }

      // 构建下载URL并进行编码
      String downloadUrl = imageFile.getUrl();
      if (imageFile.getSign() != null && !imageFile.getSign().isEmpty()) {
        downloadUrl = downloadUrl + "?sign=" + imageFile.getSign();
      }
      // 使用统一的智能编码方法处理中文路径
      downloadUrl = com.hienao.openlist2strm.util.UrlEncoder.encodeUrlSmart(downloadUrl);

      // 从 OpenList 下载
      if (openlistApiService.downloadToFile(
          context.getOpenlistConfig(), imageFile, downloadUrl, localPath)) {
        log.info("从 OpenList 下载任意命名图片文件成功: {} -> {}", fileName, localPath);
        return true;
      }

      log.debug("图片文件内容为空: {}", fileName);
      return false;

    } catch (Exception e) {
      log.warn("下载任意命名图片文件失败: {}, 错误: {}", fileName, e.getMessage());
      return false;
    }
  }

  /** 检查是否为特定命名的图片文件 */
  private boolean isNamedImageFile(String fileName, String videoBaseName) {
    String lower = fileName.toLowerCase();
    return lower.endsWith("-poster.jpg")
        || lower.endsWith("-fanart.jpg")
        || lower.endsWith("-thumb.jpg");
  }

  /** 检查是否为图片文件 */
  private boolean isImageFile(String fileName) {
    if (fileName == null) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".jpg")
        || lower.endsWith(".jpeg")
        || lower.endsWith(".png")
        || lower.endsWith(".webp")
        || lower.endsWith(".bmp");
  }

  // ==================== 电视剧共用图片处理 ====================

  /** 处理电视剧共用图片（poster.jpg, fanart.jpg） */
  public ProcessingResult processTvShowSharedImages(FileProcessingContext context) {
    try {
      String saveDirectory = context.getSaveDirectory();

      // 处理 tvshow.nfo
      processSharedFile(context, saveDirectory, "tvshow.nfo");

      // 处理 poster.jpg
      processSharedFile(context, saveDirectory, "poster.jpg");

      // 处理 fanart.jpg
      processSharedFile(context, saveDirectory, "fanart.jpg");

      return ProcessingResult.SUCCESS;

    } catch (Exception e) {
      log.error("处理电视剧共用图片失败: {}", context.getBaseFileName(), e);
      return ProcessingResult.FAILED;
    }
  }

  private void processSharedFile(
      FileProcessingContext context, String saveDirectory, String fileName) {

    Path localPath = Paths.get(saveDirectory, fileName);

    if (Files.exists(localPath)) {
      log.debug("本地共用文件已存在，跳过: {}", fileName);
      return;
    }

    // 从 OpenList 下载
    OpenlistApiService.OpenlistFile openlistFile =
        findImageInDirectory(context.getDirectoryFiles(), fileName);

    if (openlistFile != null) {
      try {
        if (openlistApiService.downloadToFile(
            context.getOpenlistConfig(), openlistFile, localPath)) {
          log.info("从 OpenList 下载共用文件成功: {}", fileName);
        }
      } catch (Exception e) {
        log.warn("从 OpenList 下载共用文件失败: {}", fileName, e);
      }
    }
  }

  // ==================== 工具方法 ====================

  /** 检查图片刮削是否启用 */
  private boolean isImageScrapingEnabled(FileProcessingContext context) {
    Object useExistingValue = context.getAttribute("useExistingScrapingInfo");
    return Boolean.TRUE.equals(useExistingValue);
  }

  /** 在目录文件中查找图片文件 */
  private OpenlistApiService.OpenlistFile findImageInDirectory(
      java.util.List<OpenlistApiService.OpenlistFile> files, String fileName) {
    if (files == null) {
      return null;
    }

    return files.stream()
        .filter(f -> "file".equals(f.getType()))
        .filter(f -> f.getName().equalsIgnoreCase(fileName))
        .findFirst()
        .orElse(null);
  }
}
