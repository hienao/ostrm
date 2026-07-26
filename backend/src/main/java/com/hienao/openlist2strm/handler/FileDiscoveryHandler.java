package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.service.OpenlistApiService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 文件发现处理器
 *
 * <p>负责递归遍历目录，发现所有文件和子目录。
 *
 * <p>Order: 10
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class FileDiscoveryHandler implements FileProcessorHandler {

  private final OpenlistApiService openlistApiService;

  // ==================== 接口实现 ====================

  @Override
  public ProcessingResult process(FileProcessingContext context) {
    try {
      if (context.getCurrentFile() != null) {
        log.trace("单文件上下文已包含当前文件，跳过文件发现: {}", context.getCurrentFile().getPath());
        return ProcessingResult.SUCCESS;
      }

      log.debug("开始文件发现: {}", context.getRelativePath());

      List<OpenlistApiService.OpenlistFile> allFiles = new ArrayList<>();

      // 递归遍历目录
      processDirectory(
          context.getOpenlistConfig(),
          context.getTaskConfig().getPath(),
          context.getTaskConfig(),
          allFiles);

      // 将发现的文件列表设置到上下文中
      context.setAttribute("discoveredFiles", allFiles);
      context.getStats().setTotalFiles(allFiles.size());

      log.info("文件发现完成，共发现 {} 个文件/目录", allFiles.size());
      return ProcessingResult.SUCCESS;

    } catch (Exception e) {
      log.error("文件发现失败: {}", e.getMessage(), e);
      return ProcessingResult.FAILED;
    }
  }

  @Override
  public Set<FileType> getHandledTypes() {
    return Set.of(FileType.ALL); // 发现所有类型的文件
  }

  // ==================== 递归目录遍历 ====================

  /**
   * 递归处理目录
   *
   * @param openlistConfig OpenList 配置
   * @param path 当前路径
   * @param taskConfig 任务配置
   * @param allFiles 收集的文件列表
   */
  private void processDirectory(
      OpenlistConfig openlistConfig,
      String path,
      TaskConfig taskConfig,
      List<OpenlistApiService.OpenlistFile> allFiles) {

    try {
      List<OpenlistApiService.OpenlistFile> files =
          openlistApiService.getDirectoryContents(openlistConfig, path);

      for (OpenlistApiService.OpenlistFile file : files) {
        allFiles.add(file);

        if ("folder".equals(file.getType())) {
          // 递归处理子目录
          String subPath = file.getPath();
          if (subPath == null || subPath.isEmpty()) {
            subPath = path + "/" + file.getName();
          }
          processDirectory(openlistConfig, subPath, taskConfig, allFiles);
        }
      }

      log.trace("处理目录完成: {}, 包含 {} 个文件", path, files.size());

    } catch (Exception e) {
      log.warn("处理目录失败: {}, 错误: {}", path, e.getMessage());
      // 不抛出异常，继续处理其他目录
    }
  }
}
