package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.handler.context.TaskScrapingSession;
import com.hienao.openlist2strm.service.OpenlistApiService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 字幕文件复制处理器
 *
 * <p>负责字幕文件的三级优先级处理：
 *
 * <ol>
 *   <li>优先级 1 - 本地文件：检查本地是否存在对应字幕文件
 *   <li>优先级 2 - OpenList 文件：本地不存在时从 OpenList 同级目录下载
 *   <li>优先级 3 - 无刮削选项：字幕文件不支持 API 刮削
 * </ol>
 *
 * <p>Order: 42
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@Order(42)
@RequiredArgsConstructor
public class SubtitleCopyHandler implements FileProcessorHandler {

  private final FilePriorityResolver priorityResolver;
  private final OpenlistApiService openlistApiService;

  // ==================== 支持的字幕文件扩展名 ====================

  private static final Set<String> SUBTITLE_EXTENSIONS =
      Set.of(".srt", ".ass", ".vtt", ".ssa", ".sub", ".idx");

  // ==================== 接口实现 ====================

  @Override
  public ProcessingResult process(FileProcessingContext context) {
    try {
      // 1. 检查配置是否启用
      boolean keepSubtitleEnabled = isKeepSubtitleEnabled(context);

      if (!keepSubtitleEnabled) {
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      // 2. 获取当前目录的所有字幕文件
      String currentDirectory =
          context
              .getCurrentFile()
              .getPath()
              .substring(0, context.getCurrentFile().getPath().lastIndexOf('/') + 1);
      TaskScrapingSession session = context.getAttribute("scrapingSession");
      if (session != null && session.processedSubtitleDirectories().contains(currentDirectory)) {
        return ProcessingResult.SKIPPED;
      }

      java.util.List<OpenlistApiService.OpenlistFile> allDirectoryFiles =
          context.getDirectoryFiles();

      java.util.List<OpenlistApiService.OpenlistFile> subtitleFiles =
          allDirectoryFiles.stream()
              .filter(f -> "file".equals(f.getType()))
              .filter(f -> isSubtitleFile(f.getName()))
              .filter(f -> f.getPath().startsWith(currentDirectory))
              .toList();

      log.debug("找到 {} 个字幕文件", subtitleFiles.size());

      if (subtitleFiles.isEmpty()) {
        markDirectoryProcessed(session, currentDirectory);
        log.debug("没有需要处理的字幕文件");
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      // 3. 处理每个字幕文件
      int successCount = 0;
      for (OpenlistApiService.OpenlistFile subtitleFile : subtitleFiles) {
        if (copySubtitleFile(context, subtitleFile)) {
          successCount++;
        }
      }
      markDirectoryProcessed(session, currentDirectory);

      if (successCount > 0) {
        log.info("成功复制 {} 个字幕文件", successCount);
        context.getStats().incrementProcessed();
        return ProcessingResult.SUCCESS;
      }

      context.getStats().incrementSkipped();
      return ProcessingResult.SKIPPED;

    } catch (Exception e) {
      log.error("字幕文件处理失败: {}", context.getBaseFileName(), e);
      context.getStats().incrementFailed();
      return ProcessingResult.FAILED;
    }
  }

  @Override
  public Set<FileType> getHandledTypes() {
    return Set.of(FileType.SUBTITLE, FileType.VIDEO);
  }

  // ==================== 字幕复制逻辑 ====================

  /** 复制单个字幕文件 */
  private boolean copySubtitleFile(
      FileProcessingContext context, OpenlistApiService.OpenlistFile subtitleFile) {

    String saveDirectory = context.getSaveDirectory();
    String fileName = subtitleFile.getName();

    try {
      // 1. 检查本地是否已存在
      Path localPath = Paths.get(saveDirectory, fileName);
      if (Files.exists(localPath)) {
        log.debug("本地字幕文件已存在，跳过: {}", fileName);
        return true;
      }

      // 2. 构建下载URL并进行编码
      String downloadUrl = subtitleFile.getUrl();
      if (subtitleFile.getSign() != null && !subtitleFile.getSign().isEmpty()) {
        downloadUrl = downloadUrl + "?sign=" + subtitleFile.getSign();
      }
      // 使用统一的智能编码方法处理中文路径
      downloadUrl = com.hienao.openlist2strm.util.UrlEncoder.encodeUrlSmart(downloadUrl);

      // 3. 从 OpenList 下载
      if (openlistApiService.downloadToFile(
          context.getOpenlistConfig(), subtitleFile, downloadUrl, localPath)) {
        log.info("已复制字幕文件: {} -> {}", fileName, localPath);
        return true;
      }

      log.debug("字幕文件内容为空: {}", fileName);
      return false;

    } catch (Exception e) {
      log.warn("复制字幕文件失败: {}, 错误: {}", fileName, e.getMessage());
      return false;
    }
  }

  // ==================== 配置检查 ====================

  /** 检查是否启用保留字幕文件 */
  private boolean isKeepSubtitleEnabled(FileProcessingContext context) {
    Object keepSubtitleValue = context.getAttribute("keepSubtitleFiles");
    return Boolean.TRUE.equals(keepSubtitleValue);
  }

  // ==================== 工具方法 ====================

  /** 检查是否为字幕文件 */
  public boolean isSubtitleFile(String fileName) {
    if (fileName == null) {
      return false;
    }
    String lower = fileName.toLowerCase();
    for (String ext : SUBTITLE_EXTENSIONS) {
      if (lower.endsWith(ext)) {
        return true;
      }
    }
    return false;
  }

  /** 获取字幕文件扩展名列表 */
  public Set<String> getSubtitleExtensions() {
    return new HashSet<>(SUBTITLE_EXTENSIONS);
  }

  private void markDirectoryProcessed(TaskScrapingSession session, String directory) {
    if (session != null) {
      session.processedSubtitleDirectories().add(directory);
    }
  }
}
