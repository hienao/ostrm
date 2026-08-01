package com.hienao.openlist2strm.notification;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 将统一通知事件渲染成适合 Apprise 下游渠道的纯文本。 */
@Component
public class NotificationRenderer {

  private static final DateTimeFormatter TIME_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  public RenderedNotification render(
      NotificationEvent event, boolean includeFullPath, int maxDetailItems) {
    String title = title(event);
    List<String> lines = new ArrayList<>();
    add(lines, "任务", event.getTaskName() + "（#" + event.getTaskId() + "）");
    if (event.getJobId() != null) {
      add(lines, "作业", "#" + event.getJobId());
    }
    add(lines, "结果", statusLabel(event.getStatus()));

    if (event.getKind() == NotificationEvent.Kind.TASK) {
      add(lines, "触发方式", triggerLabel(event.getTrigger()));
      add(lines, "执行模式", event.getExecutionMode());
      add(lines, "媒体库", libraryTypeLabel(event.getLibraryType()));
      lines.add("");
      add(lines, "源目录", displayPath(event.getSourcePath(), includeFullPath));
      add(lines, "STRM 目录", displayPath(event.getStrmPath(), includeFullPath));
      lines.add("");
      add(lines, "发现视频", event.getDiscoveredVideos());
      add(lines, "本次处理", event.getSelectedVideos());
      add(lines, "STRM 成功", event.getStrmSucceeded());
      add(lines, "处理失败", event.getProcessingFailed());
      if (event.getIncrementalSkipped() > 0) {
        add(lines, "增量未变化", event.getIncrementalSkipped());
      }
      if (event.getStructureSkipped() > 0) {
        add(lines, "结构异常跳过", event.getStructureSkipped());
      }
      addIssueCounts(lines, event.getIssues());
      if (event.getRenamedDirectories() > 0 || event.getRenamedFiles() > 0) {
        add(
            lines,
            "自动重命名",
            event.getRenamedDirectories() + " 个目录、" + event.getRenamedFiles() + " 个文件");
      }
      if (event.getCleanedStrm() > 0) {
        add(lines, "清理失效 STRM", event.getCleanedStrm() + " 个");
      }
      if (event.getMediaServerName() != null) {
        lines.add("");
        add(
            lines,
            "媒体服务器",
            event.getMediaServerName() + serverTypeSuffix(event.getMediaServerType()));
        add(lines, "刷新范围", refreshScopeLabel(event.getMediaRefreshScope()));
        if (event.getMediaLibraryName() != null) {
          add(lines, "目标媒体库", event.getMediaLibraryName());
        }
        add(lines, "刷新结果", refreshStatusLabel(event.getMediaRefreshStatus()));
        if ("FAILED".equals(event.getMediaRefreshStatus())
            && event.getMediaRefreshMessage() != null) {
          add(lines, "刷新错误", event.getMediaRefreshMessage());
        } else if ("SKIPPED".equals(event.getMediaRefreshStatus())
            && event.getMediaRefreshMessage() != null) {
          add(lines, "刷新说明", event.getMediaRefreshMessage());
        }
      }
    } else {
      add(lines, "媒体类型", libraryTypeLabel(event.getMediaType()));
      if (event.getTmdbId() != null) {
        add(lines, "TMDB ID", event.getTmdbId());
      }
      lines.add("");
      add(lines, "原目录", displayPath(event.getSourcePath(), includeFullPath));
      if (event.getFinalPath() != null) {
        add(lines, "最终目录", displayPath(event.getFinalPath(), includeFullPath));
      }
      add(lines, "重命名目录", event.getRenamedDirectories());
      add(lines, "重命名文件", event.getRenamedFiles());
      if (!event.getUploadedFiles().isEmpty()) {
        add(lines, "上传元数据", event.getUploadedFiles().size());
        if (event.getUploadedFiles().size() <= maxDetailItems) {
          add(lines, "上传文件", String.join("、", event.getUploadedFiles()));
        }
      }
    }

    appendIssues(lines, event.getIssues(), includeFullPath, maxDetailItems);
    if (event.getStatus() == NotificationEvent.Status.FAILURE) {
      lines.add("");
      add(lines, "失败阶段", stageLabel(event.getFailedStage()));
      add(lines, "错误", safeEventError(event, includeFullPath));
      if (event.getKind() == NotificationEvent.Kind.MANUAL_SCRAPING) {
        add(lines, "可以重试", event.isRetryable() ? "是" : "否");
      }
    }

    lines.add("");
    add(lines, "耗时", formatDuration(event.getDurationMillis()));
    add(
        lines,
        event.getStatus() == NotificationEvent.Status.FAILURE ? "失败时间" : "完成时间",
        formatTime(event.getCompletedAt()));
    return new RenderedNotification(title, String.join("\n", lines), type(event.getStatus()));
  }

