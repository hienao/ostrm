package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.notification.NotificationEvent;
import com.hienao.openlist2strm.notification.NotificationRenderer;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class NotificationServiceTest {

  @Test
  void sendsAppriseJsonWithConfiguredKeyAndTag() {
    SystemConfigService configService = mock(SystemConfigService.class);
    RestTemplate restTemplate = mock(RestTemplate.class);
    NotificationService service =
        new NotificationService(configService, new NotificationRenderer(), restTemplate);
    when(restTemplate.postForEntity(
            eq("http://apprise:8000/notify/ostrm/"), any(), eq(String.class)))
        .thenReturn(ResponseEntity.ok("ok"));
    Map<String, Object> config =
        Map.of(
            "serverUrl", "http://apprise:8000/",
            "configKey", "ostrm",
            "tags", "all",
            "includeFullPath", true,
            "maxDetailItems", 5);

    service.send(config, successEvent());

    @SuppressWarnings("unchecked")
    ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
    verify(restTemplate)
        .postForEntity(
            eq("http://apprise:8000/notify/ostrm/"), payload.capture(), eq(String.class));
    assertEquals("success", payload.getValue().get("type"));
    assertEquals("text", payload.getValue().get("format"));
    assertEquals("all", payload.getValue().get("tag"));
  }

  @Test
  void treatsMissingAppriseConfigurationAsFailureAndRetries() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    NotificationService service =
        new NotificationService(
            mock(SystemConfigService.class), new NotificationRenderer(), restTemplate);
    when(restTemplate.postForEntity(
            eq("http://apprise:8000/notify/missing/"), any(), eq(String.class)))
        .thenReturn(ResponseEntity.noContent().build());
    Map<String, Object> config = Map.of("serverUrl", "http://apprise:8000", "configKey", "missing");

    assertThrows(BusinessException.class, () -> service.send(config, successEvent()));

    verify(restTemplate, times(2))
        .postForEntity(eq("http://apprise:8000/notify/missing/"), any(), eq(String.class));
  }

  private NotificationEvent successEvent() {
    return NotificationEvent.builder()
        .kind(NotificationEvent.Kind.TASK)
        .status(NotificationEvent.Status.SUCCESS)
        .trigger(NotificationEvent.Trigger.MANUAL)
        .taskId(1L)
        .taskName("测试")
        .executionMode("增量")
        .libraryType("movie")
        .sourcePath("/movies")
        .strmPath("/strm")
        .completedAt(LocalDateTime.now())
        .build();
  }
}
