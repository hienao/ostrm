package com.hienao.openlist2strm.notification;

import lombok.Builder;

/** 可在通知中分组展示的执行异常。 */
@Builder
public record NotificationIssue(
    Category category,
    String reasonCode,
    String scope,
    String sourcePath,
    String targetName,
    String mediaTitle,
    Integer tmdbId,
    String reason) {

  public enum Category {
    SCRAPE_UNRECOGNIZED,
    STRUCTURE_INVALID_SKIPPED,
    DIRECTORY_RENAME_FAILED,
    FILE_RENAME_FAILED,
    PROCESSING_FAILED,
    MEDIA_SERVER_REFRESH_FAILED
  }

  /** 用于同一任务内合并自动重命名和正式刮削产生的重复异常。 */
  public String deduplicationKey() {
    return category + "|" + String.valueOf(sourcePath) + "|" + String.valueOf(targetName);
  }
}
