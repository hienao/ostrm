package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class OpenlistApiServiceRateLimitTest {

  @Test
  void directoryListAcquiresRateLimitPermit() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    OpenlistApiRateLimiter rateLimiter = mock(OpenlistApiRateLimiter.class);
    OpenlistApiService service =
        new OpenlistApiService(restTemplate, new ObjectMapper(), rateLimiter);
    OpenlistConfig config = config();
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"data\":{\"content\":[]}}"));

    assertTrue(service.getDirectoryContents(config, "/movies").isEmpty());

    verify(rateLimiter).acquire(config, "/api/fs/list");
  }

  @Test
  void pathValidationAcquiresRateLimitPermit() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    OpenlistApiRateLimiter rateLimiter = mock(OpenlistApiRateLimiter.class);
    OpenlistApiService service =
        new OpenlistApiService(restTemplate, new ObjectMapper(), rateLimiter);
    OpenlistConfig config = config();
    when(restTemplate.exchange(
            anyString(), eq(HttpMethod.POST), any(HttpEntity.class), eq(String.class)))
        .thenReturn(ResponseEntity.ok("{\"code\":200,\"data\":{\"is_dir\":true}}"));

    assertTrue(service.validatePath(config, "/movies"));

    verify(rateLimiter).acquire(config, "/api/fs/get");
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
