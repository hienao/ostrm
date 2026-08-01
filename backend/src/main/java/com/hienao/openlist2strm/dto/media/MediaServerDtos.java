package com.hienao.openlist2strm.dto.media;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.Builder;
import lombok.Data;

/** 媒体服务器配置、媒体库与连接测试 DTO。 */
public final class MediaServerDtos {

  private MediaServerDtos() {}

  @Data
  public static class SaveRequest {
    @NotBlank @Size(max = 200) private String name;

    @NotBlank @Pattern(regexp = "(?i)^(EMBY|JELLYFIN)$", message = "类型必须是 EMBY 或 JELLYFIN")
    private String serverType;

    @NotBlank @Size(max = 500) private String apiBaseUrl;

    @Size(max = 1000) private String apiKey;

    private Boolean isActive;
  }

  @Data
  @Builder
  public static class View {
    private Long id;
    private String name;
    private String serverType;
    private String apiBaseUrl;
    private boolean apiKeyConfigured;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
  }

  @Data
  @Builder
  public static class ConnectionResult {
    private String serverName;
    private String version;
    private String productName;
    private int libraryCount;
  }

  @Data
  @Builder
  public static class LibraryView {
    private String id;
    private String name;
    private String collectionType;
    @Builder.Default private List<String> locations = new ArrayList<>();
  }

  @Data
  public static class RefreshRequest {
    private String scope;
    private String libraryId;
  }
}
