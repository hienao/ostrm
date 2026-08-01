package com.hienao.openlist2strm.job;

import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.service.TaskConfigService;
import com.hienao.openlist2strm.service.TaskExecutionService;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 任务配置执行Job
 *
 * @author hienao
 * @since 2024-01-01
 */
@Component
@Slf4j
public class TaskConfigJob implements Job {

  @Autowired private TaskConfigService taskConfigService;
  @Autowired private TaskExecutionService taskExecutionService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    Map<String, Object> dataMap = context.getJobDetail().getJobDataMap();
    Long taskConfigId = (Long) dataMap.get("taskConfigId");

    try {
      log.info("开始执行定时任务，任务配置ID: {}", taskConfigId);

      // 获取任务配置
      TaskConfig taskConfig = taskConfigService.getById(taskConfigId);
      if (taskConfig == null) {
        log.warn("任务配置不存在，ID: {}", taskConfigId);
        return;
      }

      // 检查任务是否启用
      if (!taskConfig.getIsActive()) {
        log.info("任务已禁用，跳过执行，任务名称: {}", taskConfig.getTaskName());
        return;
      }

      // 执行任务
      taskExecutionService.submitScheduledTask(taskConfig.getId(), taskConfig.getIsIncrement());
      log.info("定时任务已提交，任务名称: {}", taskConfig.getTaskName());

    } catch (Exception e) {
      log.error("定时任务执行失败，任务配置ID: {}, 错误信息: {}", taskConfigId, e.getMessage(), e);
      throw new JobExecutionException(e);
    }
  }
}
