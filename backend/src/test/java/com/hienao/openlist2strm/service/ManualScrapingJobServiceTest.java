package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteRequest;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteResult;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.JobView;
import com.hienao.openlist2strm.entity.ManualScrapingJob;
import com.hienao.openlist2strm.entity.ManualScrapingJobStage;
import com.hienao.openlist2strm.entity.ManualScrapingJobStatus;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.mapper.ManualScrapingJobMapper;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualScrapingJobServiceTest {

  private ManualScrapingJobMapper jobMapper;
  private ManualScrapingService manualScrapingService;
  private TaskConfigService taskConfigService;
  private NotificationService notificationService;
  private Executor executor;
  private ManualScrapingJobService service;

  @BeforeEach
  void setUp() {
    jobMapper = mock(ManualScrapingJobMapper.class);
    manualScrapingService = mock(ManualScrapingService.class);
    taskConfigService = mock(TaskConfigService.class);
    notificationService = mock(NotificationService.class);
    executor = mock(Executor.class);
    service =
        new ManualScrapingJobService(
            jobMapper,
            manualScrapingService,
            taskConfigService,
            new ObjectMapper(),
            notificationService,
            executor);
  }

  @Test
  void rejectsSecondJobWhileSameTaskIsActive() {
    ManualScrapingJob active =
        job(21L, ManualScrapingJobStatus.RUNNING, ManualScrapingJobStage.RENAMING);
    when(taskConfigService.getById(7L)).thenReturn(new TaskConfig().setId(7L));
    when(jobMapper.selectActiveByTaskId(7L)).thenReturn(active);

    BusinessException error =
        assertThrows(BusinessException.class, () -> service.submit(7L, request()));

    assertEquals(true, error.getMessage().contains("作业ID: 21"));
    verify(jobMapper, never()).insert(any());
    verify(executor, never()).execute(any());
  }

  @Test
  void submitPersistsPendingJobBeforeSchedulingIt() {
    when(taskConfigService.getById(7L)).thenReturn(new TaskConfig().setId(7L));
    when(jobMapper.insert(any()))
        .thenAnswer(
            invocation -> {
              ManualScrapingJob inserted = invocation.getArgument(0);
              inserted.setId(22L);
              return 1;
            });
    when(jobMapper.selectById(22L))
        .thenAnswer(
            invocation ->
                job(22L, ManualScrapingJobStatus.PENDING, ManualScrapingJobStage.PREPARING));

    JobView result = service.submit(7L, request());

    assertEquals(22L, result.getId());
    assertEquals("PENDING", result.getStatus());
    verify(jobMapper).insert(any());
    verify(executor).execute(any());
  }

  @Test
  void retryKeepsCheckpointAndSchedulesSameJob() {
    ManualScrapingJob failed =
        job(23L, ManualScrapingJobStatus.FAILED, ManualScrapingJobStage.UPLOADING)
            .setFinalDirectoryPath("/movies/Film (2026) {tmdbid-123}")
            .setRenamedFileCount(1);
    when(jobMapper.selectById(23L)).thenReturn(failed);
    when(jobMapper.prepareRetry(23L))
        .thenAnswer(
            invocation -> {
              failed.setStatus(ManualScrapingJobStatus.PENDING.name());
              return 1;
            });

    JobView result = service.retry(7L, 23L);

    assertEquals("PENDING", result.getStatus());
    assertEquals("UPLOADING", result.getStage());
    assertEquals("/movies/Film (2026) {tmdbid-123}", result.getFinalDirectoryPath());
    verify(executor).execute(any());
  }

  @Test
  void notificationSubmissionFailureDoesNotChangeSuccessfulJobResult() {
    AtomicReference<ManualScrapingJob> persisted = new AtomicReference<>();
    when(taskConfigService.getById(7L)).thenReturn(new TaskConfig().setId(7L).setTaskName("电影任务"));
    when(jobMapper.insert(any()))
        .thenAnswer(
            invocation -> {
              ManualScrapingJob inserted = invocation.getArgument(0);
              inserted.setId(24L);
              persisted.set(inserted);
              return 1;
            });
    when(jobMapper.markRunningIfPending(24L)).thenReturn(1);
    when(jobMapper.selectById(24L)).thenAnswer(invocation -> persisted.get());
    when(manualScrapingService.executeJob(any(), any()))
        .thenReturn(
            ExecuteResult.builder()
                .finalDirectoryPath("/movies/Film (2026) {tmdbid-123}")
                .renamedDirectoryCount(1)
                .renamedFileCount(1)
                .uploadedFiles(java.util.List.of("movie.nfo"))
                .message("手动刮削完成")
                .build());
    doThrow(new RuntimeException("通知队列不可用")).when(notificationService).notifyAsync(any());
    org.mockito.Mockito.doAnswer(
            invocation -> {
              Runnable command = invocation.getArgument(0);
              command.run();
              return null;
            })
        .when(executor)
        .execute(any());

    JobView result = service.submit(7L, request());

    assertEquals("SUCCEEDED", result.getStatus());
    assertEquals(100, result.getProgress());
    assertEquals("/movies/Film (2026) {tmdbid-123}", result.getFinalDirectoryPath());
  }

  private ExecuteRequest request() {
    ExecuteRequest request = new ExecuteRequest();
    request.setDirectoryPath("/movies/Film");
    request.setMediaType("movie");
    request.setTmdbId(123);
    request.setRenameMedia(true);
    return request;
  }

  private ManualScrapingJob job(
      Long id, ManualScrapingJobStatus status, ManualScrapingJobStage stage) {
    return new ManualScrapingJob()
        .setId(id)
        .setTaskId(7L)
        .setDirectoryPath("/movies/Film")
        .setMediaType("movie")
        .setTmdbId(123)
        .setRenameMedia(true)
        .setStatus(status.name())
        .setStage(stage.name())
        .setProgress(20)
        .setMessage("处理中")
        .setRenamedFileCount(0)
        .setUploadedFiles("[]");
  }
}
