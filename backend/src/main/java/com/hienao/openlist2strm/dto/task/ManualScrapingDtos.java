package com.hienao.openlist2strm.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 手动刮削流程使用的请求与响应对象。 */
public final class ManualScrapingDtos {

  private ManualScrapingDtos() {}

  @Data
  @Builder
  public static class DirectoryTree {
    private Long taskId;
    private String taskName;
    private String libraryType;
    private String rootPath;
    private DirectoryNode tree;
  }

  @Data
  @Builder
  public static class DirectoryNode {
    private String name;
    private String path;
    private int videoFileCount;
    private boolean childrenLoaded;
    @Builder.Default private List<DirectoryNode> children = new ArrayList<>();
  }

  @Data
  public static class PreviewRequest {
    @NotBlank(message = "目录路径不能为空") private String directoryPath;

    /** 用户手动修正的搜索标题；为空时使用自动识别结果。 */
    private String title;

    /** 用户手动修正的发行年份。 */
    private String year;

    /** 用户指定后直接读取详情，不再执行标题搜索。 */
    @Positive(message = "TMDB ID必须大于0") private Integer tmdbId;
  }

  @Data
  @Builder
  public static class Preview {
    private String directoryPath;
    private String mediaType;
    private boolean matched;
    private String searchTitle;
    private String searchYear;
    private String matchMessage;
    private Integer tmdbId;
    private String title;
    private String originalTitle;
    private String year;
    private String overview;
    private Double voteAverage;
    private String posterUrl;
    private String backdropUrl;
    private int videoFileCount;
    private String proposedDirectoryName;
    @Builder.Default private List<RenameItem> proposedDirectoryRenames = new ArrayList<>();
    @Builder.Default private List<RenameItem> proposedFileRenames = new ArrayList<>();
    @Builder.Default private List<String> generatedFiles = new ArrayList<>();
    @Builder.Default private List<String> renamedGeneratedFiles = new ArrayList<>();
  }

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class RenameItem {
    private String sourcePath;
    private String sourceName;
    private String targetName;
  }

  @Data
  public static class ExecuteRequest {
    @NotBlank(message = "目录路径不能为空") private String directoryPath;

    @NotBlank(message = "媒体类型不能为空") private String mediaType;

    @NotNull(message = "TMDB ID不能为空") private Integer tmdbId;

    private boolean renameMedia;
  }

  @Data
  @Builder
  public static class ExecuteResult {
    private String finalDirectoryPath;
    private int renamedDirectoryCount;
    private int renamedFileCount;
    @Builder.Default private List<String> uploadedFiles = new ArrayList<>();
    private String message;
  }

  @Data
  @Builder
  public static class JobView {
    private Long id;
    private Long taskId;
    private String directoryPath;
    private String finalDirectoryPath;
    private String mediaType;
    private Integer tmdbId;
    private boolean renameMedia;
    private String status;
    private String stage;
    private int progress;
    private String message;
    private String errorMessage;
    private int renamedDirectoryCount;
    private int renamedFileCount;
    @Builder.Default private List<String> uploadedFiles = new ArrayList<>();
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime updatedAt;
  }
}
