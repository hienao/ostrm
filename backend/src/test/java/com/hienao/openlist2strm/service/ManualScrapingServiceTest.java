package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryTree;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ManualScrapingServiceTest {

  private TaskConfigService taskConfigService;
  private OpenlistConfigService openlistConfigService;
  private OpenlistApiService openlistApiService;
  private StrmFileService strmFileService;
  private ManualScrapingService service;

  @BeforeEach
  void setUp() {
    taskConfigService = mock(TaskConfigService.class);
    openlistConfigService = mock(OpenlistConfigService.class);
    openlistApiService = mock(OpenlistApiService.class);
    strmFileService = mock(StrmFileService.class);
    service =
        new ManualScrapingService(
            taskConfigService,
            openlistConfigService,
            openlistApiService,
            strmFileService,
            mock(SystemConfigService.class),
            mock(AiFileNameRecognitionService.class),
            mock(TmdbApiService.class),
            mock(NfoGeneratorService.class),
            mock(CoverImageService.class));
  }

  @Test
  void buildsAllFoldersAndAggregatesVideoCounts() {
    stubMovieTask();
    OpenlistConfig config = openlistConfigService.getById(3L);
    when(openlistApiService.getAllFilesRecursively(config, "/movies"))
        .thenReturn(
            List.of(
                entry("Movie A", "/movies/Movie A", "folder"),
                entry("movie.mkv", "/movies/Movie A/movie.mkv", "file"),
                entry("Extras", "/movies/Movie A/Extras", "folder"),
                entry("trailer.mkv", "/movies/Movie A/Extras/trailer.mkv", "file"),
                entry("Empty", "/movies/Empty", "folder")));
    when(strmFileService.isVideoFile(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class).endsWith(".mkv"));

    DirectoryTree result = service.getDirectoryTree(7L);

    assertEquals(2, result.getTree().getVideoFileCount());
    assertEquals(2, result.getTree().getChildren().size());
    assertEquals("Empty", result.getTree().getChildren().get(0).getName());
    assertEquals(0, result.getTree().getChildren().get(0).getVideoFileCount());
    assertEquals(2, result.getTree().getChildren().get(1).getVideoFileCount());
  }

  @Test
  void rejectsDirectoryWithOnlyAStringPrefixMatch() {
    stubMovieTask();

    assertThrows(BusinessException.class, () -> service.preview(7L, "/movies-archive/Movie"));
  }

  private void stubMovieTask() {
    TaskConfig task =
        new TaskConfig()
            .setId(7L)
            .setTaskName("电影")
            .setPath("/movies")
            .setLibraryType("movie")
            .setOpenlistConfigId(3L);
    OpenlistConfig config = new OpenlistConfig().setId(3L);
    when(taskConfigService.getById(7L)).thenReturn(task);
    when(openlistConfigService.getById(3L)).thenReturn(config);
  }

  private OpenlistApiService.OpenlistFile entry(String name, String path, String type) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName(name);
    file.setPath(path);
    file.setType(type);
    return file;
  }
}
