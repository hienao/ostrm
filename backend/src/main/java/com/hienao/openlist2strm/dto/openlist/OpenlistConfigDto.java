package com.hienao.openlist2strm.dto.openlist;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * openlist配置DTO
 *
 * @author hienao
 * @since 2024-01-01
 */
@Data
@Accessors(chain = true)
public class OpenlistConfigDto {

  /** 主键ID */
  private Long id;

  /** openlist网址 */
  @NotBlank(message = "openlist网址不能为空") @Pattern(regexp = "^https?://.*", message = "openlist网址格式不正确，必须以http://或https://开头") @Size(max = 500, message = "openlist网址长度不能超过500个字符")
  private String baseUrl;

  /** 用户令牌 */
  @NotBlank(message = "用户令牌不能为空") @Size(max = 1000, message = "用户令牌长度不能超过1000个字符") private String token;

  /** 初始路径 */
  @Size(max = 500, message = "初始路径长度不能超过500个字符") private String basePath;

  /** 用户名 */
  @NotBlank(message = "用户名不能为空") @Size(max = 200, message = "用户名长度不能超过200个字符") private String username;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 是否启用：true-启用，false-禁用 */
  private Boolean isActive;

  /** STRM文件生成时的baseUrl替换，可为空，为空时则不进行替换 */
  @Size(max = 500, message = "STRM Base URL长度不能超过500个字符") private String strmBaseUrl;

  /** 是否启用URL编码：true-启用（默认），false-禁用 */
  private Boolean enableUrlEncoding;

  /** OpenList 文件系统 API 每分钟最大调用次数：0-不限制 */
  @Min(value = 0, message = "API 每分钟调用次数不能小于0") @Max(value = 6000, message = "API 每分钟调用次数不能大于6000") private Integer fsApiQpmLimit;
}
