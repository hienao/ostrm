package com.hienao.openlist2strm.dto.task;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 目录结构检查首页，只包含任务根目录的直接子项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStructureCheckOverview {

  private Long taskId;
  private String taskName;
  private String libraryType;
  private String rootPath;
  private String expectedStructure;
  private boolean supported;
  private String message;
  private TaskStructureCheckResult rootFilesResult;

  @Builder.Default private List<DirectoryItem> directories = new ArrayList<>();

  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class DirectoryItem {
    private String name;
    private String path;
  }
}
