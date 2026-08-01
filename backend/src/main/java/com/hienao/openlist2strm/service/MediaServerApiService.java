package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hienao.openlist2strm.dto.media.MediaServerDtos;
import com.hienao.openlist2strm.entity.MediaRefreshScope;
import com.hienao.openlist2strm.entity.MediaServerConfig;
import com.hienao.openlist2strm.entity.MediaServerType;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/** Emby/Jellyfin API 兼容层。 */
@Service
public class MediaServerApiService {

  private final MediaServerConfigService configService;
  private final ObjectMapper objectMapper;
  private final RestTemplate restTemplate;

  @Autowired
  public MediaServerApiService(MediaServerConfigService configService, ObjectMapper objectMapper) {
    this(configService, objectMapper, createRestTemplate());
  }

  MediaServerApiService(
      MediaServerConfigService configService,
      ObjectMapper objectMapper,
      RestTemplate restTemplate) {
    this.configService = configService;
    this.objectMapper = objectMapper;
    this.restTemplate = restTemplate;
  }

  public MediaServerDtos.ConnectionResult test(MediaServerConfig config) {
    JsonNode info = request(config, HttpMethod.GET, "/System/Info", null);
    List<MediaServerDtos.LibraryView> libraries = listLibraries(config);
    return MediaServerDtos.ConnectionResult.builder()
        .serverName(text(info, "ServerName", text(info, "Name", config.getName())))
        .version(text(info, "Version", null))
        .productName(text(info, "ProductName", config.getServerType()))
        .libraryCount(libraries.size())
        .build();
  }

  public List<MediaServerDtos.LibraryView> listLibraries(Long configId) {
    return listLibraries(configService.getRequired(configId));
  }

  public List<MediaServerDtos.LibraryView> listLibraries(MediaServerConfig config) {
    MediaServerType type = MediaServerType.from(config.getServerType());
    String path =
        type == MediaServerType.EMBY ? "/Library/VirtualFolders/Query" : "/Library/VirtualFolders";
    JsonNode root = request(config, HttpMethod.GET, path, null);
    JsonNode items = type == MediaServerType.EMBY ? root.path("Items") : root;
    if (!items.isArray()) {
      throw new BusinessException("媒体服务器返回了无法识别的媒体库列表");
    }
    List<MediaServerDtos.LibraryView> result = new ArrayList<>();
    for (JsonNode item : items) {
      String id = text(item, "ItemId", text(item, "Id", null));
      String name = text(item, "Name", "未命名媒体库");
      if (id == null) {
        continue;
      }
      List<String> locations = new ArrayList<>();
      JsonNode locationNodes = item.path("Locations");
      if (locationNodes.isArray()) {
        locationNodes.forEach(node -> locations.add(node.asText()));
      }
      result.add(
          MediaServerDtos.LibraryView.builder()
              .id(id)
              .name(name)
              .collectionType(text(item, "CollectionType", null))
              .locations(locations)
              .build());
    }
    return result;
  }

  public MediaServerRefreshResult refreshAfterTask(
      TaskConfig task, boolean incremental, boolean hasChanges) {
    MediaRefreshScope scope = MediaRefreshScope.from(task.getMediaRefreshScope());
    if (scope == MediaRefreshScope.NONE || task.getMediaServerConfigId() == null) {
      return MediaServerRefreshResult.skipped("任务未配置媒体库刷新");
    }
    try {
      if (incremental && !hasChanges) {
        MediaServerConfig server = configService.getRequired(task.getMediaServerConfigId());
        return new MediaServerRefreshResult(
            MediaServerRefreshResult.Status.SKIPPED,
            server.getName(),
            server.getServerType(),
            scope.name(),
            task.getMediaLibraryName(),
            null,
            "增量任务没有媒体变化，未触发刷新");
      }
      return refresh(
          task.getMediaServerConfigId(),
          scope,
          task.getMediaLibraryId(),
          task.getMediaLibraryName());
    } catch (Exception e) {
      return new MediaServerRefreshResult(
          MediaServerRefreshResult.Status.FAILED,
          "配置 #" + task.getMediaServerConfigId(),
          null,
          scope.name(),
          task.getMediaLibraryName(),
          "MEDIA_SERVER_NOT_FOUND",
          rootMessage(e));
    }
  }

  public MediaServerRefreshResult refresh(
      Long configId, MediaRefreshScope scope, String libraryId, String libraryNameSnapshot) {
    MediaServerConfig config = configService.getRequired(configId);
    return refresh(config, scope, libraryId, libraryNameSnapshot);
  }

