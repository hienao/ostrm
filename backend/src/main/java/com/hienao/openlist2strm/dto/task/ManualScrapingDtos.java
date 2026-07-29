package com.hienao.openlist2strm.dto.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

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
    @Builder.Default private List<DirectoryNode> children = new ArrayList<>();
  }

  @Data
  public static class PreviewRequest {
    @NotBlank(message = "目录路径不能为空") private String directoryPath;
  }

  @Data
  @Builder
  public static class Preview {
    private String directoryPath;
    private String mediaType;
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
    @Builder.Default private List<RenameItem> proposedFileRenames = new ArrayList<>();
    @Builder.Default private List<String> generatedFiles = new ArrayList<>();
    @Builder.Default private List<String> renamedGeneratedFiles = new ArrayList<>();
  }

  @Data
  @Builder
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
    private int renamedFileCount;
    @Builder.Default private List<String> uploadedFiles = new ArrayList<>();
    private String message;
  }
}
