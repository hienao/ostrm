package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.entity.MediaRefreshScope;
import com.hienao.openlist2strm.entity.MediaServerConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class MediaServerApiServiceTest {

  private MediaServerApiService service;
  private MockRestServiceServer server;

  @BeforeEach
  void setUp() {
    RestTemplate restTemplate = new RestTemplate();
    server = MockRestServiceServer.bindTo(restTemplate).build();
    service = new MediaServerApiService(null, new ObjectMapper(), restTemplate);
  }

  @Test
  void springCanCreateServiceWhenTestConstructorAlsoExists() {
    try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
      context.registerBean(
          MediaServerConfigService.class, () -> mock(MediaServerConfigService.class));
      context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
      context.register(MediaServerApiService.class);

      context.refresh();

      assertNotNull(context.getBean(MediaServerApiService.class));
    }
  }

  @Test
  void preciselyRefreshesEveryLocationOfSelectedJellyfinLibrary() {
    MediaServerConfig config = jellyfin();
    server
        .expect(requestTo("http://jellyfin:8096/Library/VirtualFolders"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(
            header(
                "Authorization",
                "MediaBrowser Client=\"OStrm\", Device=\"Server\", DeviceId=\"ostrm-server\","
                    + " Version=\"1.0\", Token=\"secret\""))
        .andRespond(
            withSuccess(
                "[{\"ItemId\":\"movies-id\",\"Name\":\"电影\",\"Locations\":[\"/media/movies\",\"/media/movies2\"]}]",
                MediaType.APPLICATION_JSON));
    server
        .expect(requestTo("http://jellyfin:8096/Library/Media/Updated"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(
            content()
                .json(
                    "{\"Updates\":[{\"Path\":\"/media/movies\",\"UpdateType\":\"Modified\"},{\"Path\":\"/media/movies2\",\"UpdateType\":\"Modified\"}]}"))
        .andRespond(withSuccess());

    MediaServerRefreshResult result =
        service.refresh(config, MediaRefreshScope.LIBRARY, "movies-id", "旧名称");

    assertEquals(MediaServerRefreshResult.Status.TRIGGERED, result.status());
    assertEquals("电影", result.libraryName());
    server.verify();
  }

  @Test
  void doesNotFallBackToFullRefreshWhenLibraryDisappears() {
    MediaServerConfig config = jellyfin();
    server
        .expect(requestTo("http://jellyfin:8096/Library/VirtualFolders"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    MediaServerRefreshResult result =
        service.refresh(config, MediaRefreshScope.LIBRARY, "missing", "已删除媒体库");

    assertEquals(MediaServerRefreshResult.Status.FAILED, result.status());
    assertEquals("MEDIA_LIBRARY_NOT_FOUND", result.failureCode());
    server.verify();
  }

  @Test
  void performsFullEmbyRefreshWithOfficialTokenHeader() {
    MediaServerConfig config =
        new MediaServerConfig()
            .setName("Emby")
            .setServerType("EMBY")
            .setApiBaseUrl("http://emby:8096/emby")
            .setApiKey("emby-key")
            .setIsActive(true);
    server
        .expect(requestTo("http://emby:8096/emby/Library/Refresh"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("X-Emby-Token", "emby-key"))
        .andRespond(withSuccess());

    MediaServerRefreshResult result = service.refresh(config, MediaRefreshScope.ALL, null, null);

    assertEquals(MediaServerRefreshResult.Status.TRIGGERED, result.status());
    server.verify();
  }

  private MediaServerConfig jellyfin() {
    return new MediaServerConfig()
        .setName("Jellyfin")
        .setServerType("JELLYFIN")
        .setApiBaseUrl("http://jellyfin:8096")
        .setApiKey("secret")
        .setIsActive(true);
  }
}
