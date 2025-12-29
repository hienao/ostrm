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

package com.hienao.openlist2strm.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;

@Data
@Schema(description = "前端日志请求")
public class FrontendLogRequest {

  @Schema(description = "日志条目列表", required = true)
  private List<LogEntry> logs;

  @Data
  @Schema(description = "日志条目")
  public static class LogEntry {

    @Schema(description = "日志级别", example = "info", required = true)
    private String level;

    @Schema(description = "日志消息", example = "用户登录成功", required = true)
    private String message;

    @Schema(description = "时间戳", example = "1640995200000", required = true)
    private Long timestamp;

    @Schema(description = "用户代理", example = "Mozilla/5.0...")
    private String userAgent;

    @Schema(description = "页面URL", example = "/dashboard")
    private String url;

    @Schema(description = "用户ID", example = "user123")
    private String userId;

    @Schema(description = "会话ID", example = "session456")
    private String sessionId;

    @Schema(description = "额外数据")
    private Object extra;
  }
}
