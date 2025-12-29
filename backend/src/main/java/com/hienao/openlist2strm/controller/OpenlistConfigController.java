/*
 * OStrm - Stream Management System
 * Copyright (C) 2024 OStrm Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.hienao.openlist2strm.controller;

import com.hienao.openlist2strm.dto.ApiResponse;
import com.hienao.openlist2strm.dto.openlist.OpenlistConfigDto;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.service.OpenlistApiService;
import com.hienao.openlist2strm.service.OpenlistConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 配置管理控制器
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/openlist-config")
@RequiredArgsConstructor
@Tag(name = "OpenList配置管理", description = "OpenList配置的增删改查接口")
public class OpenlistConfigController {

  private static final String CONFIG_ID_PARAM = "配置ID";

  private final OpenlistConfigService openlistConfigService;
  private final OpenlistApiService openlistApiService;

  /** 查询所有配置 */
  @GetMapping
  @Operation(summary = "查询所有配置", description = "获取所有OpenList配置列表")
  public ResponseEntity<ApiResponse<List<OpenlistConfigDto>>> getAllConfigs() {
    List<OpenlistConfig> configs = openlistConfigService.getAllConfigs();
    List<OpenlistConfigDto> configDtos =
        configs.stream().map(this::convertToDto).collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(configDtos));
  }

  /** 查询启用的配置 */
  @GetMapping("/active")
  @Operation(summary = "查询启用的配置", description = "获取所有启用状态的OpenList配置")
  public ResponseEntity<ApiResponse<List<OpenlistConfigDto>>> getActiveConfigs() {
    List<OpenlistConfig> configs = openlistConfigService.getActiveConfigs();
    List<OpenlistConfigDto> configDtos =
        configs.stream().map(this::convertToDto).collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(configDtos));
  }

  /** 根据ID查询配置 */
  @GetMapping("/{id}")
  @Operation(summary = "根据ID查询配置", description = "根据配置ID获取OpenList配置详情")
  public ResponseEntity<ApiResponse<OpenlistConfigDto>> getConfigById(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id) {
    OpenlistConfig config = openlistConfigService.getById(id);
    if (config == null) {
      return ResponseEntity.ok(ApiResponse.error(404, "配置不存在"));
    }
    return ResponseEntity.ok(ApiResponse.success(convertToDto(config)));
  }

  /** 根据用户名查询配置 */
  @GetMapping("/username/{username}")
  @Operation(summary = "根据用户名查询配置", description = "根据用户名获取OpenList配置")
  public ResponseEntity<ApiResponse<OpenlistConfigDto>> getConfigByUsername(
      @Parameter(description = "用户名", required = true) @PathVariable String username) {
    OpenlistConfig config = openlistConfigService.getByUsername(username);
    if (config == null) {
      return ResponseEntity.ok(ApiResponse.error(404, "配置不存在"));
    }
    return ResponseEntity.ok(ApiResponse.success(convertToDto(config)));
  }

  /** 创建配置 */
  @PostMapping
  @Operation(summary = "创建配置", description = "创建新的OpenList配置")
  public ResponseEntity<ApiResponse<OpenlistConfigDto>> createConfig(
      @Parameter(description = "配置信息", required = true) @Valid @RequestBody
          OpenlistConfigDto configDto) {
    OpenlistConfig config = convertToEntity(configDto);
    OpenlistConfig createdConfig = openlistConfigService.createConfig(config);
    return ResponseEntity.ok(ApiResponse.success(convertToDto(createdConfig)));
  }

  /** 更新配置 */
  @PutMapping("/{id}")
  @Operation(summary = "更新配置", description = "更新指定ID的配置")
  public ResponseEntity<ApiResponse<OpenlistConfigDto>> updateConfig(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id,
      @Parameter(description = "配置信息", required = true) @Valid @RequestBody
          OpenlistConfigDto configDto) {
    configDto.setId(id);
    OpenlistConfig config = convertToEntity(configDto);
    OpenlistConfig updatedConfig = openlistConfigService.updateConfig(config);
    return ResponseEntity.ok(ApiResponse.success(convertToDto(updatedConfig)));
  }

  /** 删除配置 */
  @DeleteMapping("/{id}")
  @Operation(summary = "删除配置", description = "删除指定ID的配置")
  public ResponseEntity<ApiResponse<Void>> deleteConfig(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id) {
    openlistConfigService.deleteConfig(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 启用/禁用配置 */
  @PatchMapping("/{id}/status")
  @Operation(summary = "启用/禁用配置", description = "更新指定配置的启用状态")
  public ResponseEntity<ApiResponse<Void>> updateConfigStatus(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id,
      @Parameter(description = "状态更新请求", required = true) @RequestBody
          UpdateStatusRequest request) {
    openlistConfigService.updateActiveStatus(id, request.getIsActive());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 验证OpenList配置（用于前端保存配置前的验证，避免CORS问题） */
  @PostMapping("/validate")
  @Operation(summary = "验证OpenList配置", description = "验证OpenList的baseUrl和token是否有效")
  public ResponseEntity<ApiResponse<ValidateConfigResponse>> validateConfig(
      @Parameter(description = "验证配置请求", required = true) @RequestBody
          ValidateConfigRequest request) {
    try {
      OpenlistApiService.ValidateConfigResult result =
          openlistApiService.validateConfig(request.getBaseUrl(), request.getToken());
      
      ValidateConfigResponse response = new ValidateConfigResponse();
      response.setUsername(result.getUsername());
      response.setBasePath(result.getBasePath());
      
      return ResponseEntity.ok(ApiResponse.success(response));
    } catch (Exception e) {
      log.error("验证OpenList配置失败: {}", e.getMessage());
      return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
    }
  }

  /** 验证任务路径（用于前端创建任务时验证路径是否存在，避免CORS问题） */
  @PostMapping("/validate-path")
  @Operation(summary = "验证任务路径", description = "验证指定的任务路径在OpenList中是否存在且是目录")
  public ResponseEntity<ApiResponse<Void>> validatePath(
      @Parameter(description = "验证路径请求", required = true) @RequestBody
          ValidatePathRequest request) {
    try {
      openlistApiService.validatePath(
          request.getBaseUrl(),
          request.getToken(),
          request.getBasePath(),
          request.getTaskPath());
      return ResponseEntity.ok(ApiResponse.success(null));
    } catch (Exception e) {
      log.error("验证任务路径失败: {}", e.getMessage());
      return ResponseEntity.ok(ApiResponse.error(400, e.getMessage()));
    }
  }

  /** 状态更新请求DTO */
  public static class UpdateStatusRequest {
    private Boolean isActive;

    public Boolean getIsActive() {
      return isActive;
    }

    public void setIsActive(Boolean isActive) {
      this.isActive = isActive;
    }
  }

  /** 验证配置请求DTO */
  public static class ValidateConfigRequest {
    private String baseUrl;
    private String token;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }
  }

  /** 验证配置响应DTO */
  public static class ValidateConfigResponse {
    private String username;
    private String basePath;

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(String basePath) {
      this.basePath = basePath;
    }
  }

  /** 验证路径请求DTO */
  public static class ValidatePathRequest {
    private String baseUrl;
    private String token;
    private String basePath;
    private String taskPath;

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getToken() {
      return token;
    }

    public void setToken(String token) {
      this.token = token;
    }

    public String getBasePath() {
      return basePath;
    }

    public void setBasePath(String basePath) {
      this.basePath = basePath;
    }

    public String getTaskPath() {
      return taskPath;
    }

    public void setTaskPath(String taskPath) {
      this.taskPath = taskPath;
    }
  }

  /** 实体转DTO */
  private OpenlistConfigDto convertToDto(OpenlistConfig config) {
    OpenlistConfigDto dto = new OpenlistConfigDto();
    BeanUtils.copyProperties(config, dto);
    return dto;
  }

  /** DTO转实体 */
  private OpenlistConfig convertToEntity(OpenlistConfigDto dto) {
    OpenlistConfig config = new OpenlistConfig();
    BeanUtils.copyProperties(dto, config);
    return config;
  }
}
