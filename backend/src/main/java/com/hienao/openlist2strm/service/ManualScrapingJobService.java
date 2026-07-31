package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteRequest;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteResult;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.JobView;
import com.hienao.openlist2strm.entity.ManualScrapingJob;
import com.hienao.openlist2strm.entity.ManualScrapingJobStage;
import com.hienao.openlist2strm.entity.ManualScrapingJobStatus;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.mapper.ManualScrapingJobMapper;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** 提交、持久化并恢复手动刮削异步作业。 */
@Slf4j
@Service
public class ManualScrapingJobService {

  private final ManualScrapingJobMapper jobMapper;
  private final ManualScrapingService manualScrapingService;
  private final TaskConfigService taskConfigService;
  private final ObjectMapper objectMapper;
  private final Executor executor;

  public ManualScrapingJobService(
      ManualScrapingJobMapper jobMapper,
      ManualScrapingService manualScrapingService,
      TaskConfigService taskConfigService,
      ObjectMapper objectMapper,
      @Qualifier("manualScrapingExecutor") Executor executor) {
    this.jobMapper = jobMapper;
    this.manualScrapingService = manualScrapingService;
    this.taskConfigService = taskConfigService;
    this.objectMapper = objectMapper;
    this.executor = executor;
  }

  @PostConstruct
  public void recoverInterruptedJobs() {
    int count = jobMapper.markInterruptedJobsFailed();
    if (count > 0) {
      log.warn("检测到 {} 个因应用重启中断的手动刮削作业，已标记为可重试", count);
    }
  }

  public JobView submit(Long taskId, ExecuteRequest request) {
    if (taskConfigService.getById(taskId) == null) {
      throw new BusinessException("任务不存在，ID: " + taskId);
    }
    ManualScrapingJob active = jobMapper.selectActiveByTaskId(taskId);
    if (active != null) {
      throw activeJobException(active);
    }

    ManualScrapingJob job =
        new ManualScrapingJob()
            .setTaskId(taskId)
            .setDirectoryPath(request.getDirectoryPath())
            .setMediaType(request.getMediaType())
            .setTmdbId(request.getTmdbId())
            .setRenameMedia(request.isRenameMedia())
            .setStatus(ManualScrapingJobStatus.PENDING.name())
            .setStage(ManualScrapingJobStage.PREPARING.name())
            .setProgress(0)
            .setMessage("等待执行")
            .setRenameOperationIndex(0)
            .setRenamedDirectoryCount(0)
            .setRenamedFileCount(0)
            .setUploadedFiles("[]");
    try {
      jobMapper.insert(job);
    } catch (DataIntegrityViolationException e) {
      ManualScrapingJob concurrent = jobMapper.selectActiveByTaskId(taskId);
      throw concurrent == null
          ? new BusinessException("该任务已有手动刮削作业正在执行")
          : activeJobException(concurrent);
    }
    schedule(job.getId());
    return toView(jobMapper.selectById(job.getId()));
  }

  public JobView retry(Long taskId, Long jobId) {
    ManualScrapingJob job = requireJob(taskId, jobId);
    if (job.statusValue() != ManualScrapingJobStatus.FAILED) {
      throw new BusinessException("只有失败的手动刮削作业可以重试");
    }
    ManualScrapingJob active = jobMapper.selectActiveByTaskId(taskId);
    if (active != null) {
      throw activeJobException(active);
    }
    try {
      if (jobMapper.prepareRetry(jobId) != 1) {
        throw new BusinessException("作业状态已变化，请刷新后重试");
      }
    } catch (DataIntegrityViolationException e) {
      throw new BusinessException("该任务已有手动刮削作业正在执行");
    }
    schedule(jobId);
    return toView(jobMapper.selectById(jobId));
  }

  public JobView get(Long taskId, Long jobId) {
    return toView(requireJob(taskId, jobId));
  }

  public JobView getLatest(Long taskId) {
    ManualScrapingJob job = jobMapper.selectLatestByTaskId(taskId);
    return job == null ? null : toView(job);
  }

  public void assertNoActiveJob(Long taskId) {
    ManualScrapingJob active = jobMapper.selectActiveByTaskId(taskId);
    if (active != null) {
      throw activeJobException(active);
    }
  }

  private void schedule(Long jobId) {
    try {
      executor.execute(() -> run(jobId));
    } catch (RejectedExecutionException e) {
      fail(jobId, "手动刮削执行队列已满，请稍后重试", e);
      throw new BusinessException("手动刮削执行队列已满，请稍后重试");
    }
  }

