package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.service.OpenlistApiService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 孤立文件清理处理器
 *
 * <p>负责清理增量模式下不再存在于 OpenList 中的文件：
 *
 * <ul>
 *   <li>识别孤立 STRM 文件
 *   <li>清理关联的刮削文件（NFO、图片、字幕）
 *   <li>清理空目录
 * </ul>
 *
 * <p>Order: 60
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@Order(60)
@RequiredArgsConstructor
public class OrphanCleanupHandler implements FileProcessorHandler {

  private final OpenlistApiService openlistApiService;

  // ==================== 接口实现 ====================

  @Override
  public ProcessingResult process(FileProcessingContext context) {
    try {
      if (context.getCurrentFile() != null) {
        log.trace("单文件上下文跳过孤立文件清理: {}", context.getCurrentFile().getPath());
        return ProcessingResult.SKIPPED;
      }

      // 只在增量模式下执行清理
      if (!Boolean.TRUE.equals(context.getTaskConfig().getIsIncrement())) {
        log.debug("非增量模式，跳过孤立文件清理");
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      String strmBasePath = context.getTaskConfig().getStrmPath();
      @SuppressWarnings("unchecked")
      List<OpenlistApiService.OpenlistFile> allFiles =
          (List<OpenlistApiService.OpenlistFile>) context.getAttribute("originalFiles");

      // 兼容处理：如果 originalFiles 不存在，使用 discoveredFiles
      if (allFiles == null || allFiles.isEmpty()) {
        allFiles = (List<OpenlistApiService.OpenlistFile>) context.getAttribute("discoveredFiles");
      }

      if (allFiles == null || allFiles.isEmpty()) {
        log.debug("没有发现文件，跳过清理");
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      log.info("开始检查孤立文件，使用 {} 个 OpenList 文件进行匹配", allFiles.size());

      // 执行清理
      CleanupResult result =
          cleanOrphanedFiles(strmBasePath, allFiles, context.getTaskConfig().getPath());

      log.info(
          "孤立文件清理完成: {} 个 STRM, {} 个 NFO, {} 个图片, {} 个字幕",
          result.strmCount,
          result.nfoCount,
          result.imageCount,
          result.subtitleCount);

      context.getStats().incrementProcessed();
      return ProcessingResult.SUCCESS;

    } catch (Exception e) {
      log.error("孤立文件清理失败: {}", e.getMessage(), e);
      context.getStats().incrementFailed();
      return ProcessingResult.FAILED;
    }
  }

  @Override
  public java.util.Set<FileType> getHandledTypes() {
    return java.util.Set.of(FileType.ALL);
  }

  // ==================== 清理逻辑 ====================

  /** 清理孤立文件 */
  private CleanupResult cleanOrphanedFiles(
      String strmBasePath, List<OpenlistApiService.OpenlistFile> openlistFiles, String taskPath) {

    AtomicInteger strmCount = new AtomicInteger(0);
    AtomicInteger nfoCount = new AtomicInteger(0);
    AtomicInteger imageCount = new AtomicInteger(0);
    AtomicInteger subtitleCount = new AtomicInteger(0);

    try {
      Path strmPath = Paths.get(strmBasePath);
      if (!Files.exists(strmPath)) {
        return new CleanupResult(0, 0, 0, 0);
      }

      // 深度优先遍历清理
      validateAndCleanDirectory(
          strmPath,
          openlistFiles,
          taskPath,
          "",
          "",
          strmCount,
          nfoCount,
          imageCount,
          subtitleCount);

    } catch (Exception e) {
      log.error("清理孤立文件失败", e);
    }

    return new CleanupResult(
        strmCount.get(), nfoCount.get(), imageCount.get(), subtitleCount.get());
  }

  /** 递归验证和清理目录 */
  private void validateAndCleanDirectory(
      Path strmDirectoryPath,
      List<OpenlistApiService.OpenlistFile> openlistFiles,
      String taskPath,
      String openlistRelativePath,
      String renameRegex,
      AtomicInteger strmCount,
      AtomicInteger nfoCount,
      AtomicInteger imageCount,
      AtomicInteger subtitleCount) {

    try {
      // 1. 先处理所有子目录
      List<Path> subDirectories;
      try (Stream<Path> stream = Files.list(strmDirectoryPath)) {
        subDirectories =
            stream
                .filter(Files::isDirectory)
                .sorted()
                .collect(java.util.stream.Collectors.toList());
      }

      for (Path subDir : subDirectories) {
        String subDirName = subDir.getFileName().toString();
        String subOpenlistPath =
            openlistRelativePath.isEmpty() ? subDirName : openlistRelativePath + "/" + subDirName;

        validateAndCleanDirectory(
            subDir,
            openlistFiles,
            taskPath,
            subOpenlistPath,
            renameRegex,
            strmCount,
            nfoCount,
            imageCount,
            subtitleCount);
      }

      // 2. 处理当前目录的文件
      cleanFilesInDirectory(
          strmDirectoryPath,
          openlistFiles,
          taskPath,
          openlistRelativePath,
          renameRegex,
          strmCount,
          nfoCount,
          imageCount,
          subtitleCount);

      // 3. 检查是否为空目录
      if (isDirectoryEmpty(strmDirectoryPath)) {
        try {
          Files.delete(strmDirectoryPath);
          log.debug("删除空目录: {}", strmDirectoryPath);
        } catch (Exception e) {
          log.warn("删除空目录失败: {}", strmDirectoryPath, e);
        }
      }

    } catch (Exception e) {
      log.error("处理目录失败: {}", strmDirectoryPath, e);
    }
  }

  /** 清理目录中的孤立文件 */
  private void cleanFilesInDirectory(
      Path directoryPath,
      List<OpenlistApiService.OpenlistFile> openlistFiles,
      String taskPath,
      String openlistRelativePath,
      String renameRegex,
      AtomicInteger strmCount,
      AtomicInteger nfoCount,
      AtomicInteger imageCount,
      AtomicInteger subtitleCount) {

    try (Stream<Path> stream = Files.list(directoryPath)) {
      List<Path> files =
          stream.filter(Files::isRegularFile).collect(java.util.stream.Collectors.toList());

      for (Path filePath : files) {
        String fileName = filePath.getFileName().toString().toLowerCase();

        // 检查是否为 STRM 文件
        if (fileName.endsWith(".strm")) {
          String baseName = fileName.substring(0, fileName.length() - 5);
          if (!isFileExistsInOpenList(baseName, openlistFiles)) {
            // 清理关联的刮削文件
            cleanupScrapingFiles(filePath, nfoCount, imageCount, subtitleCount);
            // 删除 STRM 文件
            Files.deleteIfExists(filePath);
            strmCount.incrementAndGet();
            log.info("删除孤立 STRM 文件: {} (源视频文件在 OpenList 中不存在)", filePath);
          } else {
            log.debug("保留 STRM 文件: {} (源视频文件存在于 OpenList)", filePath);
          }
        }
        // 检查是否为 NFO 文件
        else if (fileName.endsWith(".nfo")) {
          String baseName = fileName.substring(0, fileName.length() - 4);
          if (!isFileExistsInOpenList(baseName, openlistFiles)) {
            Files.deleteIfExists(filePath);
            nfoCount.incrementAndGet();
            log.info("删除孤立 NFO 文件: {} (对应的视频源文件在 OpenList 中不存在)", filePath);
          } else {
            log.debug("保留 NFO 文件: {} (对应的视频源文件存在于 OpenList)", filePath);
          }
        }
        // 检查是否为图片文件
        else if (isImageFile(fileName)) {
          String baseName = extractBaseNameFromImage(fileName);
          if (baseName != null && !isFileExistsInOpenList(baseName, openlistFiles)) {
            Files.deleteIfExists(filePath);
            imageCount.incrementAndGet();
            log.info("删除孤立图片文件: {} (对应的视频源文件在 OpenList 中不存在)", filePath);
          } else {
            log.debug("保留图片文件: {} (对应的视频源文件存在于 OpenList)", filePath);
          }
        }
        // 检查是否为字幕文件
        else if (isSubtitleFile(fileName)) {
          String baseName = extractBaseNameFromSubtitle(fileName);
          if (baseName != null && !isFileExistsInOpenList(baseName, openlistFiles)) {
            Files.deleteIfExists(filePath);
            subtitleCount.incrementAndGet();
            log.info("删除孤立字幕文件: {} (对应的视频源文件在 OpenList 中不存在)", filePath);
          } else {
            log.debug("保留字幕文件: {} (对应的视频源文件存在于 OpenList)", filePath);
          }
        }
      }
    } catch (Exception e) {
      log.error("清理目录文件失败: {}", directoryPath, e);
    }
  }

  /** 清理关联的刮削文件 */
  private void cleanupScrapingFiles(
      Path strmFilePath,
      AtomicInteger nfoCount,
      AtomicInteger imageCount,
      AtomicInteger subtitleCount) {

    String baseName = strmFilePath.getFileName().toString();
    if (baseName.endsWith(".strm")) {
      baseName = baseName.substring(0, baseName.length() - 5);
    }

    Path parentDir = strmFilePath.getParent();

    // 删除 NFO 文件
    Path nfoFile = parentDir.resolve(baseName + ".nfo");
    if (Files.exists(nfoFile)) {
      try {
        Files.delete(nfoFile);
        nfoCount.incrementAndGet();
      } catch (IOException e) {
        log.warn("删除NFO文件失败: {}", nfoFile, e);
      }
    }

    // 删除图片文件
    String[] imageExtensions = {"-poster.jpg", "-fanart.jpg", "-thumb.jpg"};
    for (String ext : imageExtensions) {
      Path imageFile = parentDir.resolve(baseName + ext);
      if (Files.exists(imageFile)) {
        try {
          Files.delete(imageFile);
          imageCount.incrementAndGet();
        } catch (IOException e) {
          log.warn("删除图片文件失败: {}", imageFile, e);
        }
      }
    }

    // 删除字幕文件
    String[] subtitleExtensions = {".srt", ".ass", ".vtt", ".ssa", ".sub", ".idx"};
    for (String ext : subtitleExtensions) {
      Path subtitleFile = parentDir.resolve(baseName + ext);
      if (Files.exists(subtitleFile)) {
        try {
          Files.delete(subtitleFile);
          subtitleCount.incrementAndGet();
        } catch (IOException e) {
          log.warn("删除字幕文件失败: {}", subtitleFile, e);
        }
      }
    }

    // 检查电视剧共用文件
    checkAndCleanupTvShowSharedFiles(parentDir, nfoCount, imageCount);
  }

  /** 检查并清理电视剧共用文件 */
  private void checkAndCleanupTvShowSharedFiles(
      Path directoryPath, AtomicInteger nfoCount, AtomicInteger imageCount) {

    // 检查目录中是否还有其他视频文件
    try (Stream<Path> stream = Files.list(directoryPath)) {
      boolean hasOtherVideoFiles =
          stream
              .filter(Files::isRegularFile)
              .anyMatch(
                  path -> {
                    String name = path.getFileName().toString().toLowerCase();
                    return name.endsWith(".strm") || isVideoFile(name);
                  });

      if (!hasOtherVideoFiles) {
        // 删除电视剧共用文件
        Path tvshowNfo = directoryPath.resolve("tvshow.nfo");
        Path poster = directoryPath.resolve("poster.jpg");
        Path fanart = directoryPath.resolve("fanart.jpg");

        if (Files.exists(tvshowNfo)) {
          Files.delete(tvshowNfo);
          nfoCount.incrementAndGet();
        }
        if (Files.exists(poster)) {
          Files.delete(poster);
          imageCount.incrementAndGet();
        }
        if (Files.exists(fanart)) {
          Files.delete(fanart);
          imageCount.incrementAndGet();
        }
      }
    } catch (Exception e) {
      log.warn("检查电视剧共用文件失败: {}", directoryPath, e);
    }
  }

  // ==================== 工具方法 ====================

  /** 检查文件是否存在于 OpenList */
  private boolean isFileExistsInOpenList(
      String baseName, List<OpenlistApiService.OpenlistFile> openlistFiles) {

    // 规范化基础名（移除扩展名后转小写）
    String normalizedBaseName = normalizeFileName(baseName);

    return openlistFiles.stream()
        .filter(f -> "file".equals(f.getType()))
        .filter(f -> isVideoFile(f.getName()))
        .anyMatch(
            f -> {
              // 同样规范化 OpenList 文件名
              String openlistBaseName = normalizeFileName(getBaseName(f.getName()));
              return normalizedBaseName.equals(openlistBaseName);
            });
  }

  /** 规范化文件名用于匹配 保留中文和特殊字符，只转小写，移除首尾空白 */
  private String normalizeFileName(String fileName) {
    if (fileName == null) return "";
    return fileName.toLowerCase().trim();
  }

  /** 获取文件名基础名（无扩展名） */
  private String getBaseName(String fileName) {
    if (fileName == null) return "";
    String lower = fileName.toLowerCase();
    int lastDot = lower.lastIndexOf('.');
    return lastDot > 0 ? lower.substring(0, lastDot) : lower;
  }

  private boolean isDirectoryEmpty(Path directoryPath) {
    try (Stream<Path> stream = Files.list(directoryPath)) {
      return stream.noneMatch(path -> !path.getFileName().toString().equals(".DS_Store"));
    } catch (Exception e) {
      return false;
    }
  }

  private boolean isVideoFile(String fileName) {
    if (fileName == null) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".mp4")
        || lower.endsWith(".mkv")
        || lower.endsWith(".avi")
        || lower.endsWith(".mov")
        || lower.endsWith(".wmv")
        || lower.endsWith(".flv")
        || lower.endsWith(".webm")
        || lower.endsWith(".m4v");
  }

  private boolean isImageFile(String fileName) {
    if (fileName == null) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith("-poster.jpg")
        || lower.endsWith("-fanart.jpg")
        || lower.endsWith("-thumb.jpg");
  }

  private boolean isSubtitleFile(String fileName) {
    if (fileName == null) return false;
    String lower = fileName.toLowerCase();
    return lower.endsWith(".srt")
        || lower.endsWith(".ass")
        || lower.endsWith(".vtt")
        || lower.endsWith(".ssa")
        || lower.endsWith(".sub")
        || lower.endsWith(".idx");
  }

  private String extractBaseNameFromImage(String fileName) {
    if (fileName.endsWith("-poster.jpg")
        || fileName.endsWith("-fanart.jpg")
        || fileName.endsWith("-thumb.jpg")) {
      return fileName.substring(0, fileName.length() - 11);
    }
    return null;
  }

  private String extractBaseNameFromSubtitle(String fileName) {
    for (String ext : List.of(".srt", ".ass", ".vtt", ".ssa", ".sub", ".idx")) {
      if (fileName.toLowerCase().endsWith(ext)) {
        return fileName.substring(0, fileName.length() - ext.length());
      }
    }
    return null;
  }

  /** 内部类：清理结果 */
  private static class CleanupResult {
    final int strmCount;
    final int nfoCount;
    final int imageCount;
    final int subtitleCount;

    CleanupResult(int strmCount, int nfoCount, int imageCount, int subtitleCount) {
      this.strmCount = strmCount;
      this.nfoCount = nfoCount;
      this.imageCount = imageCount;
      this.subtitleCount = subtitleCount;
    }
  }
}
