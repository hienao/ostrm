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
import com.hienao.openlist2strm.dto.FrontendLogRequest;
import com.hienao.openlist2strm.service.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "日志管理", description = "系统日志相关接口")
public class LogController {

  private final LogService logService;

  @Operation(summary = "获取日志内容", description = "获取指定类型的日志内容（无需认证）")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误")
      })
  @GetMapping("/{logType}")
  public ApiResponse<List<String>> getLogs(
      @Parameter(description = "日志类型", example = "backend") @PathVariable String logType,
      @Parameter(description = "获取行数", example = "1000") @RequestParam(defaultValue = "1000")
          int lines) {
    try {
      List<String> logLines = logService.getLogLines(logType, lines);
      return ApiResponse.success(logLines);
    } catch (IllegalArgumentException e) {
      log.warn("获取日志失败，参数错误: {}", e.getMessage());
      return ApiResponse.error(400, e.getMessage());
    } catch (Exception e) {
      log.error("获取日志失败", e);
      return ApiResponse.error(500, "获取日志失败: " + e.getMessage());
    }
  }

  @Operation(summary = "下载日志文件", description = "下载指定类型的完整日志文件（无需认证）")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "下载成功",
            content = @Content(mediaType = "application/octet-stream")),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "404",
            description = "日志文件不存在"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误")
      })
  @GetMapping("/{logType}/download")
  public ResponseEntity<Resource> downloadLog(
      @Parameter(description = "日志类型", example = "backend") @PathVariable String logType) {
    try {
      Resource resource = logService.getLogFile(logType);

      if (resource == null || !resource.exists()) {
        return ResponseEntity.notFound().build();
      }

      String filename = logType + "-" + java.time.LocalDate.now() + ".log";

      return ResponseEntity.ok()
          .contentType(MediaType.APPLICATION_OCTET_STREAM)
          .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
          .body(resource);

    } catch (IllegalArgumentException e) {
      log.warn("下载日志失败，参数错误: {}", e.getMessage());
      return ResponseEntity.badRequest().build();
    } catch (Exception e) {
      log.error("下载日志失败", e);
      return ResponseEntity.internalServerError().build();
    }
  }

  @Operation(summary = "获取日志统计信息", description = "获取日志的统计信息（无需认证）")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "获取成功",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "参数错误"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误")
      })
  @GetMapping("/{logType}/stats")
  public ApiResponse<Object> getLogStats(
      @Parameter(description = "日志类型", example = "backend") @PathVariable String logType) {
    try {
      Object stats = logService.getLogStats(logType);
      return ApiResponse.success(stats);
    } catch (IllegalArgumentException e) {
      log.warn("获取日志统计失败，参数错误: {}", e.getMessage());
      return ApiResponse.error(400, e.getMessage());
    } catch (Exception e) {
      log.error("获取日志统计失败", e);
      return ApiResponse.error(500, "获取日志统计失败: " + e.getMessage());
    }
  }

  @Operation(summary = "接收前端日志", description = "接收前端发送的日志数据（无需认证）")
  @ApiResponses(
      value = {
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "接收成功",
            content = @Content(schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "请求数据无效"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "500",
            description = "服务器内部错误")
      })
  @PostMapping("/frontend")
  public ApiResponse<String> receiveFrontendLogs(
      @Parameter(description = "前端日志数据") @RequestBody FrontendLogRequest request) {
    try {
      logService.processFrontendLogs(request);
      return ApiResponse.success("日志接收成功");
    } catch (IllegalArgumentException e) {
      log.warn("接收前端日志失败，参数错误: {}", e.getMessage());
      return ApiResponse.error(400, e.getMessage());
    } catch (Exception e) {
      log.error("接收前端日志失败", e);
      return ApiResponse.error(500, "接收前端日志失败: " + e.getMessage());
    }
  }
}
