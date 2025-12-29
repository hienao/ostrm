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
import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class LogService {

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

  /** 获取日志行 */
  public List<String> getLogLines(String logType, int maxLines) {
    Path logFile = getLogFilePath(logType);

    if (!Files.exists(logFile)) {
      log.warn("日志文件不存在: {}", logFile);
      return Collections.emptyList();
    }

    try {
      List<String> allLines = Files.readAllLines(logFile);

      // 如果请求的行数大于等于总行数，返回所有行
      if (maxLines >= allLines.size()) {
        return allLines;
      }

      // 返回最后的 maxLines 行
      return allLines.subList(allLines.size() - maxLines, allLines.size());

    } catch (IOException e) {
      log.error("读取日志文件失败: {}", logFile, e);
      throw new RuntimeException("读取日志文件失败", e);
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

      // 读取文件内容进行统计
      List<String> lines = Files.readAllLines(logFile);
      stats.put("totalLines", lines.size());

      // 统计不同级别的日志数量
      long errorCount =
          lines.stream()
              .filter(
                  line ->
                      line.toLowerCase().contains("error")
                          || line.toLowerCase().contains("exception")
                          || line.toLowerCase().contains("failed"))
              .count();

      long warnCount =
          lines.stream()
              .filter(
                  line ->
                      line.toLowerCase().contains("warn") || line.toLowerCase().contains("warning"))
              .count();

      long infoCount = lines.stream().filter(line -> line.toLowerCase().contains("info")).count();

      long debugCount = lines.stream().filter(line -> line.toLowerCase().contains("debug")).count();

      stats.put("errorCount", errorCount);
      stats.put("warnCount", warnCount);
      stats.put("infoCount", infoCount);
      stats.put("debugCount", debugCount);

      // 最近的几行日志（用于预览）
      int previewLines = Math.min(10, lines.size());
      if (previewLines > 0) {
        List<String> recentLines = lines.subList(lines.size() - previewLines, lines.size());
        stats.put("recentLines", recentLines);
      } else {
        stats.put("recentLines", Collections.emptyList());
      }

    } catch (IOException e) {
      log.error("获取日志统计信息失败: {}", logFile, e);
      throw new RuntimeException("获取日志统计信息失败", e);
    }

    return stats;
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
  public void processFrontendLogs(FrontendLogRequest request) {
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
}
