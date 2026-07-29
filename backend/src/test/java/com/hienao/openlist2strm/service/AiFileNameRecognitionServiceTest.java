package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.dto.media.AiRecognitionResult;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class AiFileNameRecognitionServiceTest {

  private SystemConfigService systemConfigService;
  private RestTemplate restTemplate;
  private AiFileNameRecognitionService service;

  @BeforeEach
  void setUp() {
    systemConfigService = mock(SystemConfigService.class);
    restTemplate = mock(RestTemplate.class);
    service =
        new AiFileNameRecognitionService(systemConfigService, restTemplate, new ObjectMapper());
  }

  @Test
  void parsesResponseOnlyAfterStrictValidation() {
    AiRecognitionResult result =
        service.parseJsonResponse(
            """
            {
              "success": true,
              "titleCandidates": ["测试电影", "Test Movie"],
              "title": null,
              "year": "2024",
              "season": null,
              "episode": null,
              "type": "movie",
              "reason": null
            }
            """);

    assertTrue(result.isSuccess());
    assertEquals("测试电影", result.getTitle());
    assertEquals(2, result.getTitleCandidates().size());
    assertEquals("2024", result.getYear());
    assertEquals("movie", result.getType());
  }

  @Test
  void acceptsValidFailureResponse() {
    AiRecognitionResult result =
        service.parseJsonResponse(
            """
            {
              "success": false,
              "titleCandidates": [],
              "title": null,
              "year": null,
              "season": null,
              "episode": null,
              "type": "unknown",
              "reason": "路径中没有足够信息"
            }
            """);

    assertFalse(result.isSuccess());
    assertEquals("路径中没有足够信息", result.getReason());
  }

  @Test
  void rejectsWrongTypesMissingFieldsAndAdditionalFields() {
    assertNull(
        service.parseJsonResponse(
            """
            {
              "success": "true",
              "titleCandidates": ["测试电影"],
              "title": "测试电影",
              "year": "2024",
              "season": null,
              "episode": null,
              "type": "movie",
              "reason": null
            }
            """));

    assertNull(
        service.parseJsonResponse(
            """
            {
              "success": true,
              "titleCandidates": ["测试电影"],
              "title": "测试电影",
              "year": "2024",
              "season": null,
              "episode": null,
              "type": "movie",
              "reason": null,
              "unexpected": "value"
            }
            """));
  }

  @Test
  void rejectsMarkdownAndTrailingContent() {
    String validJson =
        """
        {
          "success": true,
          "titleCandidates": ["测试电影"],
          "title": "测试电影",
          "year": "2024",
          "season": null,
          "episode": null,
          "type": "movie",
          "reason": null
        }
        """;

    assertNull(service.parseJsonResponse("```json\n" + validJson + "\n```"));
    assertNull(service.parseJsonResponse(validJson + "\n额外说明"));
  }

  @Test
  void retriesOnceWhenCompletionIsTruncated() {
    when(systemConfigService.getAiConfig())
        .thenReturn(
            Map.of(
                "enabled",
                true,
                "baseUrl",
                "https://example.com/v1",
                "apiKey",
                "test-key",
                "model",
                "test-model",
                "qpmLimit",
                60));
    ResponseEntity<String> truncatedResponse =
        new ResponseEntity<>(
            """
            {
              "choices": [
                {
                  "finish_reason": "length",
                  "message": {"content": "{\\"success\\": true"}
                }
              ]
            }
            """,
            HttpStatus.OK);
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(truncatedResponse);

    assertNull(service.recognizeFileName("测试电影.mkv", "测试电影", "movie"));
    verify(restTemplate, times(2))
        .exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class));
  }
}
