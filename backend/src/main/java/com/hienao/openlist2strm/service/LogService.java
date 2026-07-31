/*
 * OStrm - Stream Management System
 * Copyright (C) 2024 OStrm Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.dto.FrontendLogRequest;
import com.hienao.openlist2strm.dto.LogReadResponse;
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.ReversedLinesFileReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogService {

  private static final int MAX_LOG_LINES = 5000;
  private static final int MAX_INCREMENTAL_BYTES = 2 * 1024 * 1024;
  private final Map<String, Long> logGenerations = new ConcurrentHashMap<>();

  @Value("${logging.file.path:./logs}")
  private String logPath;

  @PostConstruct
  public void init() {
    log.info("=== LogService 初始化 ===");
    log.info("配置的日志路径: {}", logPath);
    log.info("实际日志路径: {}", getActualLogPath());
    log.info("工作目录: {}", System.getProperty("user.dir"));
    log.info("支持的日志类型: {}", getSupportedLogTypes());

    // 检查日志文件状态
    for (String logType : getSupportedLogTypes()) {
      Path logFile = getLogFilePath(logType);
      log.info("日志文件 [{}]: {} (存在: {})", logType, logFile.toAbsolutePath(), Files.exists(logFile));
    }
  }

  // 获取实际的日志路径，支持多种路径检测
  private String getActualLogPath() {
    log.debug("配置的日志路径: {}", logPath);

    // 首先尝试使用配置的路径
    Path configuredPath = Paths.get(logPath);
    if (Files.exists(configuredPath)) {
      log.debug("使用配置的日志路径: {}", configuredPath.toAbsolutePath());
      return logPath;
    }

    // 尝试创建配置的路径
    try {
      Files.createDirectories(configuredPath);
      log.info("创建日志目录: {}", configuredPath.toAbsolutePath());
      return logPath;
    } catch (IOException e) {
      log.warn("无法创建配置的日志目录: {}, 错误: {}", configuredPath, e.getMessage());
    }

    // 如果配置路径不存在，尝试项目根目录下的logs
    String projectRoot = System.getProperty("user.dir");
    Path projectLogsPath = Paths.get(projectRoot, "logs");
    if (Files.exists(projectLogsPath)) {
      log.info("使用项目根目录下的日志路径: {}", projectLogsPath.toAbsolutePath());
      return projectLogsPath.toString();
    }

    // 尝试创建项目根目录下的logs
    try {
      Files.createDirectories(projectLogsPath);
      log.info("创建项目日志目录: {}", projectLogsPath.toAbsolutePath());
      return projectLogsPath.toString();
    } catch (IOException e) {
      log.warn("无法创建项目日志目录: {}, 错误: {}", projectLogsPath, e.getMessage());
    }

    // 最后返回配置的路径
    log.warn("所有日志路径检测失败，返回配置路径: {}", logPath);
    return logPath;
  }

  private static final Map<String, String> LOG_FILE_MAPPING =
      Map.of(
          "backend", "backend.log",
          "frontend", "frontend.log");

  /** 获取日志文件路径 */
  private Path getLogFilePath(String logType) {
    String fileName = LOG_FILE_MAPPING.get(logType.toLowerCase());
    if (fileName == null) {
      throw new IllegalArgumentException("不支持的日志类型: " + logType);
    }

    String actualLogPath = getActualLogPath();
    Path logFilePath = Paths.get(actualLogPath, fileName);

    log.debug("日志文件路径: {} -> {}", logType, logFilePath.toAbsolutePath());
    log.debug("日志文件是否存在: {}", Files.exists(logFilePath));

    return logFilePath;
  }

  /** 获取日志行（反向读取文件末尾，避免读取整个文件） */
  public List<String> getLogLines(String logType, int maxLines) {
    validateLineLimit(maxLines);
    Path logFile = getLogFilePath(logType);

    if (!Files.exists(logFile)) {
      log.warn("日志文件不存在: {}", logFile);
      return Collections.emptyList();
    }

    try (ReversedLinesFileReader reader =
        new ReversedLinesFileReader(logFile.toFile(), StandardCharsets.UTF_8)) {

      LinkedList<String> lines = new LinkedList<>();
      String line;
      int count = 0;

      while ((line = reader.readLine()) != null && count < maxLines) {
        lines.addFirst(line); // 保持正确顺序（时间正序）
        count++;
      }

      return lines;

    } catch (IOException e) {
      log.error("读取日志文件失败: {}", logFile, e);
      throw new RuntimeException("读取日志文件失败", e);
    }
  }

  /** 首次读取文件末尾的指定行数，后续根据字节游标只读取新增内容。文件轮转或清空后会自动返回新的快照。 */
  public LogReadResponse readLogChunk(
      String logType, Long cursor, String expectedFileKey, int maxLines) {
    validateLineLimit(maxLines);
    Path logFile = getLogFilePath(logType);

    if (!Files.exists(logFile)) {
      return new LogReadResponse(Collections.emptyList(), 0, null, cursor != null, false);
    }

    try {
      BasicFileAttributes attributes = Files.readAttributes(logFile, BasicFileAttributes.class);
      long fileSize = attributes.size();
      String fileKey = getFileKey(logType, attributes);

      boolean fileChanged =
          cursor != null
              && (cursor < 0
                  || cursor > fileSize
                  || (expectedFileKey != null && !expectedFileKey.equals(fileKey)));

      if (cursor == null || fileChanged) {
        return new LogReadResponse(
            getLogLines(logType, maxLines), fileSize, fileKey, fileChanged, false);
      }

      if (cursor == fileSize) {
        return new LogReadResponse(Collections.emptyList(), cursor, fileKey, false, false);
      }

      return readAppendedBytes(logFile, cursor, fileKey, maxLines);
    } catch (IOException e) {
      log.error("增量读取日志文件失败: {}", logFile, e);
      throw new RuntimeException("增量读取日志文件失败", e);
    }
  }

  private LogReadResponse readAppendedBytes(Path logFile, long cursor, String fileKey, int maxLines)
      throws IOException {
    try (FileChannel channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
      channel.position(cursor);
      int readableBytes =
          (int) Math.min(Math.max(channel.size() - cursor, 0), (long) MAX_INCREMENTAL_BYTES);
      if (readableBytes == 0) {
        return new LogReadResponse(Collections.emptyList(), cursor, fileKey, false, false);
      }

      ByteBuffer buffer = ByteBuffer.allocate(readableBytes);
      int bytesRead = channel.read(buffer);
      if (bytesRead <= 0) {
        return new LogReadResponse(Collections.emptyList(), cursor, fileKey, false, false);
      }

      byte[] bytes = Arrays.copyOf(buffer.array(), bytesRead);
      int consumed = findConsumedBytes(bytes, maxLines, cursor + bytesRead >= channel.size());
      String content = new String(bytes, 0, consumed, StandardCharsets.UTF_8);
      List<String> lines = content.lines().toList();
      long nextCursor = cursor + consumed;

      return new LogReadResponse(
          lines, nextCursor, fileKey, false, consumed > 0 && nextCursor < channel.size());
    }
  }

  private int findConsumedBytes(byte[] bytes, int maxLines, boolean reachedEnd) {
    int lineCount = 0;
    int lastLineEnd = -1;
    for (int i = 0; i < bytes.length; i++) {
      if (bytes[i] == '\n') {
        lineCount++;
        lastLineEnd = i + 1;
        if (lineCount >= maxLines) {
          return lastLineEnd;
        }
      }
    }

    if (reachedEnd || bytes.length >= MAX_INCREMENTAL_BYTES) {
      return bytes.length;
    }
    return Math.max(lastLineEnd, 0);
  }

  private String getFileKey(String logType, BasicFileAttributes attributes) {
    Object fileKey = attributes.fileKey();
    String physicalKey =
        fileKey != null ? fileKey.toString() : String.valueOf(attributes.creationTime().toMillis());
    return physicalKey + ":" + logGenerations.getOrDefault(logType.toLowerCase(), 0L);
  }

  private void validateLineLimit(int maxLines) {
    if (maxLines < 1 || maxLines > MAX_LOG_LINES) {
      throw new IllegalArgumentException("日志行数必须在 1 到 " + MAX_LOG_LINES + " 之间");
    }
  }

  /** 获取日志文件资源 */
  public Resource getLogFile(String logType) {
    Path logFile = getLogFilePath(logType);

    if (!Files.exists(logFile)) {
      log.warn("日志文件不存在: {}", logFile);
      return null;
    }

    return new FileSystemResource(logFile);
  }

  /** 获取日志统计信息 */
  public Map<String, Object> getLogStats(String logType) {
    Path logFile = getLogFilePath(logType);
    Map<String, Object> stats = new HashMap<>();

    if (!Files.exists(logFile)) {
      stats.put("exists", false);
      stats.put("totalLines", 0);
      stats.put("fileSize", 0);
      stats.put("lastModified", null);
      return stats;
    }

    try {
      // 基本文件信息
      stats.put("exists", true);
      stats.put("fileSize", Files.size(logFile));
      stats.put("lastModified", Files.getLastModifiedTime(logFile).toString());

      long totalLines = 0;
      long errorCount = 0;
      long warnCount = 0;
      long infoCount = 0;
      long debugCount = 0;
      Deque<String> recentLines = new ArrayDeque<>(10);

      try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
        String line;
        while ((line = reader.readLine()) != null) {
          totalLines++;
          String level = extractLogLevel(line);
          if ("error".equals(level)) {
            errorCount++;
          } else if ("warn".equals(level)) {
            warnCount++;
          } else if ("info".equals(level)) {
            infoCount++;
          } else if ("debug".equals(level)) {
            debugCount++;
          }

          if (recentLines.size() == 10) {
            recentLines.removeFirst();
          }
          recentLines.addLast(line);
        }
      }

      stats.put("totalLines", totalLines);
      stats.put("errorCount", errorCount);
      stats.put("warnCount", warnCount);
      stats.put("infoCount", infoCount);
      stats.put("debugCount", debugCount);
      stats.put("recentLines", new ArrayList<>(recentLines));

    } catch (IOException e) {
      log.error("获取日志统计信息失败: {}", logFile, e);
      throw new RuntimeException("获取日志统计信息失败", e);
    }

    return stats;
  }

  private String extractLogLevel(String line) {
    String normalized = " " + line.toUpperCase(Locale.ROOT) + " ";
    if (normalized.contains(" ERROR ")) {
      return "error";
    }
    if (normalized.contains(" WARN ")) {
      return "warn";
    }
    if (normalized.contains(" INFO ")) {
      return "info";
    }
    if (normalized.contains(" DEBUG ")) {
      return "debug";
    }
    return "";
  }

  /** 监控日志文件变化（用于实时推送） */
  public void watchLogFile(String logType, LogFileWatcher watcher) {
    Path logFile = getLogFilePath(logType);

    if (!Files.exists(logFile)) {
      log.warn("日志文件不存在，无法监控: {}", logFile);
      return;
    }

    // 这里可以实现文件监控逻辑
    // 由于简化实现，这里只是一个接口定义
    log.info("开始监控日志文件: {}", logFile);
  }

  /** 日志文件监控回调接口 */
  public interface LogFileWatcher {
    void onNewLine(String line);

    void onError(Exception e);
  }

  /** 获取支持的日志类型 */
  public Set<String> getSupportedLogTypes() {
    return LOG_FILE_MAPPING.keySet();
  }

  /** 清理旧日志文件（可选功能） */
  public void cleanOldLogs(int daysToKeep) {
    // 实现日志清理逻辑
    log.info("清理 {} 天前的日志文件", daysToKeep);
  }

  /** 处理前端日志 */
  public synchronized void processFrontendLogs(FrontendLogRequest request) {
    if (request == null || request.getLogs() == null || request.getLogs().isEmpty()) {
      log.warn("接收到空的前端日志请求");
      return;
    }

    Path frontendLogFile = getLogFilePath("frontend");
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    try {
      // 确保日志目录存在
      Files.createDirectories(frontendLogFile.getParent());

      // 格式化日志条目并写入文件
      List<String> logLines = new ArrayList<>();
      for (FrontendLogRequest.LogEntry entry : request.getLogs()) {
        Instant instant =
            (entry.getTimestamp() != null)
                ? Instant.ofEpochMilli(entry.getTimestamp())
                : Instant.now();
        String timestamp =
            LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(formatter);

        StringBuilder logLine = new StringBuilder();
        logLine.append(timestamp).append(" [FRONTEND]");

        // 添加日志级别
        if (entry.getLevel() != null) {
          logLine.append(" ").append(entry.getLevel().toUpperCase());
        }

        // 添加用户信息
        if (entry.getUserId() != null) {
          logLine.append(" [User:").append(entry.getUserId()).append("]");
        }

        // 添加页面URL
        if (entry.getUrl() != null) {
          logLine.append(" [URL:").append(entry.getUrl()).append("]");
        }

        // 添加消息
        logLine.append(" - ").append(entry.getMessage());

        // 添加额外数据
        if (entry.getExtra() != null) {
          logLine.append(" [Extra:").append(entry.getExtra().toString()).append("]");
        }

        logLines.add(logLine.toString());
      }

      // 写入文件
      Files.write(frontendLogFile, logLines, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
      log.debug("成功写入 {} 条前端日志到文件: {}", logLines.size(), frontendLogFile);

    } catch (Exception e) {
      log.error("写入前端日志失败: {}", frontendLogFile, e);
      throw new RuntimeException("写入前端日志失败", e);
    }
  }

  /** 清空日志文件。保留 inode，确保 Logback 持有的文件句柄仍可继续写入。 */
  public synchronized void clearLogFile(String logType) {
    Path logFile = getLogFilePath(logType);

    try {
      Files.createDirectories(logFile.getParent());
      log.info("准备清空日志文件: {}", logFile);
      Files.write(
          logFile,
          new byte[0],
          StandardOpenOption.CREATE,
          StandardOpenOption.WRITE,
          StandardOpenOption.TRUNCATE_EXISTING);
      logGenerations.merge(logType.toLowerCase(), 1L, Long::sum);
    } catch (IOException e) {
      log.error("清空日志文件失败: {}", logFile, e);
      throw new RuntimeException("清空日志文件失败", e);
    }
  }
}
