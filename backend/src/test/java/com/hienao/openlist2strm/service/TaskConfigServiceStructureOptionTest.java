package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.config.PathConfiguration;
import com.hienao.openlist2strm.entity.TaskConfig;
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
            mapper, mock(QuartzSchedulerService.class), mock(PathConfiguration.class));
  }

  @Test
  void newTasksKeepExistingBehaviorByDefault() {
    TaskConfig task = baseTask().setLibraryType("movie");

    service.createConfig(task);

    assertFalse(task.getSkipInvalidStructure());
  }

  @Test
  void autoLibraryTypeCannotEnableStructureFiltering() {
    TaskConfig task = baseTask().setLibraryType("auto").setSkipInvalidStructure(true);

    service.createConfig(task);

    assertFalse(task.getSkipInvalidStructure());
  }

  private TaskConfig baseTask() {
    return new TaskConfig()
        .setTaskName("测试任务")
        .setPath("/media")
        .setStrmPath("/strm")
        .setIsActive(true);
  }
}
