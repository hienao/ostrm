package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.notification.NotificationEvent;
import com.hienao.openlist2strm.notification.NotificationRenderer;
import com.hienao.openlist2strm.notification.RenderedNotification;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** 统一通知入口；当前通过 Apprise API 投递，发送失败不影响业务任务状态。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

  private final SystemConfigService systemConfigService;
  private final NotificationRenderer notificationRenderer;
  private final RestTemplate restTemplate;

  @Async("notificationExecutor")
  public void notifyAsync(NotificationEvent event) {
    try {
      Map<String, Object> config = systemConfigService.getNotificationConfig();
      if (!enabledFor(config, event.getStatus())) {
        return;
      }
      send(config, event);
    } catch (Exception e) {
      log.error(
          "发送任务通知失败，不影响任务结果: kind={}, taskId={}, error={}",
          event.getKind(),
          event.getTaskId(),
          e.getMessage());
    }
  }

  public void testApprise(Map<String, Object> suppliedConfig) {
    Map<String, Object> config = normalizeConfig(suppliedConfig);
    NotificationEvent event =
        NotificationEvent.builder()
            .kind(NotificationEvent.Kind.TASK)
            .status(NotificationEvent.Status.SUCCESS)
            .trigger(NotificationEvent.Trigger.MANUAL)
            .taskId(0L)
            .taskName("Apprise 测试通知")
            .executionMode("测试")
            .libraryType("movie")
            .sourcePath("/media/example")
            .strmPath("/app/backend/strm/example")
            .completedAt(java.time.LocalDateTime.now())
            .build();
    send(config, event);
  }

  void send(Map<String, Object> config, NotificationEvent event) {
    boolean includeFullPath = booleanValue(config, "includeFullPath", true);
    int maxDetailItems = Math.min(20, Math.max(1, intValue(config, "maxDetailItems", 5)));
    RenderedNotification rendered =
        notificationRenderer.render(event, includeFullPath, maxDetailItems);

    String serverUrl = stringValue(config, "serverUrl");
    String configKey = stringValue(config, "configKey");
    if (serverUrl == null || serverUrl.isBlank()) {
      throw new BusinessException("Apprise 服务地址不能为空");
    }
    if (configKey == null || !configKey.matches("[A-Za-z0-9_-]{1,128}")) {
      throw new BusinessException("Apprise Config ID 只能包含字母、数字、下划线和短横线");
    }

    String endpoint = serverUrl.replaceAll("/+$", "") + "/notify/" + configKey + "/";
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("title", rendered.title());
    payload.put("body", rendered.body());
    payload.put("type", rendered.type());
    payload.put("format", "text");
    String tags = stringValue(config, "tags");
    if (tags != null && !tags.isBlank()) {
      payload.put("tag", tags.trim());
    }

    Exception lastError = null;
    for (int attempt = 1; attempt <= 2; attempt++) {
      try {
        ResponseEntity<String> response =
            restTemplate.postForEntity(endpoint, payload, String.class);
        if (!response.getStatusCode().is2xxSuccessful()
            || response.getStatusCode().value() == 204) {
          throw new BusinessException("Apprise 返回状态码 " + response.getStatusCode().value());
        }
        log.info("Apprise 通知发送成功: taskId={}, type={}", event.getTaskId(), rendered.type());
        return;
      } catch (Exception e) {
        lastError = e;
        log.warn("Apprise 通知发送失败，第 {}/2 次: {}", attempt, e.getMessage());
      }
    }
    throw new BusinessException(
        "Apprise 通知发送失败: " + (lastError == null ? "未知错误" : lastError.getMessage()), lastError);
  }

  private boolean enabledFor(Map<String, Object> config, NotificationEvent.Status status) {
    if (!booleanValue(config, "enabled", false)) {
      return false;
    }
    return switch (status) {
      case SUCCESS -> booleanValue(config, "notifyOnSuccess", true);
      case PARTIAL_SUCCESS -> booleanValue(config, "notifyOnPartialSuccess", true);
      case FAILURE -> booleanValue(config, "notifyOnFailure", true);
    };
  }

  private Map<String, Object> normalizeConfig(Map<String, Object> suppliedConfig) {
    if (suppliedConfig == null) {
      return Map.of();
    }
    Object nested = suppliedConfig.get("notifications");
    if (nested instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, Object> typed = (Map<String, Object>) map;
      return typed;
    }
    return suppliedConfig;
  }

  private boolean booleanValue(Map<String, Object> config, String key, boolean defaultValue) {
    Object value = config.get(key);
    return value instanceof Boolean bool ? bool : defaultValue;
  }

  private int intValue(Map<String, Object> config, String key, int defaultValue) {
    Object value = config.get(key);
    return value instanceof Number number ? number.intValue() : defaultValue;
  }

  private String stringValue(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value == null ? null : String.valueOf(value).trim();
  }
}
