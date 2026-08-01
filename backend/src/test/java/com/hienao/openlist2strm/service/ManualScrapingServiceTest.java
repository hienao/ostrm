package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryTree;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.Preview;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.PreviewRequest;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import com.hienao.openlist2strm.entity.ManualScrapingJob;
import com.hienao.openlist2strm.entity.ManualScrapingJobStage;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InOrder;
import org.springframework.test.util.ReflectionTestUtils;

class ManualScrapingServiceTest {

  private TaskConfigService taskConfigService;
  private OpenlistConfigService openlistConfigService;
  private OpenlistApiService openlistApiService;
  private StrmFileService strmFileService;
  private SystemConfigService systemConfigService;
  private TmdbApiService tmdbApiService;
  private DataReportService dataReportService;
  private ManualScrapingService service;

  @TempDir Path tempDirectory;

  @BeforeEach
  void setUp() {
    taskConfigService = mock(TaskConfigService.class);
    openlistConfigService = mock(OpenlistConfigService.class);
    openlistApiService = mock(OpenlistApiService.class);
    strmFileService = mock(StrmFileService.class);
    systemConfigService = mock(SystemConfigService.class);
    tmdbApiService = mock(TmdbApiService.class);
    dataReportService = mock(DataReportService.class);
    service =
        new ManualScrapingService(
            taskConfigService,
            openlistConfigService,
            openlistApiService,
            strmFileService,
            systemConfigService,
            mock(AiFileNameRecognitionService.class),
            tmdbApiService,
            mock(NfoGeneratorService.class),
            mock(CoverImageService.class),
            dataReportService,
            new com.fasterxml.jackson.databind.ObjectMapper());
    ReflectionTestUtils.setField(service, "applicationDataPath", tempDirectory.toString());
  }

  @Test
  void loadsOnlyRootDirectoryLevelInitially() {
    stubMovieTask();
    OpenlistConfig config = openlistConfigService.getById(3L);
    when(openlistApiService.getDirectoryContents(config, "/movies"))
        .thenReturn(
            List.of(
                entry("Movie A", "/movies/Movie A", "folder"),
                entry("root.mkv", "/movies/root.mkv", "file"),
                entry("Empty", "/movies/Empty", "folder")));
    when(strmFileService.isVideoFile(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class).endsWith(".mkv"));

    DirectoryTree result = service.getDirectoryTree(7L);

    assertEquals(1, result.getTree().getVideoFileCount());
    assertTrue(result.getTree().isChildrenLoaded());
    assertEquals(2, result.getTree().getChildren().size());
    assertEquals("Empty", result.getTree().getChildren().get(0).getName());
    assertFalse(result.getTree().getChildren().get(0).isChildrenLoaded());
    verify(openlistApiService).getDirectoryContents(config, "/movies");
    verify(openlistApiService, never()).getAllFilesRecursively(config, "/movies");
  }

