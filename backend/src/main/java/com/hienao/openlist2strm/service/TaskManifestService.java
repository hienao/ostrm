package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.config.PathConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 持久化每个任务最后一次完整扫描的紧凑清单，用于增量识别发生变化的目录。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskManifestService {

  private final ObjectMapper objectMapper;
  private final PathConfiguration pathConfiguration;

  public ChangeSet detectChanges(
      Long taskId,
      String taskPath,
      String configurationFingerprint,
      List<OpenlistApiService.OpenlistFile> currentFiles) {
    Manifest previous = load(taskId);
    if (previous == null
        || !Objects.equals(previous.taskPath(), taskPath)
        || !Objects.equals(previous.configurationFingerprint(), configurationFingerprint)) {
      return new ChangeSet(true, allDirectories(currentFiles));
    }

    Map<String, String> currentEntries = entries(currentFiles);
    Set<String> changedDirectories = new HashSet<>();
    Set<String> paths = new HashSet<>(previous.entries().keySet());
    paths.addAll(currentEntries.keySet());
    for (String path : paths) {
      if (!Objects.equals(previous.entries().get(path), currentEntries.get(path))) {
        changedDirectories.add(parentDirectory(path));
      }
    }
    return new ChangeSet(false, changedDirectories);
  }

  public void save(
      Long taskId,
      String taskPath,
      String configurationFingerprint,
      List<OpenlistApiService.OpenlistFile> files) {
    Path target = manifestPath(taskId);
    Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
    try {
      Files.createDirectories(target.getParent());
      Manifest manifest =
          new Manifest(
              taskPath, configurationFingerprint, Instant.now().toString(), entries(files));
      objectMapper.writeValue(temporary.toFile(), manifest);
      try {
        Files.move(
            temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (java.nio.file.AtomicMoveNotSupportedException e) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } catch (Exception e) {
      log.warn("保存任务增量清单失败，任务ID: {}, 错误: {}", taskId, e.getMessage());
      try {
        Files.deleteIfExists(temporary);
      } catch (Exception cleanupError) {
        log.debug("清理任务清单临时文件失败: {}", temporary, cleanupError);
      }
    }
  }

  private Manifest load(Long taskId) {
    Path path = manifestPath(taskId);
    if (!Files.isRegularFile(path)) {
      return null;
    }
    try {
      return objectMapper.readValue(path.toFile(), new TypeReference<>() {});
    } catch (Exception e) {
      log.warn("读取任务增量清单失败，将执行完整处理，任务ID: {}", taskId, e);
      return null;
    }
  }

  private Path manifestPath(Long taskId) {
    return Path.of(pathConfiguration.getData(), "task-manifests", "task-" + taskId + ".json");
  }

  private Map<String, String> entries(List<OpenlistApiService.OpenlistFile> files) {
    Map<String, String> result = new HashMap<>(Math.max(16, files.size() * 2));
    for (OpenlistApiService.OpenlistFile file : files) {
      result.put(
          file.getPath(),
          String.join(
              "|",
              String.valueOf(file.getType()),
              String.valueOf(file.getSize()),
              String.valueOf(file.getModified()),
              String.valueOf(file.getSign())));
    }
    return result;
  }

  private Set<String> allDirectories(List<OpenlistApiService.OpenlistFile> files) {
    Set<String> directories = new HashSet<>();
    for (OpenlistApiService.OpenlistFile file : files) {
      directories.add(parentDirectory(file.getPath()));
    }
    return directories;
  }

  private String parentDirectory(String path) {
    if (path == null || path.isBlank()) {
      return "/";
    }
    int slash = path.lastIndexOf('/');
    return slash <= 0 ? "/" : path.substring(0, slash);
  }

  public record ChangeSet(boolean firstRun, Set<String> changedDirectories) {}

  private record Manifest(
      String taskPath,
      String configurationFingerprint,
      String completedAt,
      Map<String, String> entries) {}
}
