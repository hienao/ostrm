package com.hienao.openlist2strm.controller;

import com.hienao.openlist2strm.dto.ApiResponse;
import com.hienao.openlist2strm.dto.media.MediaServerDtos;
import com.hienao.openlist2strm.entity.MediaRefreshScope;
import com.hienao.openlist2strm.service.MediaServerApiService;
import com.hienao.openlist2strm.service.MediaServerConfigService;
import com.hienao.openlist2strm.service.MediaServerRefreshResult;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Emby/Jellyfin 配置与刷新接口。 */
@RestController
@RequestMapping("/api/media-servers")
@RequiredArgsConstructor
public class MediaServerController {

  private final MediaServerConfigService configService;
  private final MediaServerApiService apiService;

  @GetMapping
  public ResponseEntity<ApiResponse<List<MediaServerDtos.View>>> list() {
    return ResponseEntity.ok(
        ApiResponse.success(configService.getAll().stream().map(configService::toView).toList()));
  }

  @PostMapping
  public ResponseEntity<ApiResponse<MediaServerDtos.View>> create(
      @Valid @RequestBody MediaServerDtos.SaveRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(configService.toView(configService.create(request))));
  }

  @PutMapping("/{id}")
  public ResponseEntity<ApiResponse<MediaServerDtos.View>> update(
      @PathVariable Long id, @Valid @RequestBody MediaServerDtos.SaveRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(configService.toView(configService.update(id, request))));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
    configService.delete(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  @PostMapping("/test")
  public ResponseEntity<ApiResponse<MediaServerDtos.ConnectionResult>> testPreview(
      @Valid @RequestBody MediaServerDtos.SaveRequest request) {
    return ResponseEntity.ok(ApiResponse.success(apiService.test(configService.preview(request))));
  }

  @PostMapping("/{id}/test")
  public ResponseEntity<ApiResponse<MediaServerDtos.ConnectionResult>> testSaved(
      @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(apiService.test(configService.getRequired(id))));
  }

  @GetMapping("/{id}/libraries")
  public ResponseEntity<ApiResponse<List<MediaServerDtos.LibraryView>>> libraries(
      @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(apiService.listLibraries(id)));
  }

  @PostMapping("/{id}/refresh")
  public ResponseEntity<ApiResponse<MediaServerRefreshResult>> refresh(
      @PathVariable Long id, @RequestBody MediaServerDtos.RefreshRequest request) {
    MediaRefreshScope scope = MediaRefreshScope.from(request.getScope());
    MediaServerRefreshResult result = apiService.refresh(id, scope, request.getLibraryId(), null);
    return ResponseEntity.ok(ApiResponse.success(result));
  }
}