  private void addIssueCounts(List<String> lines, List<NotificationIssue> issues) {
    Map<NotificationIssue.Category, Long> counts = new LinkedHashMap<>();
    for (NotificationIssue issue : deduplicate(issues)) {
      counts.merge(issue.category(), 1L, Long::sum);
    }
    addCount(lines, "刮削未识别", counts.get(NotificationIssue.Category.SCRAPE_UNRECOGNIZED));
    addCount(lines, "目录重命名失败", counts.get(NotificationIssue.Category.DIRECTORY_RENAME_FAILED));
    addCount(lines, "文件重命名失败", counts.get(NotificationIssue.Category.FILE_RENAME_FAILED));
    addCount(lines, "媒体库刷新失败", counts.get(NotificationIssue.Category.MEDIA_SERVER_REFRESH_FAILED));
  }

  private void addCount(List<String> lines, String label, Long value) {
    if (value != null && value > 0) {
      add(lines, label, value);
    }
  }

  private void appendIssues(
      List<String> lines,
      List<NotificationIssue> rawIssues,
      boolean includeFullPath,
      int maxDetailItems) {
    List<NotificationIssue> issues = deduplicate(rawIssues);
    if (issues.isEmpty()) {
      return;
    }
    lines.add("");
    lines.add("异常明细：");
    for (NotificationIssue.Category category : NotificationIssue.Category.values()) {
      List<NotificationIssue> grouped =
          issues.stream().filter(issue -> issue.category() == category).toList();
      if (grouped.isEmpty()) {
        continue;
      }
      lines.add("");
      lines.add(categoryLabel(category) + "（" + grouped.size() + "）");
      grouped.stream()
          .limit(Math.max(1, maxDetailItems))
          .map(issue -> formatIssue(issue, includeFullPath))
          .forEach(lines::add);
      if (grouped.size() > maxDetailItems) {
        lines.add("• 另有 " + (grouped.size() - maxDetailItems) + " 条，请查看任务日志");
      }
    }
  }

  private List<NotificationIssue> deduplicate(List<NotificationIssue> issues) {
    Map<String, NotificationIssue> unique = new LinkedHashMap<>();
    for (NotificationIssue issue : issues) {
      unique.putIfAbsent(issue.deduplicationKey(), issue);
    }
    return List.copyOf(unique.values());
  }

  private String formatIssue(NotificationIssue issue, boolean includeFullPath) {
    StringBuilder text = new StringBuilder("• ");
    if (issue.reasonCode() != null && !issue.reasonCode().isBlank()) {
      text.append("[").append(reasonLabel(issue.reasonCode())).append("] ");
    } else if (issue.scope() != null && !issue.scope().isBlank()) {
      text.append("[").append(issue.scope()).append("] ");
    }
    text.append(displayPath(issue.sourcePath(), includeFullPath));
    if (issue.targetName() != null && !issue.targetName().isBlank()) {
      text.append(" → ").append(issue.targetName());
    }
    if (issue.mediaTitle() != null && !issue.mediaTitle().isBlank()) {
      text.append("（媒体：").append(issue.mediaTitle());
      if (issue.tmdbId() != null) {
        text.append("，TMDB: ").append(issue.tmdbId());
      }
      text.append("）");
    }
    if (issue.reason() != null && !issue.reason().isBlank()) {
      text.append("\n  原因：").append(safeIssueError(issue, includeFullPath));
    }
    return text.toString();
  }

