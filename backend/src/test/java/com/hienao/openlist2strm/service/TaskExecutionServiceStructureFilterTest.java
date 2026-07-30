package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.entity.TaskConfig;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskExecutionServiceStructureFilterTest {

  private StrmFileService strmFileService;
  private TaskExecutionService service;

  @BeforeEach
  void setUp() {
    strmFileService = mock(StrmFileService.class);
    service =
        new TaskExecutionService(
            null, null, null, strmFileService, null, null, null, null, Runnable::run);
  }

  @Test
  void filtersInvalidMoviePathsWhenEnabled() {
    TaskConfig task =
        new TaskConfig()
            .setTaskName("电影")
            .setPath("/media")
            .setLibraryType("movie")
            .setSkipInvalidStructure(true);
    OpenlistApiService.OpenlistFile valid = video("合规电影.mkv", "/media/合规电影 (2026)/合规电影.mkv");
    OpenlistApiService.OpenlistFile root = video("根目录.mkv", "/media/根目录.mkv");
    OpenlistApiService.OpenlistFile deep = video("层级过深.mkv", "/media/电影/额外目录/层级过深.mkv");
    mockRelativePaths(valid, root, deep);

    TaskExecutionService.StructureFilterResult result =
        service.filterVideoFilesByStructure(task, List.of(valid, root, deep));

    assertEquals(List.of(valid), result.eligibleVideoFiles());
    assertEquals(2, result.skippedVideoPaths().size());
    assertTrue(result.skippedVideoPaths().contains(root.getPath()));
    assertTrue(result.skippedVideoPaths().contains(deep.getPath()));
  }

  @Test
  void keepsAllVideosWhenFilteringDisabled() {
    TaskConfig task =
        new TaskConfig()
            .setTaskName("电影")
            .setPath("/media")
            .setLibraryType("movie")
            .setSkipInvalidStructure(false);
    OpenlistApiService.OpenlistFile root = video("根目录.mkv", "/media/根目录.mkv");

    TaskExecutionService.StructureFilterResult result =
        service.filterVideoFilesByStructure(task, List.of(root));

    assertEquals(List.of(root), result.eligibleVideoFiles());
    assertTrue(result.skippedVideoPaths().isEmpty());
  }

  @Test
  void keepsAllVideosForAutoLibraryType() {
    TaskConfig task =
        new TaskConfig()
            .setTaskName("自动")
            .setPath("/media")
            .setLibraryType("auto")
            .setSkipInvalidStructure(true);
    OpenlistApiService.OpenlistFile root = video("根目录.mkv", "/media/根目录.mkv");

    TaskExecutionService.StructureFilterResult result =
        service.filterVideoFilesByStructure(task, List.of(root));

    assertEquals(List.of(root), result.eligibleVideoFiles());
    assertTrue(result.skippedVideoPaths().isEmpty());
  }

  private void mockRelativePaths(OpenlistApiService.OpenlistFile... files) {
    for (OpenlistApiService.OpenlistFile file : files) {
      when(strmFileService.calculateRelativePath("/media", file.getPath()))
          .thenReturn(file.getPath().substring("/media/".length()));
    }
  }

  private OpenlistApiService.OpenlistFile video(String name, String path) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName(name);
    file.setPath(path);
    file.setType("file");
    return file;
  }
}
