/*
 * OStrm - Stream Management System
 * Copyright (C) 2024 OStrm Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.entity.MediaLibraryType;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.exception.RenameOperationException;
import com.hienao.openlist2strm.handler.FileProcessorChain;
import com.hienao.openlist2strm.handler.ProcessingResult;
import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.handler.context.TaskScrapingSession;
import com.hienao.openlist2strm.notification.NotificationEvent;
import com.hienao.openlist2strm.notification.NotificationIssue;
import com.hienao.openlist2strm.notification.ScrapeOutcome;
import com.hienao.openlist2strm.util.TaskDirectoryStructureValidator;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务执行服务类
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskExecutionService {

  private static final int MAX_INVALID_STRUCTURE_LOGS = 20;

  private final TaskConfigService taskConfigService;
  private final OpenlistConfigService openlistConfigService;
  private final OpenlistApiService openlistApiService;
  private final StrmFileService strmFileService;
  private final MediaScrapingService mediaScrapingService;
  private final SystemConfigService systemConfigService;
  private final TaskManifestService taskManifestService;
  private final ManualScrapingService manualScrapingService;
  private final NotificationService notificationService;
  private final MediaServerApiService mediaServerApiService;
  private final FileProcessorChain fileProcessorChain;
  private final Executor taskSubmitExecutor;
  private final Object taskSubmissionLock = new Object();
  private final Map<Long, CompletableFuture<Void>> activeTasks = new ConcurrentHashMap<>();

  /**
   * 提交任务到线程池执行
   *
   * @param taskId 任务ID
   * @param isIncrement 是否增量执行（可选参数）
   */
  public void submitTask(Long taskId, Boolean isIncrement) {
    scheduleTask(taskId, isIncrement, NotificationEvent.Trigger.MANUAL);
  }

  public void submitScheduledTask(Long taskId, Boolean isIncrement) {
    scheduleTask(taskId, isIncrement, NotificationEvent.Trigger.SCHEDULED);
  }

  /**
   * 同步执行任务（在线程池中调用）
   *
   * @param taskId 任务ID
   * @param isIncrement 是否增量执行（可选参数）
   */
  private void executeTaskSync(
      Long taskId, Boolean isIncrement, NotificationEvent.Trigger trigger) {
    long startedNanos = System.nanoTime();
    TaskConfig taskConfig = null;
    boolean useIncrement = false;
    try {
      log.info(
          "开始执行任务 - 任务ID: {}, 增量模式: {}, 线程: {}",
          taskId,
          isIncrement,
          Thread.currentThread().getName());

      // 获取任务配置
      taskConfig = taskConfigService.getById(taskId);
      if (taskConfig == null) {
        throw new BusinessException("任务配置不存在，ID: " + taskId);
      }

      // 检查任务是否启用
      if (!Boolean.TRUE.equals(taskConfig.getIsActive())) {
        throw new BusinessException("任务已禁用，无法执行，ID: " + taskId);
      }

      // 确定是否使用增量模式
      if (isIncrement != null) {
        // 如果传了参数，以传参为主
        useIncrement = isIncrement;
        log.info("使用传入的增量参数: {}", isIncrement);
      } else {
        // 如果没传参数，以任务配置为主
        useIncrement = Boolean.TRUE.equals(taskConfig.getIsIncrement());
        log.info("使用任务配置的增量参数: {}", useIncrement);
      }

      // 更新任务开始执行时间
      taskConfigService.updateLastExecTime(taskId, LocalDateTime.now());

      // 执行具体的任务逻辑
      NotificationEvent event = executeTaskLogic(taskConfig, useIncrement);
      applyMediaServerRefresh(event, taskConfig, useIncrement);
      event.setTrigger(trigger);
      event.setDurationMillis(elapsedMillis(startedNanos));
      event.setCompletedAt(LocalDateTime.now());
      notifySafely(event);

      log.info(
          "任务执行完成 - 任务ID: {}, 任务名称: {}, 增量模式: {}", taskId, taskConfig.getTaskName(), useIncrement);

    } catch (Exception e) {
      log.error("任务执行失败 - 任务ID: {}, 错误信息: {}", taskId, e.getMessage(), e);
      if (taskConfig != null) {
        notifySafely(
            NotificationEvent.builder()
                .kind(NotificationEvent.Kind.TASK)
                .status(NotificationEvent.Status.FAILURE)
                .trigger(trigger)
                .taskId(taskConfig.getId())
                .taskName(taskConfig.getTaskName())
                .executionMode(useIncrement ? "增量" : "全量")
                .libraryType(taskConfig.getLibraryType())
                .sourcePath(taskConfig.getPath())
                .strmPath(taskConfig.getStrmPath())
                .failedStage(failureStage(e))
                .errorMessage(rootMessage(e))
                .durationMillis(elapsedMillis(startedNanos))
                .completedAt(LocalDateTime.now())
                .build());
      }
      throw new BusinessException("任务执行失败: " + e.getMessage(), e);
    }
  }

  private void notifySafely(NotificationEvent event) {
    try {
      notificationService.notifyAsync(event);
    } catch (Exception e) {
      // 通知是附加能力，不得因配置、渲染或线程池异常改变任务结果。
      log.error("提交任务通知失败 - 任务ID: {}", event.getTaskId(), e);
    }
  }

  private void applyMediaServerRefresh(
      NotificationEvent event, TaskConfig taskConfig, boolean incremental) {
    boolean hasChanges =
        event.getSelectedVideos() > 0
            || event.getCleanedStrm() > 0
            || event.getRenamedDirectories() > 0
            || event.getRenamedFiles() > 0;
    MediaServerRefreshResult result =
        mediaServerApiService.refreshAfterTask(taskConfig, incremental, hasChanges);
    event.setMediaServerName(result.serverName());
    event.setMediaServerType(result.serverType());
    event.setMediaRefreshScope(result.scope());
    event.setMediaLibraryName(result.libraryName());
    event.setMediaRefreshStatus(result.status().name());
    event.setMediaRefreshMessage(result.message());
    if (result.status() == MediaServerRefreshResult.Status.FAILED) {
      event.setStatus(NotificationEvent.Status.PARTIAL_SUCCESS);
      List<NotificationIssue> issues = new ArrayList<>(event.getIssues());
      issues.add(
          NotificationIssue.builder()
              .category(NotificationIssue.Category.MEDIA_SERVER_REFRESH_FAILED)
              .reasonCode(result.failureCode())
              .scope(result.libraryName())
              .sourcePath(result.serverName())
              .reason(result.message())
              .build());
      event.setIssues(issues);
    }
  }

  /**
   * 异步执行任务（保留原有方法以兼容其他调用）
   *
   * @param taskId 任务ID
   * @param isIncrement 是否增量执行（可选参数）
   * @return CompletableFuture<Void>
   */
  public CompletableFuture<Void> executeTask(Long taskId, Boolean isIncrement) {
    return scheduleTask(taskId, isIncrement, NotificationEvent.Trigger.MANUAL);
  }

  private CompletableFuture<Void> scheduleTask(
      Long taskId, Boolean isIncrement, NotificationEvent.Trigger trigger) {
    synchronized (taskSubmissionLock) {
      CompletableFuture<Void> existing = activeTasks.get(taskId);
      if (existing != null && !existing.isDone()) {
        log.info("任务已在排队或执行中，忽略重复提交 - 任务ID: {}", taskId);
        return existing;
      }

      CompletableFuture<Void> future = new CompletableFuture<>();
      activeTasks.put(taskId, future);
      try {
        taskSubmitExecutor.execute(
            () -> {
              try {
                executeTaskSync(taskId, isIncrement, trigger);
                future.complete(null);
              } catch (Exception e) {
                future.completeExceptionally(e);
              } finally {
                activeTasks.remove(taskId, future);
              }
            });
      } catch (RuntimeException e) {
        activeTasks.remove(taskId, future);
        future.completeExceptionally(e);
        throw e;
      }
      log.info("任务已成功提交到线程池 - 任务ID: {}, 增量模式: {}", taskId, isIncrement);
      return future;
    }
  }

  /**
   * 执行具体的任务逻辑 1. 根据任务配置获取OpenList配置 2. 如果是全量执行，先清空STRM目录 3. 通过OpenList API递归获取所有文件 4. 对视频文件生成STRM文件
   * 5. 保持目录结构一致 6. 如果是增量执行，清理孤立的STRM文件
   *
   * @param taskConfig 任务配置
   * @param isIncrement 是否增量执行
   */
  private NotificationEvent executeTaskLogic(TaskConfig taskConfig, boolean isIncrement) {
    log.info("开始执行任务逻辑: {}, 增量模式: {}", taskConfig.getTaskName(), isIncrement);

    try {
      // 1. 获取OpenList配置
      OpenlistConfig openlistConfig = getOpenlistConfig(taskConfig);

      // 2. 使用 Handler 链处理方式执行任务
      log.info("使用 Handler 链处理方式执行任务");
      return executeTaskWithHandlerChain(taskConfig, openlistConfig, isIncrement);

    } catch (Exception e) {
      log.error("任务执行失败: {}, 错误: {}", taskConfig.getTaskName(), e.getMessage(), e);
      throw new BusinessException("任务执行失败: " + e.getMessage(), e);
    }
  }

  /** 使用 Handler 链执行任务（新方式） */
  private NotificationEvent executeTaskWithHandlerChain(
      TaskConfig taskConfig, OpenlistConfig openlistConfig, boolean isIncrement) {

    List<NotificationIssue> notificationIssues = new ArrayList<>();
    int renamedDirectoryCount = 0;
    int renamedFileCount = 0;

    // 1. 先获取目录文件列表（在清空目录之前验证 OpenList API 可用性）
    List<OpenlistApiService.OpenlistFile> allFiles;
    try {
      log.info("开始获取 OpenList 文件列表: {}", taskConfig.getPath());
      allFiles =
          openlistApiService.getAllFilesConcurrently(
              openlistConfig,
              taskConfig.getPath(),
              false,
              directoryReadConcurrency(openlistConfig));
    } catch (Exception e) {
      log.error("获取 OpenList 文件列表失败，终止任务执行，STRM 目录未受影响: {}", e.getMessage(), e);
      throw new TaskStageException("DISCOVERY", "获取 OpenList 文件列表失败，任务终止: " + e.getMessage(), e);
    }

    // 2. 验证文件列表有效性（空列表也继续执行，可能该路径下确实没有文件）
    log.info("成功获取 OpenList 文件列表，共 {} 个文件/目录", allFiles.size());

    // 普通任务的自动重命名必须先于 STRM 生成；完成后重新读取清单，确保 URL 和相对路径都是最新值。
    if (shouldAutoRename(taskConfig)) {
      List<String> mediaDirectories = autoRenameDirectories(taskConfig, allFiles);
      int skippedDirectories = 0;
      for (String mediaDirectory : mediaDirectories) {
        try {
          ManualScrapingService.AutoRenameResult result =
              manualScrapingService.autoRenameForTaskExecution(taskConfig.getId(), mediaDirectory);
          renamedDirectoryCount += result.renamedDirectoryCount();
          renamedFileCount += result.renamedFileCount();
          if (result.issue() != null) {
            notificationIssues.add(result.issue());
          }
          if (!result.matched()) {
            skippedDirectories++;
            log.warn("自动重命名跳过目录: {}, 原因: {}", mediaDirectory, result.message());
          }
        } catch (RenameOperationException e) {
          skippedDirectories++;
          notificationIssues.add(e.getIssue());
          log.warn("自动重命名目录失败，保留原名称继续执行: {}, 错误: {}", mediaDirectory, e.getMessage());
        } catch (Exception e) {
          skippedDirectories++;
          notificationIssues.add(
              NotificationIssue.builder()
                  .category(NotificationIssue.Category.PROCESSING_FAILED)
                  .scope("自动重命名")
                  .sourcePath(mediaDirectory)
                  .reason(rootMessage(e))
                  .build());
          log.warn("自动重命名目录失败，保留原名称继续执行: {}, 错误: {}", mediaDirectory, e.getMessage());
        }
      }
      if (!mediaDirectories.isEmpty()) {
        log.info(
            "自动重命名阶段完成: 媒体目录 {}, 重命名目录 {}, 重命名文件 {}, 跳过 {}",
            mediaDirectories.size(),
            renamedDirectoryCount,
            renamedFileCount,
            skippedDirectories);
        try {
          allFiles =
              openlistApiService.getAllFilesConcurrently(
                  openlistConfig,
                  taskConfig.getPath(),
                  false,
                  directoryReadConcurrency(openlistConfig));
        } catch (Exception e) {
          throw new TaskStageException(
              "AUTO_RENAMING", "自动重命名后重新读取 OpenList 文件列表失败: " + e.getMessage(), e);
        }
      }
    }

    // 3. 文件列表获取成功后，全量模式下再清空 STRM 目录
    if (!isIncrement) {
      log.info("全量执行模式，文件列表获取成功（共 {} 个），开始清理STRM目录: {}", allFiles.size(), taskConfig.getStrmPath());
      strmFileService.clearStrmDirectory(taskConfig.getStrmPath());
    }

    // 4. 创建处理上下文
    FileProcessingContext context =
        FileProcessingContext.builder()
            .openlistConfig(openlistConfig)
            .taskConfig(taskConfig)
            .build();

    context.getStats().setTotalFiles(allFiles.size());

    // 5. 过滤出视频文件
    List<OpenlistApiService.OpenlistFile> discoveredVideoFiles =
        allFiles.stream()
            .filter(f -> "file".equals(f.getType()))
            .filter(f -> strmFileService.isVideoFile(f.getName()))
            .toList();
    StructureFilterResult structureFilter =
        filterVideoFilesByStructure(taskConfig, discoveredVideoFiles);
    List<OpenlistApiService.OpenlistFile> allVideoFiles = structureFilter.eligibleVideoFiles();
    List<OpenlistApiService.OpenlistFile> effectiveFiles =
        structureFilter.skippedVideoPaths().isEmpty()
            ? allFiles
            : allFiles.stream()
                .filter(file -> !structureFilter.skippedVideoPaths().contains(file.getPath()))
                .toList();

    // 保存过滤后的有效文件列表，用于增量清单和孤立 STRM 清理
    context.setAttribute("originalFiles", effectiveFiles);
    context.setAttribute("discoveredFiles", effectiveFiles);

    // 6. 获取本次任务的不可变配置快照，并为目录内资源建立索引
    Map<String, Object> systemConfig = systemConfigService.getSystemConfig();
    Map<String, Object> scrapingConfig = getNestedConfig(systemConfig, "scraping");
    Map<String, Object> scrapingRegexConfig = getNestedConfig(systemConfig, "scrapingRegex");
    Map<String, Object> tmdbConfig = getNestedConfig(systemConfig, "tmdb");
    Map<String, Object> aiConfig = getNestedConfig(systemConfig, "ai");
    boolean needScrap = Boolean.TRUE.equals(taskConfig.getNeedScrap());
    TaskScrapingSession scrapingSession = new TaskScrapingSession();
    String manifestFingerprint =
        manifestConfigurationFingerprint(taskConfig, openlistConfig, systemConfig);
    TaskManifestService.ChangeSet changeSet =
        taskManifestService.detectChanges(
            taskConfig.getId(), taskConfig.getPath(), manifestFingerprint, effectiveFiles);
    List<OpenlistApiService.OpenlistFile> videoFiles =
        !isIncrement
            ? allVideoFiles
            : allVideoFiles.stream()
                .filter(
                    file ->
                        changeSet.changedDirectories().contains(parentDirectory(file.getPath()))
                            || !strmFileExists(taskConfig, file))
                .toList();
    if (isIncrement) {
      log.info(
          "增量清单筛选完成: 总视频 {}, 本轮需处理 {}, 变化目录 {}",
          allVideoFiles.size(),
          videoFiles.size(),
          changeSet.changedDirectories().size());
    }
    Map<String, List<OpenlistApiService.OpenlistFile>> filesByDirectory =
        allFiles.stream()
            .collect(
                Collectors.groupingBy(
                    file -> parentDirectory(file.getPath()),
                    java.util.LinkedHashMap::new,
                    Collectors.toList()));

    // 7. 处理每个视频文件
    int processedCount = 0;
    int scrapSkippedCount = 0;
    int failedCount = 0;
    int strmSucceededCount = 0;

    for (OpenlistApiService.OpenlistFile videoFile : videoFiles) {
      // 构建单个文件的上下文
      FileProcessingContext fileContext =
          createFileContext(
              context,
              videoFile,
              openlistConfig,
              scrapingConfig,
              scrapingRegexConfig,
              tmdbConfig,
              aiConfig,
              filesByDirectory,
              scrapingSession,
              isIncrement);

      // 执行处理器链
      ProcessingResult processingResult = fileProcessorChain.execute(fileContext);
      if (processingResult == ProcessingResult.FAILED) {
        failedCount++;
      }
      if (strmFileExists(taskConfig, videoFile)) {
        strmSucceededCount++;
      }
      ScrapeOutcome scrapeOutcome = fileContext.getAttribute("scrapeOutcome");
      if (scrapeOutcome != null && scrapeOutcome.isUnrecognized()) {
        notificationIssues.add(scrapeIssue(videoFile, scrapeOutcome));
      } else if (scrapeOutcome != null && scrapeOutcome.status() == ScrapeOutcome.Status.FAILED) {
        notificationIssues.add(
            NotificationIssue.builder()
                .category(NotificationIssue.Category.PROCESSING_FAILED)
                .scope("媒体刮削")
                .sourcePath(videoFile.getPath())
                .reason(scrapeOutcome.reason())
                .build());
      } else if (processingResult == ProcessingResult.FAILED) {
        notificationIssues.add(
            NotificationIssue.builder()
                .category(NotificationIssue.Category.PROCESSING_FAILED)
                .scope("文件处理")
                .sourcePath(videoFile.getPath())
                .reason("一个或多个处理步骤失败，请查看任务日志")
                .build());
      }

      // 更新统计
      if (fileContext.getStats().getProcessedFiles() > 0) {
        processedCount++;
      }
      if (fileContext.getStats().getSkippedFiles() > 0) {
        scrapSkippedCount++;
      }
    }

    log.info("Handler 链处理完成: 处理 {} 个视频文件, 跳过 {} 个", processedCount, scrapSkippedCount);

    // 8. 增量模式下清理孤立文件
    int cleanedCount = 0;
    if (isIncrement) {
      log.info("增量执行模式，开始清理孤立的STRM文件");
      cleanedCount =
          strmFileService.cleanOrphanedStrmFiles(
              taskConfig.getStrmPath(),
              effectiveFiles,
              taskConfig.getPath(),
              taskConfig.getRenameRegex(),
              openlistConfig);
      log.info("清理了 {} 个孤立的STRM文件", cleanedCount);
    }
    if (failedCount == 0) {
      taskManifestService.save(
          taskConfig.getId(), taskConfig.getPath(), manifestFingerprint, effectiveFiles);
    } else {
      log.warn("本轮有 {} 个视频处理失败，不更新增量清单", failedCount);
    }
    NotificationEvent.Status status =
        failedCount > 0 || !notificationIssues.isEmpty()
            ? NotificationEvent.Status.PARTIAL_SUCCESS
            : NotificationEvent.Status.SUCCESS;
    return NotificationEvent.builder()
        .kind(NotificationEvent.Kind.TASK)
        .status(status)
        .taskId(taskConfig.getId())
        .taskName(taskConfig.getTaskName())
        .executionMode(isIncrement ? "增量" : "全量")
        .libraryType(taskConfig.getLibraryType())
        .sourcePath(taskConfig.getPath())
        .strmPath(taskConfig.getStrmPath())
        .discoveredVideos(discoveredVideoFiles.size())
        .selectedVideos(videoFiles.size())
        .strmSucceeded(strmSucceededCount)
        .processingFailed(failedCount)
        .incrementalSkipped(Math.max(0, allVideoFiles.size() - videoFiles.size()))
        .structureSkipped(structureFilter.skippedVideoPaths().size())
        .cleanedStrm(cleanedCount)
        .renamedDirectories(renamedDirectoryCount)
        .renamedFiles(renamedFileCount)
        .issues(notificationIssues)
        .build();
  }

  private NotificationIssue scrapeIssue(
      OpenlistApiService.OpenlistFile videoFile, ScrapeOutcome outcome) {
    return NotificationIssue.builder()
        .category(NotificationIssue.Category.SCRAPE_UNRECOGNIZED)
        .reasonCode(outcome.status().name())
        .sourcePath(videoFile.getPath())
        .mediaTitle(outcome.mediaTitle())
        .tmdbId(outcome.tmdbId())
        .reason(outcome.reason())
        .build();
  }

  private long elapsedMillis(long startedNanos) {
    return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
  }

  private String failureStage(Exception exception) {
    Throwable current = exception;
    while (current != null) {
      if (current instanceof TaskStageException staged) {
        return staged.stage();
      }
      current = current.getCause();
    }
    return "PROCESSING";
  }

  private String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  private static final class TaskStageException extends BusinessException {
    private final String stage;

    private TaskStageException(String stage, String message, Throwable cause) {
      super(message, cause);
      this.stage = stage;
    }

    private String stage() {
      return stage;
    }
  }

  /** 创建单个文件的处理上下文 */
  private FileProcessingContext createFileContext(
      FileProcessingContext parentContext,
      OpenlistApiService.OpenlistFile videoFile,
      OpenlistConfig openlistConfig,
      Map<String, Object> scrapingConfig,
      Map<String, Object> scrapingRegexConfig,
      Map<String, Object> tmdbConfig,
      Map<String, Object> aiConfig,
      Map<String, List<OpenlistApiService.OpenlistFile>> filesByDirectory,
      TaskScrapingSession scrapingSession,
      boolean isIncrement) {

    // 计算相对路径
    String relativePath =
        strmFileService.calculateRelativePath(
            parentContext.getTaskConfig().getPath(), videoFile.getPath());

    // 构建保存目录
    String saveDirectory =
        buildScrapSaveDirectory(parentContext.getTaskConfig().getStrmPath(), relativePath);

    // 获取基础文件名
    String baseFileName = removeExtension(videoFile.getName());

    // 从任务级目录索引直接获取同级文件，避免为每个视频扫描完整文件列表
    List<OpenlistApiService.OpenlistFile> currentDirFiles =
        filesByDirectory.getOrDefault(parentDirectory(videoFile.getPath()), List.of());

    Map<String, Object> attributes = new HashMap<>();
    if (scrapingConfig != null) {
      attributes.putAll(scrapingConfig);
    }
    attributes.put("scrapingConfig", scrapingConfig);
    attributes.put("scrapingRegexConfig", scrapingRegexConfig);
    attributes.put("tmdbConfig", tmdbConfig);
    attributes.put("aiConfig", aiConfig);
    attributes.put("scrapingSession", scrapingSession);
    attributes.put("executionIncremental", isIncrement);

    return FileProcessingContext.builder()
        .openlistConfig(openlistConfig)
        .taskConfig(parentContext.getTaskConfig())
        .currentFile(videoFile)
        .relativePath(relativePath)
        .saveDirectory(saveDirectory)
        .baseFileName(baseFileName)
        .directoryFiles(currentDirFiles)
        .attributes(attributes)
        .build();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getNestedConfig(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }

  private String parentDirectory(String path) {
    if (path == null || path.isBlank()) {
      return "";
    }
    int slashIndex = path.lastIndexOf('/');
    return slashIndex <= 0 ? "/" : path.substring(0, slashIndex);
  }

  private int directoryReadConcurrency(OpenlistConfig config) {
    Integer qps = config.getFsApiQpsLimit();
    return qps != null && qps > 0 ? Math.min(4, qps) : 4;
  }

  private boolean shouldAutoRename(TaskConfig taskConfig) {
    return Boolean.TRUE.equals(taskConfig.getAutoRenameMedia())
        && Boolean.TRUE.equals(taskConfig.getNeedScrap())
        && MediaLibraryType.from(taskConfig.getLibraryType()) != MediaLibraryType.AUTO;
  }

  List<String> autoRenameDirectories(
      TaskConfig taskConfig, List<OpenlistApiService.OpenlistFile> files) {
    MediaLibraryType libraryType = MediaLibraryType.from(taskConfig.getLibraryType());
    String normalizedRoot = TaskDirectoryStructureValidator.normalizePath(taskConfig.getPath());
    java.util.Set<String> directories = new java.util.TreeSet<>();
    for (OpenlistApiService.OpenlistFile file : files) {
      if (!"file".equals(file.getType()) || !strmFileService.isVideoFile(file.getName())) {
        continue;
      }
      String relativePath =
          TaskDirectoryStructureValidator.calculateRelativePath(
              taskConfig.getPath(), file.getPath());
      if (TaskDirectoryStructureValidator.validate(relativePath, libraryType).isPresent()) {
        continue;
      }
      List<String> segments = TaskDirectoryStructureValidator.splitPath(relativePath);
      if (segments.size() < 2) {
        continue;
      }
      directories.add(
          "/".equals(normalizedRoot)
              ? normalizedRoot + segments.get(0)
              : normalizedRoot + "/" + segments.get(0));
    }
    return List.copyOf(directories);
  }

  private boolean strmFileExists(
      TaskConfig taskConfig, OpenlistApiService.OpenlistFile sourceFile) {
    String relativePath =
        strmFileService.calculateRelativePath(taskConfig.getPath(), sourceFile.getPath());
    return java.nio.file.Files.exists(
        strmFileService.resolveStrmFilePath(
            taskConfig.getStrmPath(),
            relativePath,
            sourceFile.getName(),
            taskConfig.getRenameRegex()));
  }

  private String manifestConfigurationFingerprint(
      TaskConfig taskConfig, OpenlistConfig openlistConfig, Map<String, Object> systemConfig) {
    return Integer.toHexString(
        Objects.hash(
            taskConfig.getPath(),
            taskConfig.getStrmPath(),
            taskConfig.getRenameRegex(),
            taskConfig.getNeedScrap(),
            taskConfig.getLibraryType(),
            taskConfig.getSkipInvalidStructure(),
            taskConfig.getAutoRenameMedia(),
            openlistConfig.getStrmBaseUrl(),
            openlistConfig.getEnableUrlEncoding(),
            systemConfig));
  }

  StructureFilterResult filterVideoFilesByStructure(
      TaskConfig taskConfig, List<OpenlistApiService.OpenlistFile> videoFiles) {
    if (!Boolean.TRUE.equals(taskConfig.getSkipInvalidStructure()) || videoFiles.isEmpty()) {
      return new StructureFilterResult(videoFiles, Set.of());
    }

    MediaLibraryType libraryType = MediaLibraryType.from(taskConfig.getLibraryType());
    if (libraryType == MediaLibraryType.AUTO) {
      log.info("任务已启用目录结构过滤，但自动识别类型没有固定结构，跳过过滤: {}", taskConfig.getTaskName());
      return new StructureFilterResult(videoFiles, Set.of());
    }

    List<OpenlistApiService.OpenlistFile> eligibleFiles = new ArrayList<>();
    java.util.LinkedHashSet<String> skippedPaths = new java.util.LinkedHashSet<>();
    Map<String, Integer> reasonCounts = new java.util.LinkedHashMap<>();
    for (OpenlistApiService.OpenlistFile file : videoFiles) {
      String relativePath =
          TaskDirectoryStructureValidator.calculateRelativePath(
              taskConfig.getPath(), file.getPath());
      java.util.Optional<String> reason =
          TaskDirectoryStructureValidator.validate(relativePath, libraryType);
      if (reason.isEmpty()) {
        eligibleFiles.add(file);
        continue;
      }
      skippedPaths.add(file.getPath());
      reasonCounts.merge(reason.get(), 1, Integer::sum);
      if (skippedPaths.size() <= MAX_INVALID_STRUCTURE_LOGS) {
        log.warn("跳过目录结构不符合要求的视频: {}, 原因: {}", file.getPath(), reason.get());
      }
    }
    if (skippedPaths.size() > MAX_INVALID_STRUCTURE_LOGS) {
      log.warn(
          "目录结构异常视频较多，仅记录前 {} 个文件，另外 {} 个请在目录结构检查页面查看",
          MAX_INVALID_STRUCTURE_LOGS,
          skippedPaths.size() - MAX_INVALID_STRUCTURE_LOGS);
    }

    log.info(
        "目录结构执行过滤完成: 类型={}, 视频总数={}, 合规={}, 跳过={}, 原因统计={}",
        libraryType.value(),
        videoFiles.size(),
        eligibleFiles.size(),
        skippedPaths.size(),
        reasonCounts);
    return new StructureFilterResult(List.copyOf(eligibleFiles), Set.copyOf(skippedPaths));
  }

  record StructureFilterResult(
      List<OpenlistApiService.OpenlistFile> eligibleVideoFiles, Set<String> skippedVideoPaths) {}

  /** 移除文件扩展名 */
  private String removeExtension(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return fileName;
    }
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return fileName.substring(0, lastDotIndex);
    }
    return fileName;
  }

  /**
   * 获取OpenList配置
   *
   * @param taskConfig 任务配置
   * @return OpenList配置
   */
  private OpenlistConfig getOpenlistConfig(TaskConfig taskConfig) {
    if (taskConfig.getOpenlistConfigId() == null) {
      throw new BusinessException("任务配置中未指定OpenList配置ID");
    }

    OpenlistConfig openlistConfig = openlistConfigService.getById(taskConfig.getOpenlistConfigId());
    if (openlistConfig == null) {
      throw new BusinessException("OpenList配置不存在，ID: " + taskConfig.getOpenlistConfigId());
    }

    if (!Boolean.TRUE.equals(openlistConfig.getIsActive())) {
      throw new BusinessException("OpenList配置已禁用，ID: " + taskConfig.getOpenlistConfigId());
    }

    return openlistConfig;
  }

  /**
   * 构建包含sign参数的文件URL，并处理baseUrl替换
   *
   * @param originalUrl 原始文件URL
   * @param sign 签名参数
   * @return 包含sign参数的完整URL
   */
  private String buildFileUrlWithSign(String originalUrl, String sign) {
    if (originalUrl == null) {
      return null;
    }

    // 先处理URL，再添加sign参数
    String processedUrl = originalUrl;

    // 添加sign参数
    if (sign != null && !sign.trim().isEmpty()) {
      // 检查URL是否已经包含查询参数
      String separator = processedUrl.contains("?") ? "&" : "?";
      processedUrl = processedUrl + separator + "sign=" + sign;
    }

    return processedUrl;
  }

  /**
   * 处理URL的baseUrl替换 这个方法会在StrmFileService中调用，用于在生成STRM文件时替换baseUrl
   *
   * @param originalUrl 原始URL
   * @param openlistConfig OpenList配置
   * @return 处理后的URL
   */
  public String processUrlWithBaseUrlReplacement(
      String originalUrl, OpenlistConfig openlistConfig) {
    if (originalUrl == null || openlistConfig == null) {
      return originalUrl;
    }

    // 如果没有配置strmBaseUrl，直接返回原始URL
    if (openlistConfig.getStrmBaseUrl() == null
        || openlistConfig.getStrmBaseUrl().trim().isEmpty()) {
      log.debug("未配置strmBaseUrl，直接使用原始URL: {}", originalUrl);
      return originalUrl;
    }

    try {
      // 解析原始URL
      java.net.URL url = new java.net.URL(originalUrl);
      String originalBaseUrl =
          url.getProtocol()
              + "://"
              + url.getHost()
              + (url.getPort() != -1 && url.getPort() != 80 && url.getPort() != 443
                  ? ":" + url.getPort()
                  : "");

      // 获取路径和查询参数
      String path = url.getPath();
      String query = url.getQuery();
      String ref = url.getRef();

      // 构建新的URL
      String newBaseUrl = openlistConfig.getStrmBaseUrl();
      if (!newBaseUrl.endsWith("/")) {
        newBaseUrl += "/";
      }

      // 确保路径不以/开头（避免双斜杠）
      if (path.startsWith("/")) {
        path = path.substring(1);
      }

      String newUrl = newBaseUrl + path;

      // 添加查询参数
      if (query != null && !query.isEmpty()) {
        newUrl += "?" + query;
      }

      // 添加锚点
      if (ref != null && !ref.isEmpty()) {
        newUrl += "#" + ref;
      }

      log.info("URL替换: {} -> {}", originalUrl, newUrl);
      return newUrl;

    } catch (Exception e) {
      log.warn("URL替换失败，使用原始URL: {}, 错误: {}", originalUrl, e.getMessage());
      return originalUrl;
    }
  }

  /**
   * 构建刮削保存目录路径 复用 MediaScrapingService 中的逻辑
   *
   * @param strmDirectory STRM文件目录
   * @param relativePath 相对路径
   * @return 保存目录路径
   */
  private String buildScrapSaveDirectory(String strmDirectory, String relativePath) {
    if (relativePath == null || relativePath.trim().isEmpty()) {
      return strmDirectory;
    }

    // 移除文件名，只保留目录路径
    String directoryPath = relativePath;
    int lastSlashIndex = relativePath.lastIndexOf('/');
    if (lastSlashIndex > 0) {
      directoryPath = relativePath.substring(0, lastSlashIndex);
    } else if (lastSlashIndex == 0) {
      directoryPath = "";
    }

    if (directoryPath.isEmpty()) {
      return strmDirectory;
    }

    return strmDirectory + "/" + directoryPath;
  }

  /** 内存优化的文件处理方法 分批处理文件，避免一次性加载所有文件到内存 */
  private List<OpenlistApiService.OpenlistFile> processFilesWithMemoryOptimization(
      OpenlistConfig openlistConfig,
      TaskConfig taskConfig,
      boolean isIncrement,
      boolean needScrap) {

    List<OpenlistApiService.OpenlistFile> allFiles = new ArrayList<>();
    int processedCount = 0;
    int scrapSkippedCount = 0;

    try {
      // 分批处理目录，每次只处理一个目录的文件
      processDirectoryBatch(
          openlistConfig,
          taskConfig.getPath(),
          taskConfig,
          isIncrement,
          needScrap,
          allFiles,
          processedCount,
          scrapSkippedCount);

      log.info("文件处理完成 - 处理了 {} 个视频文件", processedCount);
      if (needScrap && scrapSkippedCount > 0) {
        log.info("跳过了 {} 个已刮削目录中的文件", scrapSkippedCount);
      }

    } catch (Exception e) {
      log.error("内存优化文件处理失败: {}", e.getMessage(), e);
      // 降级到原始方法
      log.info("降级使用原始文件处理方法");
      allFiles = openlistApiService.getAllFilesRecursively(openlistConfig, taskConfig.getPath());
    }

    return allFiles;
  }

  /** 分批处理目录 */
  private void processDirectoryBatch(
      OpenlistConfig openlistConfig,
      String path,
      TaskConfig taskConfig,
      boolean isIncrement,
      boolean needScrap,
      List<OpenlistApiService.OpenlistFile> allFiles,
      int processedCount,
      int scrapSkippedCount) {

    try {
      List<OpenlistApiService.OpenlistFile> files =
          openlistApiService.getDirectoryContents(openlistConfig, path);

      for (OpenlistApiService.OpenlistFile file : files) {
        allFiles.add(file);

        if ("file".equals(file.getType()) && strmFileService.isVideoFile(file.getName())) {
          // 立即处理视频文件，不累积在内存中
          processVideoFile(
              openlistConfig,
              file,
              taskConfig,
              isIncrement,
              needScrap,
              files,
              processedCount,
              scrapSkippedCount);
        } else if ("folder".equals(file.getType())) {
          // 递归处理子目录
          String subPath = file.getPath();
          if (subPath == null || subPath.isEmpty()) {
            subPath = path + "/" + file.getName();
          }
          processDirectoryBatch(
              openlistConfig,
              subPath,
              taskConfig,
              isIncrement,
              needScrap,
              allFiles,
              processedCount,
              scrapSkippedCount);
        }
      }

      // 处理完一个目录后，清理局部变量引用（由JVM自动管理GC）
      // 移除显式 System.gc() 调用以提升性能

    } catch (Exception e) {
      log.error("处理目录失败: {}, 错误: {}", path, e.getMessage(), e);
    }
  }

  /** 处理单个视频文件 */
  private void processVideoFile(
      OpenlistConfig openlistConfig,
      OpenlistApiService.OpenlistFile file,
      TaskConfig taskConfig,
      boolean isIncrement,
      boolean needScrap,
      List<OpenlistApiService.OpenlistFile> directoryFiles,
      int processedCount,
      int scrapSkippedCount) {

    try {
      // 计算相对路径
      String relativePath =
          strmFileService.calculateRelativePath(taskConfig.getPath(), file.getPath());

      // 构建包含sign参数的文件URL
      String fileUrlWithSign = buildFileUrlWithSign(file.getUrl(), file.getSign());

      // 生成STRM文件（增量模式下强制重新生成）
      strmFileService.generateStrmFile(
          taskConfig.getStrmPath(),
          relativePath,
          file.getName(),
          fileUrlWithSign,
          isIncrement, // 增量模式下强制重新生成
          taskConfig.getRenameRegex(),
          openlistConfig);

      // 如果启用了刮削功能，执行媒体刮削
      if (needScrap) {
        try {
          String saveDirectory = buildScrapSaveDirectory(taskConfig.getStrmPath(), relativePath);

          // 检查是否需要刮削（在增量模式下检查NFO文件是否已存在）
          boolean needScrapFile =
              needScrapFile(
                  file.getName(),
                  taskConfig.getRenameRegex(),
                  taskConfig.getStrmPath(),
                  relativePath,
                  isIncrement);

          if (needScrapFile) {
            if (isIncrement && mediaScrapingService.isDirectoryFullyScraped(saveDirectory)) {
              log.debug("目录已完全刮削，跳过: {}", saveDirectory);
              scrapSkippedCount++;
            } else {
              mediaScrapingService.scrapMedia(
                  openlistConfig,
                  file.getName(),
                  taskConfig.getStrmPath(),
                  relativePath,
                  directoryFiles,
                  file.getPath());
            }
          } else {
            log.debug("NFO文件已存在，跳过刮削: {}", file.getName());
            scrapSkippedCount++;
          }
        } catch (Exception scrapException) {
          log.error(
              "刮削文件失败: {}, 错误: {}", file.getName(), scrapException.getMessage(), scrapException);
        }
      }

      processedCount++;

    } catch (Exception e) {
      log.error("处理文件失败: {}, 错误: {}", file.getName(), e.getMessage(), e);
    }
  }

  /**
   * 判断是否需要刮削文件 在增量模式下，检查NFO文件是否存在，如果NFO文件已存在则跳过刮削
   *
   * @param fileName 原始文件名
   * @param renameRegex 重命名正则表达式
   * @param strmPath STRM文件路径
   * @param relativePath 相对路径
   * @param isIncrement 是否增量模式
   * @return 是否需要刮削
   */
  private boolean needScrapFile(
      String fileName,
      String renameRegex,
      String strmPath,
      String relativePath,
      boolean isIncrement) {
    // 非增量模式下总是需要刮削
    if (!isIncrement) {
      return true;
    }

    try {
      // 增量模式下，检查NFO文件是否已存在
      String finalFileName = processFileNameForScraping(fileName, renameRegex);
      java.nio.file.Path strmFilePath =
          strmFileService.buildStrmFilePath(strmPath, relativePath, finalFileName);

      // 构建对应的NFO文件路径
      java.nio.file.Path nfoFilePath =
          strmFilePath.resolveSibling(
              strmFilePath.getFileName().toString().replace(".strm", ".nfo"));

      // 如果NFO文件存在，则跳过刮削
      return !java.nio.file.Files.exists(nfoFilePath);
    } catch (Exception e) {
      log.warn("检查NFO文件是否存在时发生错误: {}, 默认进行刮削", e.getMessage());
      return true;
    }
  }

  /**
   * 处理文件名（重命名和添加.strm扩展名） 这个方法复制自StrmFileService，用于判断STRM文件是否存在
   *
   * @param originalFileName 原始文件名
   * @param renameRegex 重命名正则表达式
   * @return 处理后的文件名
   */
  private String processFileNameForScraping(String originalFileName, String renameRegex) {
    String processedName = originalFileName;

    // 应用重命名规则
    if (renameRegex != null && !renameRegex.trim().isEmpty()) {
      try {
        // 简单的正则替换，可以根据需要扩展
        // 格式: "原始模式|替换内容"
        if (renameRegex.contains("|")) {
          String[] parts = renameRegex.split("\\|", 2);
          String pattern = parts[0];
          String replacement = parts[1];
          processedName = processedName.replaceAll(pattern, replacement);
          log.debug("文件重命名: {} -> {}", originalFileName, processedName);
        }
      } catch (Exception e) {
        log.warn("重命名规则应用失败: {}, 使用原始文件名", renameRegex, e);
      }
    }

    // 移除原始扩展名并添加.strm扩展名
    int lastDotIndex = processedName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      processedName = processedName.substring(0, lastDotIndex);
    }
    processedName += ".strm";

    return processedName;
  }
}
