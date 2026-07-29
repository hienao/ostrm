package com.hienao.openlist2strm.entity;

import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 任务配置信息实体类
 *
 * @author hienao
 * @since 2024-01-01
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
public class TaskConfig {

  /** 主键ID */
  private Long id;

  /** 任务名称 */
  private String taskName;

  /** 任务路径 */
  private String path;

  /** 关联的openlist_config表ID */
  private Long openlistConfigId;

  /** 是否需要刮削：true-是，false-否 */
  private Boolean needScrap;

  /** 重命名正则表达式，为空时表示不需要重命名 */
  private String renameRegex;

  /** 定时任务表达式 */
  private String cron;

  /** 是否是增量更新：true-是，false-否 */
  private Boolean isIncrement;

  /** 生成strm的基础路径 */
  private String strmPath;

  /** 上次执行的时间戳 */
  private Long lastExecTime;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新时间 */
  private LocalDateTime updatedAt;

  /** 是否启用：true-启用，false-禁用 */
  private Boolean isActive;
}
