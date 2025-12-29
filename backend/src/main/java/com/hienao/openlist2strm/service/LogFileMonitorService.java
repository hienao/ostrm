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

import com.hienao.openlist2strm.component.LogWebSocketHandler;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class LogFileMonitorService {

  private final LogWebSocketHandler webSocketHandler;

  @Value("${logging.file.path:./logs}")
  private String logPath;

  // 获取实际的日志路径
  private String getActualLogPath() {
    // 首先尝试使用配置的路径
    Path configuredPath = Paths.get(logPath);
    if (Files.exists(configuredPath)) {
      return logPath;
    }

    // 如果配置路径不存在，尝试项目根目录下的logs
    String projectRoot = System.getProperty("user.dir");
    Path projectLogsPath = Paths.get(projectRoot, "logs");
    if (Files.exists(projectLogsPath)) {
      return projectLogsPath.toString();
    }

    // 都不存在则返回配置的路径
    return logPath;
  }

  private final ExecutorService executorService = Executors.newCachedThreadPool();
  private final ConcurrentHashMap<String, Future<?>> monitorTasks = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Long> lastReadPositions = new ConcurrentHashMap<>();

  @PostConstruct
  public void init() {
    log.info("=== 初始化日志文件监控服务 ===");
    log.info("配置的日志路径: {}", logPath);
    log.info("实际日志路径: {}", getActualLogPath());
    log.info("工作目录: {}", System.getProperty("user.dir"));

    // 检查日志目录
    Path logDir = Paths.get(getActualLogPath());
    log.info("日志目录: {} (存在: {})", logDir.toAbsolutePath(), Files.exists(logDir));

    startMonitoring("backend");
    startMonitoring("frontend");
  }

  @PreDestroy
  public void destroy() {
    log.info("关闭日志文件监控服务");
    monitorTasks.values().forEach(task -> task.cancel(true));
    executorService.shutdown();
  }

  /** 开始监控指定类型的日志文件 */
  public void startMonitoring(String logType) {
    if (monitorTasks.containsKey(logType)) {
      log.warn("日志类型 {} 已在监控中", logType);
      return;
    }

    Future<?> task = executorService.submit(() -> monitorLogFile(logType));
    monitorTasks.put(logType, task);
    log.info("开始监控日志文件: {}", logType);
  }

  /** 停止监控指定类型的日志文件 */
  public void stopMonitoring(String logType) {
    Future<?> task = monitorTasks.remove(logType);
    if (task != null) {
      task.cancel(true);
      log.info("停止监控日志文件: {}", logType);
    }
  }

  /** 监控日志文件变化 (轮询模式) */
  private void monitorLogFile(String logType) {
    Path logFilePath = getLogFilePath(logType);

    try {
      log.info("开始监控日志文件 (轮询): {}", logFilePath);

      // 如果文件不存在，等待文件创建
      while (!Files.exists(logFilePath)) {
        Thread.sleep(2000);
        if (Thread.currentThread().isInterrupted()) {
          return;
        }
      }

      // 初始化读取位置为当前文件大小
      try {
        long lastPosition = Files.size(logFilePath);
        lastReadPositions.put(logType, lastPosition);
        log.info("日志文件已找到: {}, 初始大小: {}", logFilePath, lastPosition);
      } catch (IOException e) {
        log.error("获取初始文件大小失败: {}", logFilePath, e);
      }

      // 轮询循环
      while (!Thread.currentThread().isInterrupted()) {
        try {
          if (Files.exists(logFilePath)) {
            readAndBroadcastNewLines(logType, logFilePath);
          } else {
            // 如果文件被删除，重置位置并等待重建
            lastReadPositions.put(logType, 0L);
            log.warn("日志文件被删除: {}", logFilePath);
          }
          
          Thread.sleep(500); // 500ms 轮询间隔
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
          break;
        } catch (Exception e) {
          log.error("轮询日志文件异常: {}", logType, e);
          // 发生异常暂停一下，避免死循环刷日志
          Thread.sleep(5000);
        }
      }

    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch (Exception e) {
      log.error("监控日志文件致命错误: {}", logType, e);
    }
  }

  /** 读取并广播新的日志行 */
  private void readAndBroadcastNewLines(String logType, Path logFilePath) {
    try {
      long currentSize = Files.size(logFilePath);
      long lastPosition = lastReadPositions.getOrDefault(logType, 0L);

      // 处理日志轮转或截断
      if (currentSize < lastPosition) {
        log.info("日志文件大小变小(轮转?), 重置读取位置: {} -> 0", lastPosition);
        lastPosition = 0;
      }

      if (currentSize > lastPosition) {
        try (RandomAccessFile file = new RandomAccessFile(logFilePath.toFile(), "r")) {
          file.seek(lastPosition);

          // 使用UTF-8编码读取剩余内容
          byte[] buffer = new byte[(int) (currentSize - lastPosition)];
          int bytesRead = file.read(buffer);

          if (bytesRead > 0) {
            // 将字节转换为UTF-8字符串
            String content = new String(buffer, 0, bytesRead, StandardCharsets.UTF_8);

            // 按行分割并广播
            String[] lines = content.split("\r?\n");
            for (int i = 0; i < lines.length; i++) {
              String line = lines[i];

              // 如果不是最后一行，或者文件以换行符结尾，则广播这一行
              if (i < lines.length - 1 || content.endsWith("\n") || content.endsWith("\r\n")) {
                if (!line.isEmpty()) {
                  webSocketHandler.broadcastToLogType(logType, line);
                }
              } else {
                // 最后一行可能不完整，需要调整位置
                byte[] lineBytes = line.getBytes(StandardCharsets.UTF_8);
                file.seek(currentSize - lineBytes.length);
                break;
              }
            }
          }

          lastReadPositions.put(logType, file.getFilePointer());
        }
      }

    } catch (IOException e) {
      log.error("读取日志文件失败: {}", logFilePath, e);
    }
  }

  /** 获取日志文件路径 */
  private Path getLogFilePath(String logType) {
    String fileName =
        switch (logType.toLowerCase()) {
          case "backend" -> "backend.log";
          case "frontend" -> "frontend.log";
          default -> throw new IllegalArgumentException("不支持的日志类型: " + logType);
        };
    return Paths.get(getActualLogPath(), fileName);
  }

  /** 获取监控状态 */
  public boolean isMonitoring(String logType) {
    Future<?> task = monitorTasks.get(logType);
    return task != null && !task.isDone() && !task.isCancelled();
  }

  /** 获取所有监控状态 */
  public java.util.Map<String, Boolean> getAllMonitoringStatus() {
    java.util.Map<String, Boolean> status = new java.util.HashMap<>();
    status.put("backend", isMonitoring("backend"));
    status.put("frontend", isMonitoring("frontend"));
    return status;
  }
}
