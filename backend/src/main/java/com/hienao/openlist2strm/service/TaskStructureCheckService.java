package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.dto.task.TaskStructureCheckResult;
import com.hienao.openlist2strm.dto.task.TaskStructureCheckResult.StructureNode;
import com.hienao.openlist2strm.entity.MediaLibraryType;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.util.TaskDirectoryStructureValidator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 按需扫描 OpenList 任务目录，并构建仅包含异常视频文件的目录树。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskStructureCheckService {

  private final TaskConfigService taskConfigService;
  private final OpenlistConfigService openlistConfigService;
  private final OpenlistApiService openlistApiService;
  private final StrmFileService strmFileService;

  public TaskStructureCheckResult check(Long taskId) {
    TaskConfig task = taskConfigService.getById(taskId);
    if (task == null) {
      throw new BusinessException("任务不存在，ID: " + taskId);
    }

    MediaLibraryType libraryType = MediaLibraryType.from(task.getLibraryType());
    String expectedStructure = expectedStructure(libraryType);
    MutableNode root = MutableNode.folder(rootName(task), task.getPath());

    if (libraryType == MediaLibraryType.AUTO) {
      return buildResult(
          task,
          libraryType,
          expectedStructure,
          false,
          0,
          0,
          0,
          "自动识别任务没有固定目录规范，请先把媒体库类型改为电影、电视剧或动画",
          root);
    }

    OpenlistConfig openlistConfig = openlistConfigService.getById(task.getOpenlistConfigId());
    if (openlistConfig == null) {
      throw new BusinessException("任务关联的 OpenList 配置不存在");
    }

    log.info("开始检查任务目录结构: taskId={}, path={}", taskId, task.getPath());
    List<OpenlistApiService.OpenlistFile> entries =
        openlistApiService.getAllFilesRecursively(openlistConfig, task.getPath());
    List<OpenlistApiService.OpenlistFile> videoFiles =
        entries.stream()
            .filter(file -> "file".equals(file.getType()))
            .filter(file -> strmFileService.isVideoFile(file.getName()))
            .toList();

    int invalidCount = 0;
    for (OpenlistApiService.OpenlistFile file : videoFiles) {
      String relativePath = calculateRelativePath(task.getPath(), file.getPath());
      Optional<String> reason = TaskDirectoryStructureValidator.validate(relativePath, libraryType);
      if (reason.isPresent()) {
        invalidCount++;
        addInvalidFile(root, relativePath, reason.get());
      }
    }

    String message =
        invalidCount == 0 ? "目录结构检查通过，未发现异常视频文件" : "发现 " + invalidCount + " 个目录层级不符合要求的视频文件";
    log.info(
        "任务目录结构检查完成: taskId={}, entries={}, videos={}, invalid={}",
        taskId,
        entries.size(),
        videoFiles.size(),
        invalidCount);
    return buildResult(
        task,
        libraryType,
        expectedStructure,
        true,
        entries.size(),
        videoFiles.size(),
        invalidCount,
        message,
        root);
  }

  private TaskStructureCheckResult buildResult(
      TaskConfig task,
      MediaLibraryType libraryType,
      String expectedStructure,
      boolean supported,
      int scannedEntryCount,
      int videoFileCount,
      int invalidFileCount,
      String message,
      MutableNode root) {
    return TaskStructureCheckResult.builder()
        .taskId(task.getId())
        .taskName(task.getTaskName())
        .libraryType(libraryType.value())
        .rootPath(task.getPath())
        .expectedStructure(expectedStructure)
        .supported(supported)
        .scannedEntryCount(scannedEntryCount)
        .videoFileCount(videoFileCount)
        .invalidFileCount(invalidFileCount)
        .message(message)
        .tree(toDto(root))
        .build();
  }

  private String calculateRelativePath(String taskPath, String filePath) {
    if (taskPath == null || filePath == null) {
      return "";
    }
    String normalizedRoot = normalizePath(taskPath).replaceAll("/+$", "");
    String normalizedFile = normalizePath(filePath);
    String prefix = normalizedRoot + "/";
    return normalizedFile.startsWith(prefix) ? normalizedFile.substring(prefix.length()) : "";
  }

  private String normalizePath(String path) {
    String normalized = path.replace('\\', '/').replaceAll("/+", "/");
    return normalized.startsWith("/") ? normalized : "/" + normalized;
  }

  private void addInvalidFile(MutableNode root, String relativePath, String reason) {
    List<String> segments = TaskDirectoryStructureValidator.splitPath(relativePath);
    if (segments.isEmpty()) {
      segments = List.of("无法定位的文件");
    }

    MutableNode current = root;
    StringBuilder currentPath = new StringBuilder();
    for (int i = 0; i < segments.size(); i++) {
      String segment = segments.get(i);
      if (!currentPath.isEmpty()) {
        currentPath.append('/');
      }
      currentPath.append(segment);
      boolean file = i == segments.size() - 1;
      String key = (file ? "file:" : "folder:") + segment;
      MutableNode child =
          current.children.computeIfAbsent(
              key,
              ignored ->
                  file
                      ? MutableNode.file(segment, currentPath.toString(), reason)
                      : MutableNode.folder(segment, currentPath.toString()));
      current = child;
    }
  }

  private StructureNode toDto(MutableNode node) {
    List<StructureNode> children =
        node.children.values().stream()
            .sorted(
                Comparator.comparing((MutableNode child) -> "file".equals(child.type))
                    .thenComparing(child -> child.name, String.CASE_INSENSITIVE_ORDER))
            .map(this::toDto)
            .toList();
    return StructureNode.builder()
        .name(node.name)
        .path(node.path)
        .type(node.type)
        .reason(node.reason)
        .children(new ArrayList<>(children))
        .build();
  }

  private String rootName(TaskConfig task) {
    List<String> segments = TaskDirectoryStructureValidator.splitPath(task.getPath());
    return segments.isEmpty() ? task.getTaskName() : segments.get(segments.size() - 1);
  }

  private String expectedStructure(MediaLibraryType libraryType) {
    return switch (libraryType) {
      case MOVIE -> "电影名 (年份)/视频文件";
      case TV -> "剧名/Season 01/S01E01.ext";
      case ANIME -> "动画名/Season 01/01.ext，或动画名/[01].ext";
      case AUTO -> "自动识别没有固定目录结构";
    };
  }

  private static final class MutableNode {

    private final String name;
    private final String path;
    private final String type;
    private final String reason;
    private final Map<String, MutableNode> children = new LinkedHashMap<>();

    private MutableNode(String name, String path, String type, String reason) {
      this.name = name;
      this.path = path;
      this.type = type;
      this.reason = reason;
    }

    private static MutableNode folder(String name, String path) {
      return new MutableNode(name, path, "folder", null);
    }

    private static MutableNode file(String name, String path, String reason) {
      return new MutableNode(name, path, "file", reason);
    }
  }
}
