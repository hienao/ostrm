package com.hienao.openlist2strm.notification;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 渠道无关的任务终态通知事件。 */
@Data
@Builder
public class NotificationEvent {

  public enum Kind {
    TASK,
    MANUAL_SCRAPING
  }

  public enum Status {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE
  }

  public enum Trigger {
    MANUAL,
    SCHEDULED
  }

  private Kind kind;
  private Status status;
  private Trigger trigger;
  private Long taskId;
  private String taskName;
  private Long jobId;
  private String executionMode;
  private String libraryType;
  private String mediaType;
  private Integer tmdbId;
  private String sourcePath;
  private String strmPath;
  private String finalPath;
  private String failedStage;
  private String errorMessage;
  private boolean retryable;
  private int discoveredVideos;
  private int selectedVideos;
  private int strmSucceeded;
  private int processingFailed;
  private int incrementalSkipped;
  private int structureSkipped;
  private int cleanedStrm;
  private int renamedDirectories;
  private int renamedFiles;
  private String mediaServerName;
  private String mediaServerType;
  private String mediaRefreshScope;
  private String mediaLibraryName;
  private String mediaRefreshStatus;
  private String mediaRefreshMessage;
  @Builder.Default private List<String> uploadedFiles = new ArrayList<>();
  @Builder.Default private List<NotificationIssue> issues = new ArrayList<>();
  private long durationMillis;
  private LocalDateTime completedAt;
}
