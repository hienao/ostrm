package com.hienao.openlist2strm.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * openlist配置信息实体类
 *
 * @author hienao
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class OpenlistConfig {

  /** 主键ID */
  private Long id;

  /** openlist网址 */
  private String baseUrl;

  /** 用户令牌 */
  private String token;

  /** 初始路径 */
  private String basePath;

  /** 用户名 */
  private String username;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 是否启用：1-启用，0-禁用 */
  private Boolean isActive;

  /** STRM文件生成时的baseUrl替换，可为空，为空时则不进行替换 */
  private String strmBaseUrl;

  /** 是否启用URL编码：1-启用（默认），0-禁用 */
  private Boolean enableUrlEncoding;

  /** OpenList 文件系统 API 每分钟最大调用次数：0-不限制 */
  private Integer fsApiQpmLimit;

  /** OpenList 文件系统 API 每秒最大调用次数：0-不限制 */
  private Integer fsApiQpsLimit;
}
