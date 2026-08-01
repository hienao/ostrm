package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.config.PathConfiguration;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.mapper.MediaServerConfigMapper;
import com.hienao.openlist2strm.mapper.TaskConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskConfigServiceStructureOptionTest {

  private TaskConfigMapper mapper;
  private TaskConfigService service;

  @BeforeEach
  void setUp() {
    mapper = mock(TaskConfigMapper.class);
    when(mapper.insert(any(TaskConfig.class))).thenReturn(1);
    service =
        new TaskConfigService(
            mapper,
            mock(QuartzSchedulerService.class),
            mock(PathConfiguration.class),
            mock(MediaServerConfigMapper.class));
  }

  @Test
  void newTasksKeepExistingBehaviorByDefault() {
    TaskConfig task = baseTask().setLibraryType("movie");

    service.createConfig(task);

    assertFalse(task.getSkipInvalidStructure());
    assertFalse(task.getAutoRenameMedia());
    assertEquals("NONE", task.getMediaRefreshScope());
  }

  @Test
  void autoLibraryTypeCannotEnableStructureFiltering() {
    TaskConfig task = baseTask().setLibraryType("auto").setSkipInvalidStructure(true);

    service.createConfig(task);

    assertFalse(task.getSkipInvalidStructure());
  }

  @Test
  void automaticRenameRequiresScrapingAndExplicitLibraryType() {
    TaskConfig scrapingDisabled =
        baseTask().setLibraryType("movie").setNeedScrap(false).setAutoRenameMedia(true);

    service.createConfig(scrapingDisabled);

    assertFalse(scrapingDisabled.getAutoRenameMedia());

    TaskConfig automaticType =
        baseTask()
            .setTaskName("自动类型")
            .setPath("/auto")
            .setLibraryType("auto")
            .setNeedScrap(true)
            .setAutoRenameMedia(true);

    service.createConfig(automaticType);

    assertFalse(automaticType.getAutoRenameMedia());
  }

  private TaskConfig baseTask() {
    return new TaskConfig()
        .setTaskName("测试任务")
        .setPath("/media")
        .setStrmPath("/strm")
        .setIsActive(true);
  }
}