  private String title(NotificationEvent event) {
    String prefix =
        switch (event.getStatus()) {
          case SUCCESS -> "✅";
          case PARTIAL_SUCCESS -> "⚠️";
          case FAILURE -> "❌";
        };
    String action =
        event.getKind() == NotificationEvent.Kind.TASK
            ? switch (event.getStatus()) {
              case SUCCESS -> "任务完成";
              case PARTIAL_SUCCESS -> "任务部分完成";
              case FAILURE -> "任务失败";
            }
            : event.getStatus() == NotificationEvent.Status.FAILURE ? "手动刮削失败" : "手动刮削完成";
    String subject =
        event.getKind() == NotificationEvent.Kind.MANUAL_SCRAPING
            ? displayPath(
                event.getFinalPath() == null ? event.getSourcePath() : event.getFinalPath(), false)
            : event.getTaskName();
    return prefix + " OStrm " + action + "｜" + subject;
  }

  private String statusLabel(NotificationEvent.Status status) {
    return switch (status) {
      case SUCCESS -> "成功";
      case PARTIAL_SUCCESS -> "部分完成";
      case FAILURE -> "失败";
    };
  }

  private String type(NotificationEvent.Status status) {
    return switch (status) {
      case SUCCESS -> "success";
      case PARTIAL_SUCCESS -> "warning";
      case FAILURE -> "failure";
    };
  }

  private String triggerLabel(NotificationEvent.Trigger trigger) {
    return trigger == NotificationEvent.Trigger.SCHEDULED ? "定时执行" : "手动执行";
  }

  private String libraryTypeLabel(String type) {
    if (type == null) {
      return null;
    }
    return switch (type.toLowerCase()) {
      case "movie" -> "电影";
      case "tv" -> "电视剧";
      case "anime" -> "动画";
      default -> "自动识别";
    };
  }

  private String stageLabel(String stage) {
    if (stage == null || stage.isBlank()) {
      return "未知阶段";
    }
    return switch (stage.toUpperCase()) {
      case "PREPARING", "DISCOVERY" -> "读取和识别媒体";
      case "RENAMING", "AUTO_RENAMING" -> "重命名媒体";
      case "GENERATING", "PROCESSING" -> "生成 STRM 和元数据";
      case "UPLOADING" -> "上传元数据";
      case "CLEANUP" -> "清理失效 STRM";
      default -> stage;
    };
  }

  private String reasonLabel(String reasonCode) {
    return switch (reasonCode) {
      case "LOW_CONFIDENCE" -> "识别置信度过低";
      case "TITLE_UNAVAILABLE" -> "无法提取标题";
      case "TMDB_NOT_MATCHED" -> "TMDB 未匹配";
      case "UNSUPPORTED_MEDIA_TYPE" -> "无法确定媒体类型";
      case "MEDIA_LIBRARY_NOT_FOUND" -> "媒体库已失效";
      case "MEDIA_LIBRARY_PATH_EMPTY" -> "媒体库路径为空";
      case "MEDIA_SERVER_DISABLED" -> "媒体服务器已停用";
      case "MEDIA_SERVER_NOT_FOUND" -> "媒体服务器配置不存在";
      case "MEDIA_SERVER_REFRESH_FAILED" -> "刷新请求失败";
      default -> reasonCode;
    };
  }

