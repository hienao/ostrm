package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

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
            null,
            null,
            null,
            strmFileService,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            Runnable::run);
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

  @Test
  void selectsOnlyValidFirstLevelMediaDirectoriesForAutomaticRename() {
    TaskConfig task =
        new TaskConfig().setPath("/media").setLibraryType("tv").setAutoRenameMedia(true);
    OpenlistApiService.OpenlistFile first =
        video("S01E01.mkv", "/media/Show B/Season 01/S01E01.mkv");
    OpenlistApiService.OpenlistFile second =
        video("S01E02.mkv", "/media/Show B/Season 01/S01E02.mkv");
    OpenlistApiService.OpenlistFile another =
        video("S01E01.mkv", "/media/Show A/Season 01/S01E01.mkv");
    OpenlistApiService.OpenlistFile invalid = video("root.mkv", "/media/root.mkv");
    org.mockito.Mockito.when(strmFileService.isVideoFile(org.mockito.ArgumentMatchers.anyString()))
        .thenReturn(true);

    List<String> result =
        service.autoRenameDirectories(task, List.of(first, second, another, invalid));

    assertEquals(List.of("/media/Show A", "/media/Show B"), result);
  }

  private OpenlistApiService.OpenlistFile video(String name, String path) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName(name);
    file.setPath(path);
    file.setType("file");
    return file;
  }
}
