package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.dto.media.MediaServerDtos;
import com.hienao.openlist2strm.entity.MediaServerConfig;
import com.hienao.openlist2strm.entity.MediaServerType;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.mapper.MediaServerConfigMapper;
import com.hienao.openlist2strm.mapper.TaskConfigMapper;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** Emby/Jellyfin 服务器配置管理。 */
@Service
@RequiredArgsConstructor
public class MediaServerConfigService {

  private final MediaServerConfigMapper mapper;
  private final TaskConfigMapper taskConfigMapper;

  public List<MediaServerConfig> getAll() {
    return mapper.selectAll();
  }

  public MediaServerConfig getRequired(Long id) {
    MediaServerConfig config = id == null ? null : mapper.selectById(id);
    if (config == null) {
      throw new BusinessException("媒体服务器配置不存在");
    }
    return config;
  }

  @Transactional(rollbackFor = Exception.class)
  public MediaServerConfig create(MediaServerDtos.SaveRequest request) {
    MediaServerConfig config = fromRequest(request, null);
    if (!StringUtils.hasText(config.getApiKey())) {
      throw new BusinessException("API Key 不能为空");
    }
    ensureUniqueName(config.getName(), null);
    if (mapper.insert(config) <= 0) {
      throw new BusinessException("创建媒体服务器配置失败");
    }
    return mapper.selectById(config.getId());
  }

  @Transactional(rollbackFor = Exception.class)
  public MediaServerConfig update(Long id, MediaServerDtos.SaveRequest request) {
    MediaServerConfig existing = getRequired(id);
    MediaServerConfig config = fromRequest(request, existing);
    config.setId(id);
    ensureUniqueName(config.getName(), id);
    if (mapper.updateById(config) <= 0) {
      throw new BusinessException("更新媒体服务器配置失败");
    }
    return mapper.selectById(id);
  }

  @Transactional(rollbackFor = Exception.class)
  public void delete(Long id) {
    MediaServerConfig config = getRequired(id);
    List<TaskConfig> references = taskConfigMapper.selectByMediaServerConfigId(id);
    if (!references.isEmpty()) {
      String names =
          references.stream()
              .map(TaskConfig::getTaskName)
              .limit(5)
              .reduce((a, b) -> a + "、" + b)
              .orElse("");
      throw new BusinessException("该媒体服务器仍被任务引用，请先修改任务：" + names);
    }
    if (mapper.deleteById(config.getId()) <= 0) {
      throw new BusinessException("删除媒体服务器配置失败");
    }
  }

  public MediaServerDtos.View toView(MediaServerConfig config) {
    return MediaServerDtos.View.builder()
        .id(config.getId())
        .name(config.getName())
        .serverType(config.getServerType())
        .apiBaseUrl(config.getApiBaseUrl())
        .apiKeyConfigured(StringUtils.hasText(config.getApiKey()))
        .active(Boolean.TRUE.equals(config.getIsActive()))
        .createdAt(config.getCreatedAt())
        .updatedAt(config.getUpdatedAt())
        .build();
  }

  /** 构造仅用于连接测试、不会持久化的配置。 */
  public MediaServerConfig preview(MediaServerDtos.SaveRequest request) {
    MediaServerConfig config = fromRequest(request, null);
    if (!StringUtils.hasText(config.getApiKey())) {
      throw new BusinessException("API Key 不能为空");
    }
    return config;
  }

  private MediaServerConfig fromRequest(
      MediaServerDtos.SaveRequest request, MediaServerConfig existing) {
    if (request == null || !StringUtils.hasText(request.getName())) {
      throw new BusinessException("配置名称不能为空");
    }
    String type = MediaServerType.from(request.getServerType()).name();
    String url = normalizeUrl(request.getApiBaseUrl());
    String apiKey =
        StringUtils.hasText(request.getApiKey())
            ? request.getApiKey().trim()
            : existing == null ? null : existing.getApiKey();
    return new MediaServerConfig()
        .setName(request.getName().trim())
        .setServerType(type)
        .setApiBaseUrl(url)
        .setApiKey(apiKey)
        .setIsActive(request.getIsActive() == null || request.getIsActive());
  }

  private String normalizeUrl(String raw) {
    if (!StringUtils.hasText(raw)) {
      throw new BusinessException("API 根地址不能为空");
    }
    try {
      URI uri = URI.create(raw.trim());
      if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
          || uri.getHost() == null
          || uri.getUserInfo() != null
          || uri.getQuery() != null
          || uri.getFragment() != null) {
        throw new IllegalArgumentException();
      }
      String value = uri.toString();
      return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    } catch (IllegalArgumentException e) {
      throw new BusinessException("API 根地址必须是有效的 HTTP/HTTPS 地址，且不能包含认证信息、查询参数或片段");
    }
  }

  private void ensureUniqueName(String name, Long currentId) {
    MediaServerConfig duplicate = mapper.selectByName(name);
    if (duplicate != null && !duplicate.getId().equals(currentId)) {
      throw new BusinessException("媒体服务器配置名称已存在：" + name);
    }
  }
}
