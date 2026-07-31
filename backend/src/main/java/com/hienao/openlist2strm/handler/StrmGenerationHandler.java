package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.service.OpenlistApiService;
import com.hienao.openlist2strm.service.StrmFileService;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * STRM 文件生成处理器
 *
 * <p>负责为视频文件生成 STRM 文件。
 *
 * <p>Order: 30
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@Order(30)
@RequiredArgsConstructor
public class StrmGenerationHandler implements FileProcessorHandler {

  private final StrmFileService strmFileService;

  // ==================== 接口实现 ====================

  @Override
  public ProcessingResult process(FileProcessingContext context) {
    OpenlistApiService.OpenlistFile currentFile = context.getCurrentFile();
    if (currentFile == null) {
      log.debug("没有当前文件，跳过 STRM 生成");
      return ProcessingResult.SUCCESS;
    }

    try {
      String fileName = currentFile.getName();
      String relativePath = context.getRelativePath();
      String strmPath = context.getTaskConfig().getStrmPath();
      String renameRegex = context.getTaskConfig().getRenameRegex();
      OpenlistConfig openlistConfig = context.getOpenlistConfig();
      boolean isIncrement =
          Boolean.TRUE.equals(
              context.getAttribute(
                  "executionIncremental",
                  Boolean.TRUE.equals(context.getTaskConfig().getIsIncrement())));

      // 构建文件 URL 并添加 sign 参数
      String fileUrlWithSign = buildFileUrlWithSign(currentFile.getUrl(), currentFile.getSign());

      // 生成 STRM 文件
      strmFileService.generateStrmFile(
          strmPath,
          relativePath,
          fileName,
          fileUrlWithSign,
          isIncrement,
          renameRegex,
          openlistConfig);

      context.getStats().incrementProcessed();
      return ProcessingResult.SUCCESS;

    } catch (Exception e) {
      log.error("生成 STRM 文件失败: {}, 错误: {}", currentFile.getName(), e.getMessage(), e);
      context.getStats().incrementFailed();
      return ProcessingResult.FAILED;
    }
  }

  @Override
  public Set<FileType> getHandledTypes() {
    return Set.of(FileType.VIDEO);
  }

  // ==================== URL 处理 ====================

  /** 构建包含 sign 参数的文件 URL */
  private String buildFileUrlWithSign(String originalUrl, String sign) {
    if (originalUrl == null) {
      return null;
    }

    String processedUrl = originalUrl;

    // 添加 sign 参数
    if (sign != null && !sign.trim().isEmpty()) {
      String separator = processedUrl.contains("?") ? "&" : "?";
      processedUrl = processedUrl + separator + "sign=" + sign;
    }

    return processedUrl;
  }
}