  private void run(Long jobId) {
    if (jobMapper.markRunningIfPending(jobId) != 1) {
      return;
    }
    ManualScrapingJob job = jobMapper.selectById(jobId);
    try {
      ExecuteResult result =
          manualScrapingService.executeJob(
              job,
              (stage,
                  progress,
                  message,
                  finalPath,
                  renamedDirectoryCount,
                  renamedFileCount,
                  renamePlan,
                  renameOperationIndex) ->
                  checkpoint(
                      jobId,
                      stage,
                      progress,
                      message,
                      finalPath,
                      renamedDirectoryCount,
                      renamedFileCount,
                      renamePlan,
                      renameOperationIndex));
      ManualScrapingJob completed = jobMapper.selectById(jobId);
      completed
          .setStatus(ManualScrapingJobStatus.SUCCEEDED.name())
          .setStage(ManualScrapingJobStage.COMPLETED.name())
          .setProgress(100)
          .setMessage(result.getMessage())
          .setErrorMessage(null)
          .setFinalDirectoryPath(result.getFinalDirectoryPath())
          .setRenamedDirectoryCount(result.getRenamedDirectoryCount())
          .setRenamedFileCount(result.getRenamedFileCount())
          .setUploadedFiles(writeFiles(result.getUploadedFiles()))
          .setCompletedAt(LocalDateTime.now());
      jobMapper.updateCheckpoint(completed);
      log.info("手动刮削作业完成 - 作业ID: {}, 任务ID: {}", jobId, job.getTaskId());
    } catch (Exception e) {
      fail(jobId, rootMessage(e), e);
    }
  }

  private void checkpoint(
      Long jobId,
      ManualScrapingJobStage stage,
      int progress,
      String message,
      String finalPath,
      int renamedDirectoryCount,
      int renamedFileCount,
      String renamePlan,
      int renameOperationIndex) {
    ManualScrapingJob job = jobMapper.selectById(jobId);
    if (job == null || job.statusValue() != ManualScrapingJobStatus.RUNNING) {
      throw new BusinessException("手动刮削作业状态异常");
    }
    job.setStage(stage.name())
        .setProgress(Math.max(0, Math.min(100, progress)))
        .setMessage(message)
        .setFinalDirectoryPath(finalPath)
        .setRenamedDirectoryCount(renamedDirectoryCount)
        .setRenamedFileCount(renamedFileCount)
        .setRenamePlan(renamePlan)
        .setRenameOperationIndex(renameOperationIndex);
    jobMapper.updateCheckpoint(job);
  }

  private void fail(Long jobId, String message, Exception error) {
    ManualScrapingJob job = jobMapper.selectById(jobId);
    if (job == null) {
      return;
    }
    job.setStatus(ManualScrapingJobStatus.FAILED.name())
        .setMessage("手动刮削失败，可从当前阶段重试")
        .setErrorMessage(message)
        .setCompletedAt(LocalDateTime.now());
    jobMapper.updateCheckpoint(job);
    log.error("手动刮削作业失败 - 作业ID: {}, 阶段: {}", jobId, job.getStage(), error);
  }

  private ManualScrapingJob requireJob(Long taskId, Long jobId) {
    ManualScrapingJob job = jobMapper.selectById(jobId);
    if (job == null || !taskId.equals(job.getTaskId())) {
      throw new BusinessException("手动刮削作业不存在");
    }
    return job;
  }

  private BusinessException activeJobException(ManualScrapingJob active) {
    return new BusinessException(
        "该任务已有手动刮削作业正在执行，作业ID: " + active.getId() + "，当前阶段: " + active.getStage());
  }

  private JobView toView(ManualScrapingJob job) {
    return JobView.builder()
        .id(job.getId())
        .taskId(job.getTaskId())
        .directoryPath(job.getDirectoryPath())
        .finalDirectoryPath(job.getFinalDirectoryPath())
        .mediaType(job.getMediaType())
        .tmdbId(job.getTmdbId())
        .renameMedia(Boolean.TRUE.equals(job.getRenameMedia()))
        .status(job.getStatus())
        .stage(job.getStage())
        .progress(job.getProgress() == null ? 0 : job.getProgress())
        .message(job.getMessage())
        .errorMessage(job.getErrorMessage())
        .renamedDirectoryCount(
            job.getRenamedDirectoryCount() == null ? 0 : job.getRenamedDirectoryCount())
        .renamedFileCount(job.getRenamedFileCount() == null ? 0 : job.getRenamedFileCount())
        .uploadedFiles(readFiles(job.getUploadedFiles()))
        .createdAt(job.getCreatedAt())
        .startedAt(job.getStartedAt())
        .completedAt(job.getCompletedAt())
        .updatedAt(job.getUpdatedAt())
        .build();
  }

  private String writeFiles(List<String> files) {
    try {
      return objectMapper.writeValueAsString(files);
    } catch (JsonProcessingException e) {
      throw new BusinessException("保存上传文件列表失败", e);
    }
  }

  private List<String> readFiles(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(value, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      log.warn("读取手动刮削上传文件列表失败: {}", value, e);
      return List.of();
    }
  }

  private String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null) {
      current = current.getCause();
    }
    return current.getMessage() == null ? error.getClass().getSimpleName() : current.getMessage();
  }
}
