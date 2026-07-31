package com.hienao.openlist2strm.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** 持久化的手动刮削异步作业。 */
@Data
@Accessors(chain = true)
public class ManualScrapingJob {
  private Long id;
  private Long taskId;
  private String directoryPath;
  private String finalDirectoryPath;
  private String mediaType;
  private Integer tmdbId;
  private Boolean renameMedia;
  private String status;
  private String stage;
  private Integer progress;
  private String message;
  private String errorMessage;
  private String renamePlan;
  private Integer renameOperationIndex;
  private Integer renamedDirectoryCount;
  private Integer renamedFileCount;
  private String uploadedFiles;
  private LocalDateTime createdAt;
  private LocalDateTime startedAt;
  private LocalDateTime completedAt;
  private LocalDateTime updatedAt;

  public ManualScrapingJobStatus statusValue() {
    return ManualScrapingJobStatus.valueOf(status);
  }

  public ManualScrapingJobStage stageValue() {
    return ManualScrapingJobStage.valueOf(stage);
  }
}