  @Test
  void loadsOnlyRequestedChildDirectoryLevel() {
    stubMovieTask();
    OpenlistConfig config = openlistConfigService.getById(3L);
    when(openlistApiService.getDirectoryContents(config, "/movies/Movie A"))
        .thenReturn(
            List.of(
                entry("movie.mkv", "/movies/Movie A/movie.mkv", "file"),
                entry("Extras", "/movies/Movie A/Extras", "folder")));
    when(strmFileService.isVideoFile("movie.mkv")).thenReturn(true);

    var result = service.getDirectoryChildren(7L, "/movies/Movie A");

    assertEquals(1, result.getVideoFileCount());
    assertTrue(result.isChildrenLoaded());
    assertEquals(1, result.getChildren().size());
    assertEquals("Extras", result.getChildren().get(0).getName());
    assertFalse(result.getChildren().get(0).isChildrenLoaded());
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

  @Test
  void keepsAlreadyRenamedFileReservedWhenRecoveringMultiFileMovie() {
    stubMovieTask();
    OpenlistConfig config = openlistConfigService.getById(3L);
    String directory = "/movies/Film (2026) {tmdbid-123}";
    String targetBase = "Film (2026) {tmdbid-123}";
    when(openlistApiService.getAllFilesRecursively(config, directory))
        .thenReturn(
            List.of(
                entry("B.mkv", directory + "/B.mkv", "file"),
                entry(targetBase + ".mkv", directory + "/" + targetBase + ".mkv", "file")));
    when(strmFileService.isVideoFile(anyString())).thenReturn(true);
    TmdbMovieDetail detail = new TmdbMovieDetail();
    detail.setId(123);
    detail.setTitle("Film");
    detail.setReleaseDate("2026-01-01");
    when(tmdbApiService.getMovieDetail(123)).thenReturn(detail);

    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath(directory);
    request.setTmdbId(123);

    Preview preview = service.preview(7L, request);

    assertEquals(targetBase + " - 02.mkv", preview.getProposedFileRenames().get(0).getTargetName());
    assertEquals(targetBase + ".mkv", preview.getProposedFileRenames().get(1).getTargetName());
  }

  @Test
  void taskExecutionAutoRenameOnlyRenamesMedia() {
    stubMovieTask();
    OpenlistConfig config = openlistConfigService.getById(3L);
    String directory = "/movies/Film (2026) {tmdbid-123}";
    when(openlistApiService.getAllFilesRecursively(config, directory))
        .thenReturn(List.of(entry("raw.mkv", directory + "/raw.mkv", "file")));
    when(openlistApiService.getDirectoryContents(config, "/movies"))
        .thenReturn(List.of(entry(lastSegment(directory), directory, "folder")));
    when(strmFileService.isVideoFile("raw.mkv")).thenReturn(true);
    TmdbMovieDetail detail = new TmdbMovieDetail();
    detail.setId(123);
    detail.setTitle("Film");
    detail.setReleaseDate("2026-01-01");
    when(tmdbApiService.getMovieDetail(123)).thenReturn(detail);

    var result = service.autoRenameForTaskExecution(7L, directory);

    verify(openlistApiService)
        .renameEntry(config, directory + "/raw.mkv", "Film (2026) {tmdbid-123}.mkv");
    verify(openlistApiService, never())
        .uploadFile(
            eq(config), anyString(), org.mockito.ArgumentMatchers.any(Path.class), anyString());
    assertTrue(result.matched());
    assertEquals(0, result.renamedDirectoryCount());
    assertEquals(1, result.renamedFileCount());
  }

  @Test
  void previewsCanonicalSeasonDirectoryNames() {
    stubTvDirectory();
    TmdbTvDetail detail = tvDetail();
    when(tmdbApiService.getTvDetail(1399)).thenReturn(detail);

    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath("/tv/Show");
    request.setTmdbId(1399);

    Preview preview = service.preview(7L, request);

    assertEquals(3, preview.getProposedDirectoryRenames().size());
    assertEquals("Season 00", preview.getProposedDirectoryRenames().get(0).getTargetName());
    assertEquals("Season 01", preview.getProposedDirectoryRenames().get(1).getTargetName());
    assertEquals("Season 02", preview.getProposedDirectoryRenames().get(2).getTargetName());
    assertEquals("Show - S01E01.mkv", preview.getProposedFileRenames().get(0).getTargetName());
  }

  @Test
  void previewsContainedSeasonMarkerAndReportsAmbiguousDirectoryWhenEnabled() {
    stubTvTaskWithEntries(
        List.of(
            entry("黑袍纠察队 第四季 1080p Remux", "/tv/Show/黑袍纠察队 第四季 1080p Remux", "folder"),
            entry("S03 第四季", "/tv/Show/S03 第四季", "folder"),
            entry("raw.S04E01.mkv", "/tv/Show/黑袍纠察队 第四季 1080p Remux/raw.S04E01.mkv", "file")));
    when(systemConfigService.isDataReportEnabled()).thenReturn(true);
    when(tmdbApiService.getTvDetail(1399)).thenReturn(tvDetail());

    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath("/tv/Show");
    request.setTmdbId(1399);

    Preview preview = service.preview(7L, request);

    assertEquals(1, preview.getProposedDirectoryRenames().size());
    assertEquals(
        "黑袍纠察队 第四季 1080p Remux", preview.getProposedDirectoryRenames().get(0).getSourceName());
    assertEquals("Season 04", preview.getProposedDirectoryRenames().get(0).getTargetName());
    verify(dataReportService)
        .reportEvent(
            eq("manual_season_directory_match_failed"),
            argThat(
                properties ->
                    "S03 第四季".equals(properties.get("directory_name"))
                        && "ambiguous".equals(properties.get("failure_reason"))));
  }

  @Test
  void doesNotReportAmbiguousSeasonDirectoryWhenUsageReportingIsDisabled() {
    stubTvTaskWithEntries(
        List.of(
            entry("S03 第四季", "/tv/Show/S03 第四季", "folder"),
            entry("raw.S03E01.mkv", "/tv/Show/S03 第四季/raw.S03E01.mkv", "file")));
    when(systemConfigService.isDataReportEnabled()).thenReturn(false);
    when(tmdbApiService.getTvDetail(1399)).thenReturn(tvDetail());

    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath("/tv/Show");
    request.setTmdbId(1399);

    Preview preview = service.preview(7L, request);

    assertTrue(preview.getProposedDirectoryRenames().isEmpty());
    verify(dataReportService, never())
        .reportEvent(anyString(), org.mockito.ArgumentMatchers.anyMap());
  }

  @Test
  void renamesSeriesRootThenSeasonThenMediaFile() {
    stubTvDirectory();
    TmdbTvDetail detail = tvDetail();
    when(tmdbApiService.getTvDetail(1399)).thenReturn(detail);
    OpenlistConfig config = openlistConfigService.getById(3L);
    when(openlistApiService.getDirectoryContents(config, "/tv"))
        .thenReturn(List.of(entry("Show", "/tv/Show", "folder")));

    PreviewRequest previewRequest = new PreviewRequest();
    previewRequest.setDirectoryPath("/tv/Show");
    previewRequest.setTmdbId(1399);
    service.preview(7L, previewRequest);

    var executeRequest = new com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteRequest();
    executeRequest.setDirectoryPath("/tv/Show");
    executeRequest.setMediaType("tv");
    executeRequest.setTmdbId(1399);
    executeRequest.setRenameMedia(true);

    var result = service.execute(7L, executeRequest);

    InOrder order = inOrder(openlistApiService);
    order.verify(openlistApiService).renameEntry(config, "/tv/Show", "Show (2011) {tmdbid-1399}");
    order
        .verify(openlistApiService)
        .renameEntry(config, "/tv/Show (2011) {tmdbid-1399}/S1", "Season 01");
    order
        .verify(openlistApiService)
        .renameEntry(
            config, "/tv/Show (2011) {tmdbid-1399}/Season 01/raw.S01E01.mkv", "Show - S01E01.mkv");
    assertEquals(4, result.getRenamedDirectoryCount());
    assertEquals(3, result.getRenamedFileCount());
  }

  @Test
  void resumesSeasonRenameAfterSeriesRootWasAlreadyRenamed() {
    TaskConfig task =
        new TaskConfig()
            .setId(7L)
            .setTaskName("电视剧")
            .setPath("/tv")
            .setLibraryType("tv")
            .setOpenlistConfigId(3L);
    OpenlistConfig config = new OpenlistConfig().setId(3L);
    String finalRoot = "/tv/Show (2011) {tmdbid-1399}";
    when(taskConfigService.getById(7L)).thenReturn(task);
    when(openlistConfigService.getById(3L)).thenReturn(config);
    when(tmdbApiService.getTvDetail(1399)).thenReturn(tvDetail());
    when(openlistApiService.getDirectoryContents(config, "/tv"))
        .thenReturn(List.of(entry(lastSegment(finalRoot), finalRoot, "folder")));
    List<OpenlistApiService.OpenlistFile> currentEntries =
        List.of(
            entry("S1", finalRoot + "/S1", "folder"),
            entry("raw.S01E01.mkv", finalRoot + "/S1/raw.S01E01.mkv", "file"));
    when(openlistApiService.getAllFilesRecursively(config, finalRoot)).thenReturn(currentEntries);
    when(strmFileService.isVideoFile(anyString())).thenReturn(true);

    String persistedPlan =
        "{\"directoryName\":\"Show (2011) {tmdbid-1399}\","
            + "\"seasonDirectories\":[{\"sourcePath\":\"/tv/Show/S1\",\"sourceName\":\"S1\","
            + "\"targetName\":\"Season 01\"}],"
            + "\"files\":[{\"sourcePath\":\"/tv/Show/S1/raw.S01E01.mkv\","
            + "\"sourceName\":\"raw.S01E01.mkv\",\"targetName\":\"Show - S01E01.mkv\"}]}";
    ManualScrapingJob job =
        new ManualScrapingJob()
            .setId(99L)
            .setTaskId(7L)
            .setDirectoryPath("/tv/Show")
            .setFinalDirectoryPath(finalRoot)
            .setMediaType("tv")
            .setTmdbId(1399)
            .setRenameMedia(true)
            .setStage(ManualScrapingJobStage.RENAMING.name())
            .setRenamePlan(persistedPlan)
            .setRenameOperationIndex(1)
            .setRenamedDirectoryCount(1)
            .setRenamedFileCount(0);

    var result =
        service.executeJob(
            job,
            (stage,
                progress,
                message,
                finalPath,
                directoryCount,
                fileCount,
                renamePlan,
                operationIndex) -> {});

    verify(openlistApiService, never())
        .renameEntry(config, "/tv/Show", "Show (2011) {tmdbid-1399}");
    verify(openlistApiService).renameEntry(config, finalRoot + "/S1", "Season 01");
    verify(openlistApiService)
        .renameEntry(config, finalRoot + "/Season 01/raw.S01E01.mkv", "Show - S01E01.mkv");
    assertEquals(2, result.getRenamedDirectoryCount());
    assertEquals(1, result.getRenamedFileCount());
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

  private void stubTvDirectory() {
    stubTvTaskWithEntries(
        List.of(
            entry("S1", "/tv/Show/S1", "folder"),
            entry("第2季", "/tv/Show/第2季", "folder"),
            entry("Specials", "/tv/Show/Specials", "folder"),
            entry("raw.S01E01.mkv", "/tv/Show/S1/raw.S01E01.mkv", "file"),
            entry("raw.S02E01.mkv", "/tv/Show/第2季/raw.S02E01.mkv", "file"),
            entry("raw.S00E01.mkv", "/tv/Show/Specials/raw.S00E01.mkv", "file")));
  }

  private void stubTvTaskWithEntries(List<OpenlistApiService.OpenlistFile> entries) {
    TaskConfig task =
        new TaskConfig()
            .setId(7L)
            .setTaskName("电视剧")
            .setPath("/tv")
            .setLibraryType("tv")
            .setOpenlistConfigId(3L);
    OpenlistConfig config = new OpenlistConfig().setId(3L);
    when(taskConfigService.getById(7L)).thenReturn(task);
    when(openlistConfigService.getById(3L)).thenReturn(config);
    when(openlistApiService.getAllFilesRecursively(config, "/tv/Show")).thenReturn(entries);
    when(openlistApiService.getDirectoryContents(config, "/tv/Show"))
        .thenReturn(entries.stream().filter(entry -> "folder".equals(entry.getType())).toList());
    when(strmFileService.isVideoFile(anyString()))
        .thenAnswer(invocation -> invocation.getArgument(0, String.class).endsWith(".mkv"));
  }

  private TmdbTvDetail tvDetail() {
    TmdbTvDetail detail = new TmdbTvDetail();
    detail.setId(1399);
    detail.setName("Show");
    detail.setFirstAirDate("2011-04-17");
    return detail;
  }

  private String lastSegment(String path) {
    return path.substring(path.lastIndexOf('/') + 1);
  }

  private OpenlistApiService.OpenlistFile entry(String name, String path, String type) {
    OpenlistApiService.OpenlistFile file = new OpenlistApiService.OpenlistFile();
    file.setName(name);
    file.setPath(path);
    file.setType(type);
    return file;
  }
}
