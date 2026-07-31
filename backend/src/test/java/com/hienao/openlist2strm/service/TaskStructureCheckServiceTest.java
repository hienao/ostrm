package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.dto.task.TaskStructureCheckResult;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TaskStructureCheckServiceTest {

  private TaskConfigService taskConfigService;
  private OpenlistConfigService openlistConfigService;
  private OpenlistApiService openlistApiService;
  private StrmFileService strmFileService;
  private TaskStructureCheckService service;

  @BeforeEach
  void setUp() {
    taskConfigService = mock(TaskConfigService.class);
    openlistConfigService = mock(OpenlistConfigService.class);
    openlistApiService = mock(OpenlistApiService.class);
    strmFileService = mock(StrmFileService.class);
    service =
        new TaskStructureCheckService(
            taskConfigService, openlistConfigService, openlistApiService, strmFileService);
  }

  @Test
  void returnsOnlyInvalidFilesAndTheirAncestorFolders() {
    TaskConfig task =
        new TaskConfig()
            .setId(7L)
            .setTaskName("电影")
            .setPath("/movies")
            .setLibraryType("movie")
            .setOpenlistConfigId(3L);
    OpenlistConfig openlistConfig = new OpenlistConfig().setId(3L);
    when(taskConfigService.getById(7L)).thenReturn(task);
    when(openlistConfigService.getById(3L)).thenReturn(openlistConfig);
    when(openlistApiService.getAllFilesRecursively(openlistConfig, "/movies", true))
        .thenReturn(
            List.of(
                file("root.mkv", "/movies/root.mkv"),
                file("valid.mkv", "/movies/Valid Movie/valid.mkv"),
                file("extra.mkv", "/movies/Deep Movie/Extras/extra.mkv"),
                file("poster.jpg", "/movies/Deep Movie/poster.jpg")));
    when(strmFileService.isVideoFile(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class).endsWith(".mkv"));

    TaskStructureCheckResult result = service.check(7L);

    assertTrue(result.isSupported());
    assertEquals(4, result.getScannedEntryCount());
    assertEquals(3, result.getVideoFileCount());
    assertEquals(2, result.getInvalidFileCount());
    assertEquals(2, result.getTree().getChildren().size());
    assertEquals("Deep Movie", result.getTree().getChildren().get(0).getName());
    assertEquals("root.mkv", result.getTree().getChildren().get(1).getName());
  }

  @Test
  void autoTaskReturnsGuidanceWithoutScanningOpenlist() {
    TaskConfig task =
        new TaskConfig()
            .setId(8L)
            .setTaskName("旧任务")
            .setPath("/mixed")
            .setLibraryType("auto")
            .setOpenlistConfigId(3L);
    when(taskConfigService.getById(8L)).thenReturn(task);

    TaskStructureCheckResult result = service.check(8L);

    assertFalse(result.isSupported());
    assertEquals(0, result.getScannedEntryCount());
    verify(openlistApiService, never())
        .getAllFilesRecursively(org.mockito.ArgumentMatchers.any(), anyString());
  }

  @Test
  void overviewReadsOnlyRootAndChecksDirectRootFiles() {
    TaskConfig task = movieTask();
    OpenlistConfig openlistConfig = new OpenlistConfig().setId(3L);
    when(taskConfigService.getById(7L)).thenReturn(task);
    when(openlistConfigService.getById(3L)).thenReturn(openlistConfig);
    when(openlistApiService.getDirectoryContents(openlistConfig, "/movies", true))
        .thenReturn(
            List.of(
                folder("Zulu", "/movies/Zulu"),
                file("root.mkv", "/movies/root.mkv"),
                folder("Alpha", "/movies/Alpha")));
    when(strmFileService.isVideoFile("root.mkv")).thenReturn(true);

    var result = service.getOverview(7L);

    assertTrue(result.isSupported());
    assertEquals(2, result.getDirectories().size());
    assertEquals("Alpha", result.getDirectories().get(0).getName());
    assertEquals(1, result.getRootFilesResult().getInvalidFileCount());
    verify(openlistApiService).getDirectoryContents(openlistConfig, "/movies", true);
    verify(openlistApiService, never())
        .getAllFilesRecursively(org.mockito.ArgumentMatchers.any(), anyString());
  }

  @Test
  void checksOnlySelectedFirstLevelDirectorySubtree() {
    TaskConfig task = movieTask();
    OpenlistConfig openlistConfig = new OpenlistConfig().setId(3L);
    when(taskConfigService.getById(7L)).thenReturn(task);
    when(openlistConfigService.getById(3L)).thenReturn(openlistConfig);
    when(openlistApiService.getAllFilesRecursively(openlistConfig, "/movies/Deep Movie", true))
        .thenReturn(List.of(file("extra.mkv", "/movies/Deep Movie/Extras/extra.mkv")));
    when(strmFileService.isVideoFile("extra.mkv")).thenReturn(true);

    TaskStructureCheckResult result = service.checkDirectory(7L, "/movies/Deep Movie");

    assertEquals(1, result.getInvalidFileCount());
    assertEquals("Deep Movie", result.getTree().getName());
    assertEquals("Extras", result.getTree().getChildren().get(0).getName());
    verify(openlistApiService).getAllFilesRecursively(openlistConfig, "/movies/Deep Movie", true);
  }

  @Test
  void rejectsNestedDirectoryCheck() {
    when(taskConfigService.getById(7L)).thenReturn(movieTask());

    assertThrows(
        BusinessException.class, () -> service.checkDirectory(7L, "/movies/Show/Season 01"));
    assertThrows(BusinessException.class, () -> service.checkDirectory(7L, "/movies/."));

    verify(openlistApiService, never())
        .getAllFilesRecursively(org.mockito.ArgumentMatchers.any(), anyString());
  }

  private TaskConfig movieTask() {
    return new TaskConfig()
        .setId(7L)
        .setTaskName("电影")
        .setPath("/movies")
        .setLibraryType("movie")
        .setOpenlistConfigId(3L);
  }

  private OpenlistApiService.OpenlistFile file(String name, String path) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName(name);
    file.setPath(path);
    file.setType("file");
    return file;
  }

  private OpenlistApiService.OpenlistFile folder(String name, String path) {
    OpenlistApiService.OpenlistFile folder = new OpenlistApiService.OpenlistFile();
    folder.setName(name);
    folder.setPath(path);
    folder.setType("folder");
    return folder;
  }
}
