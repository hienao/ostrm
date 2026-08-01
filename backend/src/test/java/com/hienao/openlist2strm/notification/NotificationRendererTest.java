package com.hienao.openlist2strm.notification;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationRendererTest {

  private final NotificationRenderer renderer = new NotificationRenderer();

  @Test
  void groupsUnrecognizedAndRenameFailuresInPartialNotification() {
    NotificationEvent event =
        NotificationEvent.builder()
            .kind(NotificationEvent.Kind.TASK)
            .status(NotificationEvent.Status.PARTIAL_SUCCESS)
            .trigger(NotificationEvent.Trigger.SCHEDULED)
            .taskId(12L)
            .taskName("电视剧增量同步")
            .executionMode("增量")
            .libraryType("tv")
            .sourcePath("/电视剧")
            .strmPath("/app/backend/strm/tv")
            .discoveredVideos(128)
            .selectedVideos(12)
            .strmSucceeded(12)
            .issues(
                List.of(
                    issue(
                        NotificationIssue.Category.SCRAPE_UNRECOGNIZED,
                        "LOW_CONFIDENCE",
                        "/电视剧/Unknown/Season 01/raw.mkv",
                        null,
                        "置信度 40%"),
                    issue(
                        NotificationIssue.Category.DIRECTORY_RENAME_FAILED,
                        null,
                        "/电视剧/Show/S1",
                        "Season 01",
                        "目标目录已经存在"),
                    issue(
                        NotificationIssue.Category.FILE_RENAME_FAILED,
                        null,
                        "/电视剧/Show/Season 01/raw.S01E01.mkv",
                        "Show - S01E01.mkv",
                        "目标文件已经存在")))
            .durationMillis(88_000)
            .completedAt(LocalDateTime.of(2026, 8, 1, 20, 15, 30))
            .build();

    RenderedNotification result = renderer.render(event, true, 5);

    assertTrue(result.title().contains("任务部分完成"));
    assertTrue(result.body().contains("刮削未识别（1）"));
    assertTrue(result.body().contains("目录重命名失败（1）"));
    assertTrue(result.body().contains("文件重命名失败（1）"));
    assertTrue(result.body().contains("[识别置信度过低] /电视剧/Unknown/Season 01/raw.mkv"));
    assertTrue(result.body().contains("/电视剧/Show/S1 → Season 01"));
    assertTrue(result.body().contains("耗时：1分28秒"));
    assertTrue("warning".equals(result.type()));
  }

  @Test
  void hidesParentDirectoriesWhenFullPathsAreDisabled() {
    NotificationEvent event =
        NotificationEvent.builder()
            .kind(NotificationEvent.Kind.MANUAL_SCRAPING)
            .status(NotificationEvent.Status.FAILURE)
            .taskId(12L)
            .taskName("电影")
            .jobId(156L)
            .mediaType("movie")
            .sourcePath("/电影/后室")
            .failedStage("UPLOADING")
            .errorMessage("写入失败: /电影/后室/old.mkv")
            .issues(
                List.of(
                    issue(
                        NotificationIssue.Category.FILE_RENAME_FAILED,
                        null,
                        "/电影/后室/old.mkv",
                        "new.mkv",
                        "重命名目标已存在: /电影/后室/new.mkv")))
            .completedAt(LocalDateTime.now())
            .build();

    RenderedNotification result = renderer.render(event, false, 5);

    assertTrue(result.body().contains("原目录：后室"));
    assertFalse(result.body().contains("/电影/后室"));
    assertTrue(result.body().contains("old.mkv → new.mkv"));
  }

  private NotificationIssue issue(
      NotificationIssue.Category category,
      String reasonCode,
      String sourcePath,
      String targetName,
      String reason) {
    return NotificationIssue.builder()
        .category(category)
        .reasonCode(reasonCode)
        .sourcePath(sourcePath)
        .targetName(targetName)
        .reason(reason)
        .build();
  }
}
