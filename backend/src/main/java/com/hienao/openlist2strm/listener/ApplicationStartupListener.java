package com.hienao.openlist2strm.listener;

import com.hienao.openlist2strm.config.PathConfiguration;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.job.LogCleanupJob;
import com.hienao.openlist2strm.service.DataReportService;
import com.hienao.openlist2strm.service.LogConfigService;
import com.hienao.openlist2strm.service.QuartzSchedulerService;
import com.hienao.openlist2strm.service.SystemConfigService;
import com.hienao.openlist2strm.service.TaskConfigService;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.TriggerBuilder;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * 应用启动监听器 在应用启动完成后，自动加载所有定时任务到Quartz调度器
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApplicationStartupListener implements ApplicationListener<ApplicationReadyEvent> {

  private final TaskConfigService taskConfigService;
  private final QuartzSchedulerService quartzSchedulerService;
  private final LogConfigService logConfigService;
  private final SystemConfigService systemConfigService;
  private final DataReportService dataReportService;
  private final PathConfiguration pathConfiguration;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    log.info("应用启动完成，开始初始化系统配置...");

    try {
      // 1. 应用日志级别配置
      logConfigService.applyLogLevelConfig();

      // 2. 执行启动时日志清理
      executeStartupLogCleanup();

      // 3. 注册日志清理定时任务
      registerLogCleanupTask();

      // 4. 查询所有有定时任务表达式的任务配置
      List<TaskConfig> scheduledConfigs = taskConfigService.getScheduledConfigs();

      if (scheduledConfigs.isEmpty()) {
        log.info("没有找到需要调度的任务配置");
      } else {
        log.info("找到 {} 个需要调度的任务配置", scheduledConfigs.size());
        // 初始化所有定时任务
        quartzSchedulerService.initializeScheduledTasks(scheduledConfigs);
      }

      log.info("系统初始化完成");

    } catch (Exception e) {
      log.error("系统初始化失败: {}", e.getMessage(), e);
    }
  }

  /** 注册日志清理定时任务 */
  private void registerLogCleanupTask() {
    try {
      // 获取Scheduler实例
      Scheduler scheduler = quartzSchedulerService.getScheduler();

      // 每天凌晨1:30执行日志清理任务
      String cronExpression = "0 30 1 * * ?";
      String jobName = "LogCleanupJob";
      String jobGroup = "SYSTEM";

      // 创建JobDetail
      JobDetail jobDetail =
          JobBuilder.newJob(LogCleanupJob.class)
              .withIdentity(jobName, jobGroup)
              .withDescription("日志清理定时任务")
              .storeDurably(true)
              .build();

      // 创建CronTrigger
      CronTrigger trigger =
          TriggerBuilder.newTrigger()
              .withIdentity(jobName + "Trigger", jobGroup)
              .withDescription("日志清理触发器")
              .withSchedule(CronScheduleBuilder.cronSchedule(cronExpression))
              .forJob(jobDetail)
              .build();

      // 调度任务
      scheduler.scheduleJob(jobDetail, trigger);

      log.info("日志清理定时任务注册成功，执行时间: 每天凌晨1:30");

    } catch (Exception e) {
      log.error("注册日志清理定时任务失败: {}", e.getMessage(), e);
    }
  }

  /** 执行启动时日志清理 在应用启动时执行一次日志清理，清理过期的日志文件 */
  private void executeStartupLogCleanup() {
    try {
      log.info("开始执行启动时日志清理");

      // 获取日志配置
      Map<String, Object> logConfig = systemConfigService.getLogConfig();
      Integer retentionDays = (Integer) logConfig.getOrDefault("retentionDays", 7);

      log.info("启动时日志清理 - 保留天数: {} 天", retentionDays);

      // 获取日志目录路径
      String backendLogDir = pathConfiguration.getLogs();
      String frontendLogDir = pathConfiguration.getFrontendLogs();

      log.info("清理后端日志目录: {}", backendLogDir);
      log.info("清理前端日志目录: {}", frontendLogDir);

      // 清理后端日志
      cleanupLogDirectory(backendLogDir, retentionDays);

      // 清理前端日志
      cleanupLogDirectory(frontendLogDir, retentionDays);

      log.info("启动时日志清理执行完成");

      // 上报应用使用事件（与LogCleanupJob保持一致）
      try {
        dataReportService.reportEvent("app_use", new HashMap<>());
        log.debug("上报启动时日志清理使用事件成功");
      } catch (Exception reportException) {
        log.warn("上报启动时日志清理使用事件失败，错误: {}", reportException.getMessage());
        // 不影响主要业务流程，仅记录警告日志
      }

    } catch (Exception e) {
      log.error("启动时日志清理执行失败: {}", e.getMessage(), e);
      // 启动时日志清理失败不应该影响应用启动，只记录错误日志
    }
  }

  /**
   * 清理指定目录下的过期日志文件
   *
   * @param logDirPath 日志目录路径
   * @param retentionDays 保留天数
   */
  private void cleanupLogDirectory(String logDirPath, int retentionDays) {
    try {
      Path logDir = Paths.get(logDirPath);

      // 检查日志目录是否存在
      if (!Files.exists(logDir) || !Files.isDirectory(logDir)) {
        log.info("日志目录不存在或不是目录: {}", logDirPath);
        return;
      }

      // 计算过期时间
      LocalDateTime cutoffTime = LocalDateTime.now().minusDays(retentionDays);
      long cutoffMillis = cutoffTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

      log.info("清理目录: {}, 删除 {} 之前的日志文件", logDirPath, cutoffTime);

      // 遍历日志目录
      File[] logFiles = logDir.toFile().listFiles();
      if (logFiles == null) {
        log.info("日志目录为空: {}", logDirPath);
        return;
      }

      log.info("在目录 {} 中找到 {} 个文件", logDirPath, logFiles.length);
      for (File file : logFiles) {
        log.debug("文件: {}, 是目录: {}, 是文件: {}", file.getName(), file.isDirectory(), file.isFile());
      }

      int deletedCount = 0;
      long deletedSize = 0;

      for (File logFile : logFiles) {
        if (logFile.isFile()) {
          log.debug("检查文件: {}, 是否为日志文件: {}", logFile.getName(), isLogFile(logFile.getName()));
          if (isLogFile(logFile.getName())) {
            if (isActiveLogFile(logFile.getName())) {
              continue;
            }
            long fileModifiedTime = logFile.lastModified();
            LocalDateTime fileModifiedDateTime =
                LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(fileModifiedTime), ZoneId.systemDefault());
            log.debug(
                "日志文件: {}, 最后修改时间: {}, 是否过期: {}",
                logFile.getName(),
                fileModifiedDateTime,
                fileModifiedTime < cutoffMillis);

            // 检查文件最后修改时间
            if (fileModifiedTime < cutoffMillis) {
              long fileSize = logFile.length();
              if (logFile.delete()) {
                deletedCount++;
                deletedSize += fileSize;
                log.info(
                    "删除过期日志文件: {} (大小: {} bytes, 修改时间: {})",
                    logFile.getAbsolutePath(),
                    fileSize,
                    fileModifiedDateTime);
              } else {
                log.warn("删除日志文件失败: {}", logFile.getAbsolutePath());
              }
            }
          }
        }
      }

      if (deletedCount > 0) {
        log.info("目录 {} 清理完成，删除 {} 个文件，释放空间 {} KB", logDirPath, deletedCount, deletedSize / 1024);
      } else {
        log.info("目录 {} 没有需要清理的过期日志文件", logDirPath);
      }

    } catch (Exception e) {
      log.error("清理日志目录失败: {}, 错误: {}", logDirPath, e.getMessage(), e);
    }
  }

  /**
   * 判断是否为日志文件
   *
   * @param fileName 文件名
   * @return 是否为日志文件
   */
  private boolean isLogFile(String fileName) {
    if (fileName == null || fileName.trim().isEmpty()) {
      return false;
    }

    String lowerFileName = fileName.toLowerCase();

    // 检查常见的日志文件扩展名
    return lowerFileName.endsWith(".log")
        || lowerFileName.endsWith(".log.gz")
        || lowerFileName.endsWith(".log.zip")
        || lowerFileName.contains(".log.")
        || (lowerFileName.startsWith("application") && lowerFileName.contains(".log"))
        || (lowerFileName.startsWith("spring") && lowerFileName.contains(".log"))
        || (lowerFileName.startsWith("error") && lowerFileName.contains(".log"))
        || (lowerFileName.startsWith("access") && lowerFileName.contains(".log"))
        || (lowerFileName.startsWith("backend") && lowerFileName.contains(".log"))
        || (lowerFileName.startsWith("frontend") && lowerFileName.contains(".log"));
  }

  private boolean isActiveLogFile(String fileName) {
    String lowerFileName = fileName.toLowerCase();
    return lowerFileName.equals("backend.log")
        || lowerFileName.equals("error.log")
        || lowerFileName.equals("frontend.log");
  }
}
