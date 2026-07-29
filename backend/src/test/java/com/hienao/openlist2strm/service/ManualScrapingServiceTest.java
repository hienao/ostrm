package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryTree;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.Preview;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.PreviewRequest;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
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
  private TmdbApiService tmdbApiService;
  private ManualScrapingService service;

  @BeforeEach
  void setUp() {
    taskConfigService = mock(TaskConfigService.class);
    openlistConfigService = mock(OpenlistConfigService.class);
    openlistApiService = mock(OpenlistApiService.class);
    strmFileService = mock(StrmFileService.class);
    tmdbApiService = mock(TmdbApiService.class);
    service =
        new ManualScrapingService(
            taskConfigService,
            openlistConfigService,
            openlistApiService,
            strmFileService,
            mock(SystemConfigService.class),
            mock(AiFileNameRecognitionService.class),
            tmdbApiService,
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

  @Test
  void returnsEditableSearchValuesWhenTmdbHasNoMatch() {
    stubMovieDirectory();
    TmdbSearchResponse emptyResponse = new TmdbSearchResponse();
    emptyResponse.setResults(List.of());
    when(tmdbApiService.searchMovies("镖人：风起大漠", "2026")).thenReturn(emptyResponse);

    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath("/movies/镖人");
    request.setTitle("镖人：风起大漠");
    request.setYear("2026");

    Preview preview = service.preview(7L, request);

    assertFalse(preview.isMatched());
    assertEquals("镖人：风起大漠", preview.getSearchTitle());
    assertEquals("2026", preview.getSearchYear());
    assertEquals(1, preview.getVideoFileCount());
    verify(tmdbApiService).searchMovies("镖人：风起大漠", "2026");
  }

  @Test
  void loadsManualTmdbIdAndIncludesItInRenamePreview() {
    stubMovieDirectory();
    TmdbMovieDetail detail = new TmdbMovieDetail();
    detail.setId(123456);
    detail.setTitle("镖人：风起大漠");
    detail.setOriginalTitle("Blades of the Guardians");
    detail.setReleaseDate("2026-02-17");
    when(tmdbApiService.getMovieDetail(123456)).thenReturn(detail);

    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath("/movies/镖人");
    request.setTmdbId(123456);

    Preview preview = service.preview(7L, request);

    assertTrue(preview.isMatched());
    assertEquals(123456, preview.getTmdbId());
    assertEquals("镖人：风起大漠 (2026) {tmdbid-123456}", preview.getProposedDirectoryName());
    assertEquals(
        "镖人：风起大漠 (2026) {tmdbid-123456}.mkv",
        preview.getProposedFileRenames().get(0).getTargetName());
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

  private void stubMovieDirectory() {
    stubMovieTask();
    OpenlistConfig config = openlistConfigService.getById(3L);
    when(openlistApiService.getAllFilesRecursively(config, "/movies/镖人"))
        .thenReturn(List.of(entry("movie.mkv", "/movies/镖人/movie.mkv", "file")));
    when(strmFileService.isVideoFile("movie.mkv")).thenReturn(true);
  }

  private OpenlistApiService.OpenlistFile entry(String name, String path, String type) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName(name);
    file.setPath(path);
    file.setType(type);
    return file;
  }
}