  MediaServerRefreshResult refresh(
      MediaServerConfig config,
      MediaRefreshScope scope,
      String libraryId,
      String libraryNameSnapshot) {
    if (!Boolean.TRUE.equals(config.getIsActive())) {
      return failed(config, scope, libraryNameSnapshot, "MEDIA_SERVER_DISABLED", "媒体服务器配置已停用");
    }
    try {
      if (scope == MediaRefreshScope.ALL) {
        request(config, HttpMethod.POST, "/Library/Refresh", null);
        return triggered(config, scope, null);
      }
      if (scope != MediaRefreshScope.LIBRARY || libraryId == null || libraryId.isBlank()) {
        throw new BusinessException("精确刷新必须指定媒体库");
      }
      MediaServerDtos.LibraryView library =
          listLibraries(config).stream()
              .filter(item -> libraryId.equals(item.getId()))
              .findFirst()
              .orElse(null);
      if (library == null) {
        return failed(
            config, scope, libraryNameSnapshot, "MEDIA_LIBRARY_NOT_FOUND", "已配置的媒体库不存在，未回退为全部刷新");
      }
      if (library.getLocations().isEmpty()) {
        return failed(
            config, scope, library.getName(), "MEDIA_LIBRARY_PATH_EMPTY", "媒体库没有可刷新的目录路径");
      }
      List<Map<String, String>> updates =
          library.getLocations().stream()
              .map(path -> Map.of("Path", path, "UpdateType", "Modified"))
              .toList();
      request(config, HttpMethod.POST, "/Library/Media/Updated", Map.of("Updates", updates));
      return triggered(config, scope, library.getName());
    } catch (Exception e) {
      return failed(
          config, scope, libraryNameSnapshot, "MEDIA_SERVER_REFRESH_FAILED", rootMessage(e));
    }
  }

  private MediaServerRefreshResult triggered(
      MediaServerConfig config, MediaRefreshScope scope, String libraryName) {
    return new MediaServerRefreshResult(
        MediaServerRefreshResult.Status.TRIGGERED,
        config.getName(),
        config.getServerType(),
        scope.name(),
        libraryName,
        null,
        "刷新请求已提交");
  }

  private MediaServerRefreshResult failed(
      MediaServerConfig config,
      MediaRefreshScope scope,
      String libraryName,
      String code,
      String message) {
    return new MediaServerRefreshResult(
        MediaServerRefreshResult.Status.FAILED,
        config.getName(),
        config.getServerType(),
        scope.name(),
        libraryName,
        code,
        message);
  }

  private JsonNode request(MediaServerConfig config, HttpMethod method, String path, Object body) {
    HttpHeaders headers = headers(config);
    if (body != null) {
      headers.setContentType(MediaType.APPLICATION_JSON);
    }
    HttpEntity<?> entity = new HttpEntity<>(body, headers);
    for (int attempt = 0; attempt < 2; attempt++) {
      try {
        ResponseEntity<String> response =
            restTemplate.exchange(config.getApiBaseUrl() + path, method, entity, String.class);
        String value = response.getBody();
        return value == null || value.isBlank()
            ? objectMapper.createObjectNode()
            : objectMapper.readTree(value);
      } catch (HttpStatusCodeException e) {
        if (e.getStatusCode().is5xxServerError() && attempt == 0) {
          continue;
        }
        throw new BusinessException("媒体服务器请求失败（HTTP " + e.getStatusCode().value() + "）", e);
      } catch (ResourceAccessException e) {
        if (attempt == 0) {
          continue;
        }
        throw new BusinessException("无法连接媒体服务器：" + rootMessage(e), e);
      } catch (Exception e) {
        throw e instanceof BusinessException business
            ? business
            : new BusinessException("解析媒体服务器响应失败：" + rootMessage(e), e);
      }
    }
    throw new BusinessException("媒体服务器请求失败");
  }

  private HttpHeaders headers(MediaServerConfig config) {
    HttpHeaders headers = new HttpHeaders();
    if (MediaServerType.from(config.getServerType()) == MediaServerType.EMBY) {
      headers.set("X-Emby-Token", config.getApiKey());
    } else {
      headers.set(
          HttpHeaders.AUTHORIZATION,
          "MediaBrowser Client=\"OStrm\", Device=\"Server\", DeviceId=\"ostrm-server\","
              + " Version=\"1.0\", Token=\""
              + config.getApiKey()
              + "\"");
    }
    headers.setAccept(List.of(MediaType.APPLICATION_JSON));
    return headers;
  }

  private String text(JsonNode node, String field, String fallback) {
    JsonNode value = node.get(field);
    return value == null || value.isNull() || value.asText().isBlank() ? fallback : value.asText();
  }

  private String rootMessage(Throwable error) {
    Throwable current = error;
    while (current.getCause() != null && current.getCause() != current) {
      current = current.getCause();
    }
    return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
  }

  private static RestTemplate createRestTemplate() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(5_000);
    factory.setReadTimeout(10_000);
    return new RestTemplate(factory);
  }
}
