package com.hienao.openlist2strm.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/** Emby/Jellyfin 媒体服务器配置。 */
@Data
@Accessors(chain = true)
public class MediaServerConfig {
  private Long id;
  private String name;
  private String serverType;
  private String apiBaseUrl;
  private String apiKey;
  private Boolean isActive;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
}
