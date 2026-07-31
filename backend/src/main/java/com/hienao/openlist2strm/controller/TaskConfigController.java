package com.hienao.openlist2strm.controller;

import com.hienao.openlist2strm.dto.ApiResponse;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryNode;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryTree;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteRequest;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.JobView;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.Preview;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.PreviewRequest;
import com.hienao.openlist2strm.dto.task.TaskConfigDto;
import com.hienao.openlist2strm.dto.task.TaskStructureCheckOverview;
import com.hienao.openlist2strm.dto.task.TaskStructureCheckResult;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.service.ManualScrapingJobService;
import com.hienao.openlist2strm.service.ManualScrapingService;
import com.hienao.openlist2strm.service.TaskConfigService;
import com.hienao.openlist2strm.service.TaskExecutionService;
import com.hienao.openlist2strm.service.TaskStructureCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

/**
 * 任务配置管理控制器
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@RestController
@RequestMapping("/api/task-config")
@RequiredArgsConstructor
@Tag(name = "任务配置管理", description = "任务配置的增删改查接口")
public class TaskConfigController {

  private static final String CONFIG_ID_PARAM = "配置ID";

  private final TaskConfigService taskConfigService;
  private final TaskExecutionService taskExecutionService;
  private final TaskStructureCheckService taskStructureCheckService;
  private final ManualScrapingService manualScrapingService;
  private final ManualScrapingJobService manualScrapingJobService;

  /** 查询所有配置 */
  @GetMapping
  @Operation(summary = "查询所有配置", description = "获取所有任务配置列表")
  public ResponseEntity<ApiResponse<List<TaskConfigDto>>> getAllConfigs() {
    List<TaskConfig> configs = taskConfigService.getAllConfigs();
    List<TaskConfigDto> configDtos =
        configs.stream().map(this::convertToDto).collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(configDtos));
  }

  /** 查询启用的配置 */
  @GetMapping("/active")
  @Operation(summary = "查询启用的配置", description = "获取所有启用状态的任务配置")
  public ResponseEntity<ApiResponse<List<TaskConfigDto>>> getActiveConfigs() {
    List<TaskConfig> configs = taskConfigService.getActiveConfigs();
    List<TaskConfigDto> configDtos =
        configs.stream().map(this::convertToDto).collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(configDtos));
  }

  /** 查询有定时任务的配置 */
  @GetMapping("/scheduled")
  @Operation(summary = "查询有定时任务的配置", description = "获取所有配置了定时任务的任务配置")
  public ResponseEntity<ApiResponse<List<TaskConfigDto>>> getScheduledConfigs() {
    List<TaskConfig> configs = taskConfigService.getScheduledConfigs();
    List<TaskConfigDto> configDtos =
        configs.stream().map(this::convertToDto).collect(Collectors.toList());
    return ResponseEntity.ok(ApiResponse.success(configDtos));
  }

  /** 根据ID查询配置 */
  @GetMapping("/{id}")
  @Operation(summary = "根据ID查询配置", description = "根据配置ID获取任务配置详情")
  public ResponseEntity<ApiResponse<TaskConfigDto>> getConfigById(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id) {
    TaskConfig config = taskConfigService.getById(id);
    if (config == null) {
      return ResponseEntity.ok(ApiResponse.error(404, "配置不存在"));
    }
    return ResponseEntity.ok(ApiResponse.success(convertToDto(config)));
  }

  /** 根据任务名称查询配置 */
  @GetMapping("/task-name/{taskName}")
  @Operation(summary = "根据任务名称查询配置", description = "根据任务名称获取任务配置")
  public ResponseEntity<ApiResponse<TaskConfigDto>> getConfigByTaskName(
      @Parameter(description = "任务名称", required = true) @PathVariable String taskName) {
    TaskConfig config = taskConfigService.getByTaskName(taskName);
    if (config == null) {
      return ResponseEntity.ok(ApiResponse.error(404, "配置不存在"));
    }
    return ResponseEntity.ok(ApiResponse.success(convertToDto(config)));
  }

  /** 根据路径查询配置 */
  @GetMapping("/path")
  @Operation(summary = "根据路径查询配置", description = "根据路径获取任务配置")
  public ResponseEntity<ApiResponse<TaskConfigDto>> getConfigByPath(
      @Parameter(description = "任务路径", required = true) @RequestParam String path) {
    TaskConfig config = taskConfigService.getByPath(path);
    if (config == null) {
      return ResponseEntity.ok(ApiResponse.error(404, "配置不存在"));
    }
    return ResponseEntity.ok(ApiResponse.success(convertToDto(config)));
  }

  /** 创建配置 */
  @PostMapping
  @Operation(summary = "创建配置", description = "创建新的任务配置")
  public ResponseEntity<ApiResponse<TaskConfigDto>> createConfig(
      @Valid @RequestBody TaskConfigDto configDto) {
    // 转换Cron表达式格式
    if (StringUtils.hasText(configDto.getCron())) {
      String convertedCron = convertToQuartzFormat(configDto.getCron());
      if (convertedCron != null) {
        configDto.setCron(convertedCron);
      }
    }
    TaskConfig config = convertToEntity(configDto);
    TaskConfig createdConfig = taskConfigService.createConfig(config);
    return ResponseEntity.ok(ApiResponse.success(convertToDto(createdConfig)));
  }

  /** 更新配置 */
  @PutMapping("/{id}")
  @Operation(summary = "更新配置", description = "更新指定ID的任务配置")
  public ResponseEntity<ApiResponse<TaskConfigDto>> updateConfig(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id,
      @Valid @RequestBody TaskConfigDto configDto) {
    configDto.setId(id);
    // 转换Cron表达式格式
    if (StringUtils.hasText(configDto.getCron())) {
      String convertedCron = convertToQuartzFormat(configDto.getCron());
      if (convertedCron != null) {
        configDto.setCron(convertedCron);
      }
    }
    TaskConfig config = convertToEntity(configDto);
    TaskConfig updatedConfig = taskConfigService.updateConfig(config);
    return ResponseEntity.ok(ApiResponse.success(convertToDto(updatedConfig)));
  }

  /** 删除配置 */
  @DeleteMapping("/{id}")
  @Operation(summary = "删除配置", description = "删除指定ID的任务配置")
  public ResponseEntity<ApiResponse<Void>> deleteConfig(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id) {
    taskConfigService.deleteById(id);
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 更新配置启用状态 */
  @PatchMapping("/{id}/status")
  @Operation(summary = "更新配置启用状态", description = "启用或禁用指定ID的任务配置")
  public ResponseEntity<ApiResponse<Void>> updateConfigStatus(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id,
      @RequestBody UpdateStatusRequest request) {
    taskConfigService.updateActiveStatus(id, request.getIsActive());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 更新最后执行时间 */
  @PatchMapping("/{id}/last-exec-time")
  @Operation(summary = "更新最后执行时间", description = "更新指定ID任务配置的最后执行时间")
  public ResponseEntity<ApiResponse<Void>> updateLastExecTime(
      @Parameter(description = CONFIG_ID_PARAM, required = true) @PathVariable Long id,
      @RequestBody UpdateLastExecTimeRequest request) {
    taskConfigService.updateLastExecTime(id, request.getLastExecTime());
    return ResponseEntity.ok(ApiResponse.success(null));
  }

  /** 提交任务执行 */
  @PostMapping("/{id}/submit")
  @Operation(summary = "提交任务执行", description = "将指定ID的任务提交到线程池中执行")
  public ResponseEntity<ApiResponse<String>> submitTask(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @RequestBody(required = false) TaskSubmitRequest request) {
    Boolean isIncremental = request != null ? request.getIsIncremental() : null;
    manualScrapingJobService.assertNoActiveJob(id);
    taskExecutionService.submitTask(id, isIncremental);
    return ResponseEntity.ok(ApiResponse.success("任务已提交执行"));
  }

  /** 检查任务目录结构 */
  @PostMapping("/{id}/structure-check")
  @Operation(summary = "检查任务目录结构", description = "递归扫描任务目录，并以文件树返回目录层级不符合要求的视频文件")
  public ResponseEntity<ApiResponse<TaskStructureCheckResult>> checkTaskStructure(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(taskStructureCheckService.check(id)));
  }

  /** 获取目录结构检查的第一层目录 */
  @GetMapping("/{id}/structure-check/directories")
  @Operation(summary = "获取待检查目录", description = "只读取任务根目录，并返回第一层子目录和根目录文件检查结果")
  public ResponseEntity<ApiResponse<TaskStructureCheckOverview>> getStructureCheckDirectories(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(taskStructureCheckService.getOverview(id)));
  }

  /** 检查一个第一层子目录 */
  @PostMapping("/{id}/structure-check/directory")
  @Operation(summary = "检查单个第一层目录", description = "递归检查指定第一层子目录，仅返回该目录的异常文件树")
  public ResponseEntity<ApiResponse<TaskStructureCheckResult>> checkStructureDirectory(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @Valid @RequestBody StructureDirectoryCheckRequest request) {
    return ResponseEntity.ok(
        ApiResponse.success(
            taskStructureCheckService.checkDirectory(id, request.getDirectoryPath())));
  }

  /** 获取手动刮削目录树 */
  @GetMapping("/{id}/manual-scraping/tree")
  @Operation(summary = "获取手动刮削目录树", description = "只读取任务根目录的直接子目录和本层媒体文件数量")
  public ResponseEntity<ApiResponse<DirectoryTree>> getManualScrapingTree(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(manualScrapingService.getDirectoryTree(id)));
  }

  /** 按需获取手动刮削目录的下一层 */
  @GetMapping("/{id}/manual-scraping/tree/children")
  @Operation(summary = "加载手动刮削子目录", description = "只读取指定目录的直接子目录和本层媒体文件数量")
  public ResponseEntity<ApiResponse<DirectoryNode>> getManualScrapingChildren(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @Parameter(description = "待展开目录路径", required = true) @RequestParam String directoryPath) {
    return ResponseEntity.ok(
        ApiResponse.success(manualScrapingService.getDirectoryChildren(id, directoryPath)));
  }

  /** 识别所选目录并返回刮削预览 */
  @PostMapping("/{id}/manual-scraping/preview")
  @Operation(summary = "预览手动刮削", description = "识别所选媒体目录并返回TMDB信息、重命名和上传文件预览")
  public ResponseEntity<ApiResponse<Preview>> previewManualScraping(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @Valid @RequestBody PreviewRequest request) {
    return ResponseEntity.ok(ApiResponse.success(manualScrapingService.preview(id, request)));
  }

  /** 提交手动刮削异步作业 */
  @PostMapping("/{id}/manual-scraping/execute")
  @Operation(summary = "提交手动刮削", description = "立即返回作业ID，后台分阶段执行重命名、生成和上传")
  public ResponseEntity<ApiResponse<JobView>> executeManualScraping(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @Valid @RequestBody ExecuteRequest request) {
    return ResponseEntity.ok(ApiResponse.success(manualScrapingJobService.submit(id, request)));
  }

  /** 查询最近一次手动刮削作业 */
  @GetMapping("/{id}/manual-scraping/jobs/latest")
  @Operation(summary = "查询最近手动刮削作业", description = "用于页面恢复当前进度或最近一次执行结果")
  public ResponseEntity<ApiResponse<JobView>> getLatestManualScrapingJob(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id) {
    return ResponseEntity.ok(ApiResponse.success(manualScrapingJobService.getLatest(id)));
  }

  /** 查询手动刮削作业状态 */
  @GetMapping("/{id}/manual-scraping/jobs/{jobId}")
  @Operation(summary = "查询手动刮削进度", description = "返回作业当前阶段、进度和错误信息")
  public ResponseEntity<ApiResponse<JobView>> getManualScrapingJob(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @Parameter(description = "手动刮削作业ID", required = true) @PathVariable Long jobId) {
    return ResponseEntity.ok(ApiResponse.success(manualScrapingJobService.get(id, jobId)));
  }

  /** 从失败阶段重试手动刮削作业 */
  @PostMapping("/{id}/manual-scraping/jobs/{jobId}/retry")
  @Operation(summary = "重试手动刮削作业", description = "从已持久化的重命名、生成或上传检查点继续")
  public ResponseEntity<ApiResponse<JobView>> retryManualScrapingJob(
      @Parameter(description = "任务配置ID", required = true) @PathVariable Long id,
      @Parameter(description = "手动刮削作业ID", required = true) @PathVariable Long jobId) {
    return ResponseEntity.ok(ApiResponse.success(manualScrapingJobService.retry(id, jobId)));
  }

  /** 更新状态请求体 */
  public static class UpdateStatusRequest {
    private Boolean isActive;

    public Boolean getIsActive() {
      return isActive;
    }

    public void setIsActive(Boolean isActive) {
      this.isActive = isActive;
    }
  }

  /** 更新最后执行时间请求体 */
  public static class UpdateLastExecTimeRequest {
    private Long lastExecTime;

    public Long getLastExecTime() {
      return lastExecTime;
    }

    public void setLastExecTime(Long lastExecTime) {
      this.lastExecTime = lastExecTime;
    }
  }

  /** 任务提交请求体 */
  public static class TaskSubmitRequest {
    private Boolean isIncremental;

    public Boolean getIsIncremental() {
      return isIncremental;
    }

    public void setIsIncremental(Boolean isIncremental) {
      this.isIncremental = isIncremental;
    }
  }

  /** 第一层目录检查请求体 */
  public static class StructureDirectoryCheckRequest {
    @NotBlank(message = "目录路径不能为空") private String directoryPath;

    public String getDirectoryPath() {
      return directoryPath;
    }

    public void setDirectoryPath(String directoryPath) {
      this.directoryPath = directoryPath;
    }
  }

  /** 实体转DTO */
  private TaskConfigDto convertToDto(TaskConfig config) {
    TaskConfigDto dto = new TaskConfigDto();
    BeanUtils.copyProperties(config, dto);
    return dto;
  }

  /** DTO转实体 */
  private TaskConfig convertToEntity(TaskConfigDto dto) {
    TaskConfig config = new TaskConfig();
    BeanUtils.copyProperties(dto, config);
    return config;
  }

  /**
   * 将 Unix Cron 格式转换为 Quartz Cron 格式 Unix Cron: 分 时 日 月 周 (5个字段) Quartz Cron: 秒 分 时 日 月 周 (6个字段)
   */
  private String convertToQuartzFormat(String cronExpression) {
    if (cronExpression == null || cronExpression.trim().isEmpty()) {
      return null;
    }

    String[] parts = cronExpression.trim().split("\\s+");

    // 如果是 5 个字段，转换为 6 个字段的 Quartz 格式
    if (parts.length == 5) {
      String minute = parts[0];
      String hour = parts[1];
      String day = parts[2];
      String month = parts[3];
      String week = parts[4];

      // 在 Quartz 中，如果指定了周几，日期字段应该用 ?
      if (!week.equals("*")) {
        return "0 " + minute + " " + hour + " ? " + month + " " + week;
      } else {
        return "0 " + minute + " " + hour + " " + day + " " + month + " ?";
      }
    }

    // 如果已经是 6 个字段的 Quartz 格式，直接返回
    if (parts.length == 6) {
      return cronExpression;
    }

    return null;
  }
}
