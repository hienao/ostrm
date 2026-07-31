package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class OpenlistApiServiceRateLimitTest {

  private RestTemplate restTemplate;
  private OpenlistApiRateLimiter rateLimiter;
  private OpenlistApiService service;
  private OpenlistConfig config;

  @BeforeEach
  void setUp() {
    restTemplate = mock(RestTemplate.class);
    rateLimiter = mock(OpenlistApiRateLimiter.class);
    service = new OpenlistApiService(restTemplate, new ObjectMapper(), rateLimiter);
    config = config();
  }

  @Test
  void directoryListAcquiresRateLimitPermit() {
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"data\":{\"content\":[]}}"));

    assertTrue(service.getDirectoryContents(config, "/movies").isEmpty());

    verify(rateLimiter).acquire(config, "/api/fs/list");
  }

  @Test
  void directoryListPassesForcedRefreshToOpenlist() {
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"data\":{\"content\":[]}}"));

    service.getDirectoryContents(config, "/movies", true);

    ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(anyString(), eq(HttpMethod.POST), entityCaptor.capture(), eq(String.class));
    assertTrue(entityCaptor.getValue().getBody().contains("\"refresh\":true"));
  }

  @Test
  void pathValidationAcquiresRateLimitPermit() {
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"data\":{\"is_dir\":true}}"));

    assertTrue(service.validatePath(config, "/movies"));

    verify(rateLimiter).acquire(config, "/api/fs/get");
  }

  @Test
  void renameUsesOfficialMutationEndpoint() {
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"message\":\"success\",\"data\":null}"));

    service.renameEntry(config, "/movies/旧名称", "新名称 (2026)");

    ArgumentCaptor<HttpEntity<String>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            eq("https://openlist.example.com/api/fs/rename"),
            eq(HttpMethod.POST),
            entityCaptor.capture(),
            eq(String.class));
    assertTrue(entityCaptor.getValue().getBody().contains("\"overwrite\":false"));
    assertTrue(entityCaptor.getValue().getBody().contains("\"path\":\"/movies/旧名称\""));
    verify(rateLimiter).acquire(config, "/api/fs/rename");
  }

  @Test
  void uploadEncodesFilePathHeaderAndSendsBytes() {
    byte[] content = "metadata".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.PUT), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"message\":\"success\",\"data\":null}"));

    service.uploadFile(config, "/电影/海报 poster.jpg", content, "image/jpeg");

    ArgumentCaptor<HttpEntity<byte[]>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
    verify(restTemplate)
        .exchange(
            eq("https://openlist.example.com/api/fs/put"),
            eq(HttpMethod.PUT),
            entityCaptor.capture(),
            eq(String.class));
    assertEquals(
        "%2F%E7%94%B5%E5%BD%B1%2F%E6%B5%B7%E6%8A%A5%20poster.jpg",
        entityCaptor.getValue().getHeaders().getFirst("File-Path"));
    assertArrayEquals(content, entityCaptor.getValue().getBody());
    verify(rateLimiter).acquire(config, "/api/fs/put");
  }

  private OpenlistConfig config() {
    return new OpenlistConfig()
        .setId(1L)
        .setBaseUrl("https://openlist.example.com")
        .setToken("token")
        .setBasePath("/")
        .setFsApiQpmLimit(60);
  }
}
