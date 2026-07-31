package com.hienao.openlist2strm.util;

import com.hienao.openlist2strm.entity.MediaLibraryType;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/** 按任务媒体库类型检查视频文件相对于任务根目录的层级。 */
public final class TaskDirectoryStructureValidator {

  private TaskDirectoryStructureValidator() {}

  /** 计算包含文件名的任务相对路径，供目录检查与正式任务过滤共同使用。 */
  public static String calculateRelativePath(String taskPath, String filePath) {
    if (taskPath == null || filePath == null) {
      return "";
    }
    String normalizedRoot = normalizePath(taskPath).replaceAll("/+$", "");
    String normalizedFile = normalizePath(filePath);
    String prefix = normalizedRoot + "/";
    return normalizedFile.startsWith(prefix) ? normalizedFile.substring(prefix.length()) : "";
  }

  public static Optional<String> validate(String relativeFilePath, MediaLibraryType libraryType) {
    List<String> segments = splitPath(relativeFilePath);
    if (segments.isEmpty()) {
      return Optional.of("无法计算文件相对于任务根目录的路径");
    }

    return switch (libraryType) {
      case MOVIE -> validateMovie(segments);
      case TV -> validateTv(segments);
      case ANIME -> validateAnime(segments);
      case AUTO -> Optional.of("自动识别任务没有固定目录结构，请先选择明确的媒体库类型");
    };
  }

  private static Optional<String> validateMovie(List<String> segments) {
    if (segments.size() == 1) {
      return Optional.of("电影文件不能直接放在任务根目录，应使用“电影目录/视频文件”结构");
    }
    if (segments.size() > 2) {
      return Optional.of("电影文件层级过深，应使用“电影目录/视频文件”结构");
    }
    return Optional.empty();
  }

  private static Optional<String> validateTv(List<String> segments) {
    if (segments.size() < 3) {
      return Optional.of("电视剧文件缺少季目录，应使用“剧名/Season 01/视频文件”结构");
    }
    if (segments.size() > 3) {
      return Optional.of("电视剧文件层级过深，应使用“剧名/Season 01/视频文件”结构");
    }
    if (!TaskMediaParser.isSeasonDirectory(segments.get(1))) {
      return Optional.of("第二级目录不是有效季目录，应使用 Season 01、S01 或第1季");
    }
    return Optional.empty();
  }

  private static Optional<String> validateAnime(List<String> segments) {
    if (segments.size() == 1) {
      return Optional.of("动画文件不能直接放在任务根目录，应至少使用“动画名/视频文件”结构");
    }
    if (segments.size() == 2) {
      return Optional.empty();
    }
    if (segments.size() > 3) {
      return Optional.of("动画文件层级过深，应使用“动画名/Season 01/视频文件”结构");
    }
    if (!TaskMediaParser.isSeasonDirectory(segments.get(1))) {
      return Optional.of("动画的第二级目录不是有效季目录，应使用 Season 01、S01 或第1季");
    }
    return Optional.empty();
  }

  public static List<String> splitPath(String path) {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    return Arrays.stream(path.replace('\\', '/').split("/+"))
        .filter(segment -> !segment.isBlank())
        .toList();
  }

  public static String normalizePath(String path) {
    String normalized =
        Paths.get(path.replace('\\', '/')).normalize().toString().replace('\\', '/');
    return normalized.startsWith("/") ? normalized : "/" + normalized;
  }
}