  private String categoryLabel(NotificationIssue.Category category) {
    return switch (category) {
      case SCRAPE_UNRECOGNIZED -> "刮削未识别";
      case STRUCTURE_INVALID_SKIPPED -> "结构异常跳过";
      case DIRECTORY_RENAME_FAILED -> "目录重命名失败";
      case FILE_RENAME_FAILED -> "文件重命名失败";
      case PROCESSING_FAILED -> "处理失败";
      case MEDIA_SERVER_REFRESH_FAILED -> "媒体库刷新失败";
    };
  }

  private String serverTypeSuffix(String type) {
    return type == null ? "" : "（" + type + "）";
  }

  private String refreshScopeLabel(String scope) {
    if (scope == null) {
      return null;
    }
    return switch (scope) {
      case "ALL" -> "全部媒体库";
      case "LIBRARY" -> "指定媒体库（精确刷新）";
      default -> "不刷新";
    };
  }

  private String refreshStatusLabel(String status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case "TRIGGERED" -> "已提交刷新请求";
      case "SKIPPED" -> "已跳过";
      case "FAILED" -> "失败";
      default -> status;
    };
  }

  private String displayPath(String value, boolean includeFullPath) {
    if (value == null || value.isBlank()) {
      return null;
    }
    if (includeFullPath) {
      return value;
    }
    String normalized = value.replace('\\', '/').replaceAll("/+$", "");
    int slash = normalized.lastIndexOf('/');
    return slash >= 0 ? normalized.substring(slash + 1) : normalized;
  }

  private String safeEventError(NotificationEvent event, boolean includeFullPath) {
    String value = event.getErrorMessage();
    if (!includeFullPath) {
      for (NotificationIssue issue : event.getIssues()) {
        value = hideIssuePaths(value, issue);
      }
      value = hideKnownPath(value, event.getSourcePath());
      value = hideKnownPath(value, event.getStrmPath());
      value = hideKnownPath(value, event.getFinalPath());
    }
    return safeError(value);
  }

  private String safeIssueError(NotificationIssue issue, boolean includeFullPath) {
    String value = issue.reason();
    if (!includeFullPath) {
      value = hideIssuePaths(value, issue);
    }
    return safeError(value);
  }

  private String hideIssuePaths(String value, NotificationIssue issue) {
    value = hideKnownPath(value, issue.sourcePath());
    if (issue.sourcePath() != null && issue.targetName() != null) {
      String normalized = issue.sourcePath().replace('\\', '/');
      int slash = normalized.lastIndexOf('/');
      if (slash >= 0) {
        value = hideKnownPath(value, normalized.substring(0, slash + 1) + issue.targetName());
      }
    }
    return value;
  }

  private String hideKnownPath(String value, String path) {
    if (value == null || path == null || path.isBlank()) {
      return value;
    }
    return value.replace(path, displayPath(path, false));
  }

  private String safeError(String value) {
    if (value == null || value.isBlank()) {
      return "未知错误";
    }
    String sanitized = value.replaceAll("(?i)(token|api[_ -]?key|sign)=([^\\s&]+)", "$1=***");
    return sanitized.length() > 500 ? sanitized.substring(0, 500) + "…" : sanitized;
  }

  private String formatDuration(long millis) {
    long seconds = Math.max(0, millis / 1000);
    long minutes = seconds / 60;
    long hours = minutes / 60;
    if (hours > 0) {
      return hours + "小时" + (minutes % 60) + "分" + (seconds % 60) + "秒";
    }
    if (minutes > 0) {
      return minutes + "分" + (seconds % 60) + "秒";
    }
    return seconds + "秒";
  }

  private String formatTime(LocalDateTime value) {
    return (value == null ? LocalDateTime.now() : value).format(TIME_FORMATTER);
  }

  private void add(List<String> lines, String label, Object value) {
    if (value != null && !String.valueOf(value).isBlank()) {
      lines.add(label + "：" + value);
    }
  }
}
