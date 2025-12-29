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

package com.hienao.openlist2strm.component;

import com.hienao.openlist2strm.config.PathConfiguration;
import com.hienao.openlist2strm.service.LogService;
import java.io.IOException;
import java.net.URI;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class LogWebSocketHandler implements WebSocketHandler {

  private final LogService logService;
  private final PathConfiguration pathConfiguration;
  private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, WatchService> watchServices = new ConcurrentHashMap<>();
  private final ExecutorService executorService = Executors.newCachedThreadPool();

  @Override
  public void afterConnectionEstablished(WebSocketSession session) throws Exception {
    String logType = extractLogTypeFromPath(session.getUri());
    if (logType == null) {
      session.close(CloseStatus.BAD_DATA.withReason("Invalid log type"));
      return;
    }

    String sessionKey = session.getId() + "_" + logType;
    sessions.put(sessionKey, session);

    log.info("WebSocket连接已建立: sessionId={}, logType={}", session.getId(), logType);

    // 发送连接成功消息
    session.sendMessage(new TextMessage("连接成功，开始接收 " + logType + " 日志"));
  }

  @Override
  public void handleMessage(WebSocketSession session, WebSocketMessage<?> message)
      throws Exception {
    // 处理客户端发送的消息（如果需要）
    log.debug("收到WebSocket消息: sessionId={}, message={}", session.getId(), message.getPayload());
  }

  @Override
  public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
    log.error("WebSocket传输错误: sessionId={}", session.getId(), exception);
    cleanupSession(session);
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus)
      throws Exception {
    log.info("WebSocket连接已关闭: sessionId={}, status={}", session.getId(), closeStatus);
    cleanupSession(session);
  }

  @Override
  public boolean supportsPartialMessages() {
    return false;
  }

  /** 从WebSocket路径中提取日志类型 */
  private String extractLogTypeFromPath(URI uri) {
    if (uri == null) return null;

    String path = uri.getPath();
    String[] segments = path.split("/");

    // 路径格式: /ws/logs/{logType}
    if (segments.length >= 4 && "ws".equals(segments[1]) && "logs".equals(segments[2])) {
      String logType = segments[3];
      if (logService.getSupportedLogTypes().contains(logType)) {
        return logType;
      }
    }

    return null;
  }

  /** 获取日志文件路径 */
  private Path getLogFilePath(String logType) {
    // 使用统一的路径配置
    String logPath = pathConfiguration.getLogs();
    String fileName =
        switch (logType.toLowerCase()) {
          case "backend" -> "backend.log";
          case "frontend" -> "frontend.log";
          default -> throw new IllegalArgumentException("不支持的日志类型: " + logType);
        };
    return Paths.get(logPath, fileName);
  }

  /** 清理会话资源 */
  private void cleanupSession(WebSocketSession session) {
    String sessionId = session.getId();

    // 移除会话
    sessions.entrySet().removeIf(entry -> entry.getKey().startsWith(sessionId));

    log.info("清理WebSocket会话: {}", sessionId);
  }

  /** 广播消息到所有连接的会话 */
  public void broadcastToLogType(String logType, String message) {
    sessions.entrySet().stream()
        .filter(entry -> entry.getKey().endsWith("_" + logType))
        .forEach(
            entry -> {
              try {
                WebSocketSession session = entry.getValue();
                if (session.isOpen()) {
                  session.sendMessage(new TextMessage(message));
                }
              } catch (IOException e) {
                log.error("广播消息失败: sessionId={}", entry.getValue().getId(), e);
              }
            });
  }
}
