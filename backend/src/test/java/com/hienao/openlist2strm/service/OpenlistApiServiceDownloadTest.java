package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import java.net.URI;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

class OpenlistApiServiceDownloadTest {

  @TempDir Path temporaryDirectory;

  @Test
  void streamingDownloadPassesNormalizedUriToHttpClient() {
    RestTemplate restTemplate = mock(RestTemplate.class);
    when(restTemplate.execute(
            any(URI.class),
            eq(HttpMethod.GET),
            any(RequestCallback.class),
            any(ResponseExtractor.class)))
        .thenReturn(false);
    OpenlistApiService service =
        new OpenlistApiService(
            restTemplate, new ObjectMapper(), mock(OpenlistApiRateLimiter.class));
    OpenlistConfig config = new OpenlistConfig().setToken("token");
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName("临时劫案 (2024) {tmdbid-991197}.nfo");
    file.setUrl(
        "http://192.168.88.229:5244/d/115/meida/movie/"
            + "临时劫案 (2024) {tmdbid-991197}/临时劫案 (2024) {tmdbid-991197}.nfo");
    file.setSign("vhDa_M9JsSD5r8kTtZZ9AJFQzKun391omDM-V_EEmPo=:0");

    assertFalse(service.downloadToFile(config, file, temporaryDirectory.resolve(file.getName())));

    ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
    verify(restTemplate)
        .execute(
            uriCaptor.capture(),
            eq(HttpMethod.GET),
            any(RequestCallback.class),
            any(ResponseExtractor.class));
    assertEquals(
        "http://192.168.88.229:5244/d/115/meida/movie/"
            + "%E4%B8%B4%E6%97%B6%E5%8A%AB%E6%A1%88%20(2024)%20%7Btmdbid-991197%7D/"
            + "%E4%B8%B4%E6%97%B6%E5%8A%AB%E6%A1%88%20(2024)%20%7Btmdbid-991197%7D.nfo"
            + "?sign=vhDa_M9JsSD5r8kTtZZ9AJFQzKun391omDM-V_EEmPo%3D%3A0",
        uriCaptor.getValue().toASCIIString());
  }
}
