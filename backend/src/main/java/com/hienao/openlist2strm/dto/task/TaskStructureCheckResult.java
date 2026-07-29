package com.hienao.openlist2strm.dto.task;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 任务目录结构检查结果。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStructureCheckResult {

  private Long taskId;
  private String taskName;
  private String libraryType;
  private String rootPath;
  private String expectedStructure;
  private boolean supported;
  private int scannedEntryCount;
  private int videoFileCount;
  private int invalidFileCount;
  private String message;
  private StructureNode tree;

  /** 仅包含异常文件及其父目录的文件树节点。 */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class StructureNode {

    private String name;
    private String path;
    private String type;
    private String reason;

    @Builder.Default private List<StructureNode> children = new ArrayList<>();
  }
}
