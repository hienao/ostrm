package com.hienao.openlist2strm.mapper;

import com.hienao.openlist2strm.entity.TaskConfig;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 任务配置信息Mapper接口
 *
 * @author hienao
 * @since 2024-01-01
 */
@Mapper
public interface TaskConfigMapper {

  /**
   * 根据ID查询任务配置
   *
   * @param id 主键ID
   * @return 任务配置信息
   */
  TaskConfig selectById(@Param("id") Long id);

  /**
   * 根据任务名称查询配置
   *
   * @param taskName 任务名称
   * @return 任务配置信息
   */
  TaskConfig selectByTaskName(@Param("taskName") String taskName);

  /**
   * 根据路径查询配置
   *
   * @param path 任务路径
   * @return 任务配置信息
   */
  TaskConfig selectByPath(@Param("path") String path);

  /**
   * 查询所有启用的任务配置
   *
   * @return 任务配置列表
   */
  List<TaskConfig> selectActiveConfigs();

  /**
   * 查询所有任务配置
   *
   * @return 任务配置列表
   */
  List<TaskConfig> selectAll();

  /**
   * 查询有定时任务的配置
   *
   * @return 任务配置列表
   */
  List<TaskConfig> selectWithCron();

  /** 查询引用指定媒体服务器的任务。 */
  List<TaskConfig> selectByMediaServerConfigId(
      @Param("mediaServerConfigId") Long mediaServerConfigId);

  /**
   * 插入任务配置
   *
   * @param taskConfig 任务配置信息
   * @return 影响行数
   */
  int insert(TaskConfig taskConfig);

  /**
   * 更新任务配置
   *
   * @param taskConfig 任务配置信息
   * @return 影响行数
   */
  int updateById(TaskConfig taskConfig);

  /**
   * 根据ID删除任务配置
   *
   * @param id 主键ID
   * @return 影响行数
   */
  int deleteById(@Param("id") Long id);

  /**
   * 启用/禁用任务配置
   *
   * @param id 主键ID
   * @param isActive 是否启用
   * @return 影响行数
   */
  int updateActiveStatus(@Param("id") Long id, @Param("isActive") Boolean isActive);

  /**
   * 更新最后执行时间
   *
   * @param id 主键ID
   * @param lastExecTime 最后执行时间戳
   * @return 影响行数
   */
  int updateLastExecTime(@Param("id") Long id, @Param("lastExecTime") Long lastExecTime);
}
