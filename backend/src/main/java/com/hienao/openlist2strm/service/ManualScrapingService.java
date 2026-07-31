package com.hienao.openlist2strm.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.hienao.openlist2strm.dto.media.AiRecognitionResult;
import com.hienao.openlist2strm.dto.media.MediaInfo;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryNode;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.DirectoryTree;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteRequest;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.ExecuteResult;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.Preview;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.PreviewRequest;
import com.hienao.openlist2strm.dto.task.ManualScrapingDtos.RenameItem;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import com.hienao.openlist2strm.entity.ManualScrapingJob;
import com.hienao.openlist2strm.entity.ManualScrapingJobStage;
import com.hienao.openlist2strm.entity.MediaLibraryType;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.util.SeasonDirectoryNameParser;
import com.hienao.openlist2strm.util.TaskMediaParser;
import com.hienao.openlist2strm.util.TmdbIdExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 提供目录选择、媒体识别预览以及确认后写回 OpenList 的手动刮削流程。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManualScrapingService {

  private final TaskConfigService taskConfigService;
  private final OpenlistConfigService openlistConfigService;
  private final OpenlistApiService openlistApiService;
  private final StrmFileService strmFileService;
  private final SystemConfigService systemConfigService;
  private final AiFileNameRecognitionService aiFileNameRecognitionService;
  private final TmdbApiService tmdbApiService;
  private final NfoGeneratorService nfoGeneratorService;
  private final CoverImageService coverImageService;
  private final DataReportService dataReportService;
  private final ObjectMapper objectMapper;
  private final Cache<PreviewCacheKey, PreviewSnapshot> previewCache =
      Caffeine.newBuilder().maximumSize(200).expireAfterWrite(Duration.ofMinutes(5)).build();

  @Value("${app.paths.data}")
  private String applicationDataPath;

  public DirectoryTree getDirectoryTree(Long taskId) {
    Context context = loadContext(taskId);
    DirectoryNode root =
        loadDirectoryLevel(
            context, normalizePath(context.task().getPath()), rootName(context.task()));

    return DirectoryTree.builder()
        .taskId(context.task().getId())
        .taskName(context.task().getTaskName())
        .libraryType(context.libraryType().value())
        .rootPath(normalizePath(context.task().getPath()))
        .tree(root)
        .build();
  }

  public DirectoryNode getDirectoryChildren(Long taskId, String directoryPath) {
    Context context = loadContext(taskId);
    String selectedPath = requireTaskPath(context.task(), directoryPath);
    return loadDirectoryLevel(context, selectedPath, lastSegment(selectedPath));
  }

  private DirectoryNode loadDirectoryLevel(Context context, String directoryPath, String name) {
    List<OpenlistApiService.OpenlistFile> entries =
        openlistApiService.getDirectoryContents(context.openlistConfig(), directoryPath);
    int directVideoCount =
        (int)
            entries.stream()
                .filter(entry -> "file".equals(entry.getType()))
                .filter(entry -> strmFileService.isVideoFile(entry.getName()))
                .count();
    List<DirectoryNode> children =
        entries.stream()
            .filter(entry -> "folder".equals(entry.getType()))
            .sorted(
                Comparator.comparing(
                    OpenlistApiService.OpenlistFile::getName, String.CASE_INSENSITIVE_ORDER))
            .map(
                entry ->
                    DirectoryNode.builder()
                        .name(entry.getName())
                        .path(normalizePath(entry.getPath()))
                        .videoFileCount(0)
                        .childrenLoaded(false)
                        .build())
            .toList();
    return DirectoryNode.builder()
        .name(name)
        .path(normalizePath(directoryPath))
        .videoFileCount(directVideoCount)
        .childrenLoaded(true)
        .children(children)
        .build();
  }

  public Preview preview(Long taskId, PreviewRequest request) {
    Context context = loadContext(taskId);
    String selectedPath = requireTaskPath(context.task(), request.getDirectoryPath());
    validateSelectableDirectory(context.libraryType(), selectedPath);
    VideoScan videoScan = scanVideoFiles(context.openlistConfig(), selectedPath);
    List<OpenlistApiService.OpenlistFile> videoFiles = videoScan.videoFiles();
    if (videoFiles.isEmpty()) {
      throw new BusinessException("所选目录下没有可刮削的媒体文件");
    }
    validateMediaDirectory(context.libraryType(), selectedPath, videoFiles);

    String mediaType = context.libraryType() == MediaLibraryType.MOVIE ? "movie" : "tv";
    String searchTitle = trimToNull(request.getTitle());
    String searchYear = trimToNull(request.getYear());
    Match match;

    if (request.getTmdbId() != null) {
      match = loadMatch(mediaType, request.getTmdbId());
    } else {
      MediaInfo mediaInfo;
      if (searchTitle != null) {
        mediaInfo =
            new MediaInfo()
                .setType(
                    context.libraryType() == MediaLibraryType.MOVIE
                        ? MediaInfo.MediaType.MOVIE
                        : MediaInfo.MediaType.TV_SHOW)
                .setTitle(searchTitle)
                .setYear(searchYear)
                .setHasYear(searchYear != null)
                .setConfidence(100);
      } else {
        mediaInfo = recognize(context, videoFiles.get(0));
        searchTitle = mediaInfo.getSearchQuery();
        searchYear = searchYear != null ? searchYear : trimToNull(mediaInfo.getYear());
        if (searchYear != null) {
          mediaInfo.setYear(searchYear).setHasYear(true);
        }
      }
      match = findMatch(context.libraryType(), mediaInfo, selectedPath);
      if (match == null) {
        return Preview.builder()
            .directoryPath(selectedPath)
            .mediaType(mediaType)
            .matched(false)
            .searchTitle(searchTitle)
            .searchYear(searchYear)
            .matchMessage("TMDB 未找到匹配结果，请修改标题、年份或直接输入 TMDB ID")
            .videoFileCount(videoFiles.size())
            .build();
      }
    }

    RenamePlan renamePlan =
        buildRenamePlan(
            context,
            selectedPath,
            videoFiles,
            videoScan.entries(),
            match.title(),
            match.year(),
            match.tmdbId());

    Preview preview =
        Preview.builder()
            .directoryPath(selectedPath)
            .mediaType(match.mediaType())
            .matched(true)
            .searchTitle(match.title())
            .searchYear(match.year())
            .tmdbId(match.tmdbId())
            .title(match.title())
            .originalTitle(match.originalTitle())
            .year(match.year())
            .overview(match.overview())
            .voteAverage(match.voteAverage())
            .posterUrl(match.posterUrl())
            .backdropUrl(match.backdropUrl())
            .videoFileCount(videoFiles.size())
            .proposedDirectoryName(renamePlan.directoryName())
            .proposedDirectoryRenames(renamePlan.seasonDirectories())
            .proposedFileRenames(renamePlan.files())
            .generatedFiles(metadataNames(match, renamePlan, false))
            .renamedGeneratedFiles(metadataNames(match, renamePlan, true))
            .build();
    previewCache.put(
        new PreviewCacheKey(taskId, selectedPath, match.tmdbId()),
        new PreviewSnapshot(
            match,
            List.copyOf(videoFiles),
            List.copyOf(videoScan.entries()),
            renamePlan,
            videoScan.rootDirectoryFingerprint()));
    return preview;
  }

  public Preview preview(Long taskId, String directoryPath) {
    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath(directoryPath);
    return preview(taskId, request);
  }

  public ExecuteResult execute(Long taskId, ExecuteRequest request) {
    return executeInternal(
        taskId, request, ManualScrapingJobStage.PREPARING, null, 0, 0, null, 0, null);
  }

  public ExecuteResult executeJob(ManualScrapingJob job, JobProgressListener listener) {
    ExecuteRequest request = new ExecuteRequest();
    request.setDirectoryPath(job.getDirectoryPath());
    request.setMediaType(job.getMediaType());
    request.setTmdbId(job.getTmdbId());
    request.setRenameMedia(Boolean.TRUE.equals(job.getRenameMedia()));
    Path workspace = Paths.get(applicationDataPath, "manual-scraping", String.valueOf(job.getId()));
    return executeInternal(
        job.getTaskId(),
        request,
        job.stageValue(),
        job.getFinalDirectoryPath(),
        job.getRenamedDirectoryCount() == null ? 0 : job.getRenamedDirectoryCount(),
        job.getRenamedFileCount() == null ? 0 : job.getRenamedFileCount(),
        job.getRenamePlan(),
        job.getRenameOperationIndex() == null ? 0 : job.getRenameOperationIndex(),
        workspace,
        listener);
  }

  private ExecuteResult executeInternal(
      Long taskId,
      ExecuteRequest request,
      ManualScrapingJobStage resumeStage,
      String checkpointDirectoryPath,
      int checkpointRenamedDirectoryCount,
      int checkpointRenamedFileCount,
      String checkpointRenamePlan,
      int checkpointRenameOperationIndex,
      Path persistentWorkspace) {
    return executeInternal(
        taskId,
        request,
        resumeStage,
        checkpointDirectoryPath,
        checkpointRenamedDirectoryCount,
        checkpointRenamedFileCount,
        checkpointRenamePlan,
        checkpointRenameOperationIndex,
        persistentWorkspace,
        (stage,
            progress,
            message,
            finalPath,
            renamedDirectoryCount,
            renamedFileCount,
            renamePlan,
            renameOperationIndex) -> {});
  }

  private ExecuteResult executeInternal(
      Long taskId,
      ExecuteRequest request,
      ManualScrapingJobStage resumeStage,
      String checkpointDirectoryPath,
      int checkpointRenamedDirectoryCount,
      int checkpointRenamedFileCount,
      String checkpointRenamePlan,
      int checkpointRenameOperationIndex,
      Path persistentWorkspace,
      JobProgressListener listener) {
    Context context = loadContext(taskId);
    String originalPath = requireTaskPath(context.task(), request.getDirectoryPath());
    listener.checkpoint(
        resumeStage,
        Math.max(5, stageProgress(resumeStage)),
        resumeStage == ManualScrapingJobStage.PREPARING ? "正在校验目录并读取 TMDB 信息" : "正在恢复上次中断的刮削作业",
        checkpointDirectoryPath,
        checkpointRenamedDirectoryCount,
        checkpointRenamedFileCount,
        checkpointRenamePlan,
        checkpointRenameOperationIndex);
    validateRequestedType(context.libraryType(), request.getMediaType());
    PreviewCacheKey previewCacheKey =
        new PreviewCacheKey(taskId, originalPath, request.getTmdbId());
    PreviewSnapshot previewSnapshot = previewCache.getIfPresent(previewCacheKey);
    Match match =
        previewSnapshot == null
            ? loadMatch(request.getMediaType(), request.getTmdbId())
            : previewSnapshot.match();
    String expectedDirectoryName = buildDirectoryName(match.title(), match.year(), match.tmdbId());
    String expectedDirectoryPath =
        request.isRenameMedia() ? join(parentPath(originalPath), expectedDirectoryName) : null;
    String temporaryDirectoryPath =
        request.isRenameMedia()
            ? join(
                parentPath(originalPath), temporaryRenameName(originalPath, expectedDirectoryName))
            : null;
    String selectedPath =
        resolveExecutionPath(
            context,
            originalPath,
            checkpointDirectoryPath,
            expectedDirectoryPath,
            temporaryDirectoryPath);
    validateSelectableDirectory(context.libraryType(), selectedPath);

    boolean reusePreview =
        canReusePreviewSnapshot(context, selectedPath, originalPath, previewSnapshot);
    VideoScan videoScan =
        reusePreview
            ? new VideoScan(
                previewSnapshot.videoFiles(),
                previewSnapshot.entries(),
                previewSnapshot.rootDirectoryFingerprint())
            : scanVideoFiles(context.openlistConfig(), selectedPath);
    List<OpenlistApiService.OpenlistFile> videoFiles = videoScan.videoFiles();
    if (videoFiles.isEmpty()) {
      throw new BusinessException("所选目录下没有可刮削的媒体文件");
    }
    validateMediaDirectory(context.libraryType(), selectedPath, videoFiles);

    RenamePlan renamePlan = readRenamePlan(checkpointRenamePlan);
    if (renamePlan == null) {
      renamePlan =
          buildRenamePlan(
              context,
              selectedPath,
              videoFiles,
              videoScan.entries(),
              match.title(),
              match.year(),
              match.tmdbId());
    }
    String serializedRenamePlan = writeRenamePlan(renamePlan);
    String finalDirectoryPath = selectedPath;
    int renamedDirectoryCount = checkpointRenamedDirectoryCount;
    int renamedFileCount = checkpointRenamedFileCount;
    int renameOperationIndex = checkpointRenameOperationIndex;

    if (request.isRenameMedia() && !resumeStage.isAtLeast(ManualScrapingJobStage.GENERATING)) {
      listener.checkpoint(
          ManualScrapingJobStage.RENAMING,
          15,
          "正在重命名媒体目录和文件",
          finalDirectoryPath,
          renamedDirectoryCount,
          renamedFileCount,
          serializedRenamePlan,
          renameOperationIndex);
      if (normalizePath(context.task().getPath()).equals(selectedPath)) {
        throw new BusinessException("不能重命名任务根目录，请选择根目录下的媒体目录");
      }
      validateRenameCollisions(
          context.openlistConfig(), selectedPath, renamePlan, videoScan.entries());
      RenameExecutionResult renameResult =
          executeRenamePlan(
              context,
              originalPath,
              selectedPath,
              renamePlan,
              videoScan.entries(),
              renameOperationIndex,
              renamedDirectoryCount,
              renamedFileCount,
              serializedRenamePlan,
              listener);
      finalDirectoryPath = renameResult.finalDirectoryPath();
      renamedDirectoryCount = renameResult.renamedDirectoryCount();
      renamedFileCount = renameResult.renamedFileCount();
      renameOperationIndex = renameResult.operationIndex();
    }

    if (checkpointDirectoryPath != null && !checkpointDirectoryPath.isBlank()) {
      finalDirectoryPath = selectedPath;
    }

    Path workspace = persistentWorkspace;
    boolean temporaryWorkspace = workspace == null;
    try {
      if (temporaryWorkspace) {
        workspace = Files.createTempDirectory("ostrm-manual-scraping-");
      } else {
        Files.createDirectories(workspace);
      }

      List<GeneratedFile> generated;
      if (resumeStage.isAtLeast(ManualScrapingJobStage.UPLOADING) && hasGeneratedFiles(workspace)) {
        generated = loadGeneratedFiles(workspace);
      } else {
        listener.checkpoint(
            ManualScrapingJobStage.GENERATING,
            50,
            "正在生成 NFO 并下载图片",
            finalDirectoryPath,
            renamedDirectoryCount,
            renamedFileCount,
            serializedRenamePlan,
            renameOperationIndex);
        clearDirectoryContents(workspace);
        generated =
            generateMetadata(context, match, renamePlan, request.isRenameMedia(), workspace);
      }

      listener.checkpoint(
          ManualScrapingJobStage.UPLOADING,
          75,
          "正在上传元数据到 OpenList",
          finalDirectoryPath,
          renamedDirectoryCount,
          renamedFileCount,
          serializedRenamePlan,
          renameOperationIndex);
      String uploadDirectoryPath = finalDirectoryPath;
      int completedDirectoryRenameCount = renamedDirectoryCount;
      int completedRenameCount = renamedFileCount;
      int completedRenameOperationIndex = renameOperationIndex;
      List<String> uploadedFiles =
          uploadMetadata(
              context,
              uploadDirectoryPath,
              generated,
              (uploaded, total) ->
                  listener.checkpoint(
                      ManualScrapingJobStage.UPLOADING,
                      75 + (int) Math.round(uploaded * 20.0 / Math.max(1, total)),
                      "已上传 " + uploaded + "/" + total + " 个元数据文件",
                      uploadDirectoryPath,
                      completedDirectoryRenameCount,
                      completedRenameCount,
                      serializedRenamePlan,
                      completedRenameOperationIndex));
      listener.checkpoint(
          ManualScrapingJobStage.COMPLETED,
          100,
          "手动刮削完成",
          finalDirectoryPath,
          renamedDirectoryCount,
          renamedFileCount,
          serializedRenamePlan,
          renameOperationIndex);
      deleteTempDirectory(workspace);
      previewCache.invalidate(previewCacheKey);
      return ExecuteResult.builder()
          .finalDirectoryPath(finalDirectoryPath)
          .renamedDirectoryCount(renamedDirectoryCount)
          .renamedFileCount(renamedFileCount)
          .uploadedFiles(uploadedFiles)
          .message("手动刮削完成，已上传 " + uploadedFiles.size() + " 个元数据文件")
          .build();
    } catch (IOException e) {
      throw new BusinessException("生成刮削文件失败: " + e.getMessage(), e);
    } finally {
      if (temporaryWorkspace) {
        deleteTempDirectory(workspace);
      }
    }
  }

  private Context loadContext(Long taskId) {
    TaskConfig task = taskConfigService.getById(taskId);
    if (task == null) {
      throw new BusinessException("任务不存在，ID: " + taskId);
    }
    MediaLibraryType libraryType = MediaLibraryType.from(task.getLibraryType());
    if (libraryType == MediaLibraryType.AUTO) {
      throw new BusinessException("手动刮削前请先为任务选择电影、电视剧或动画类型");
    }
    OpenlistConfig config = openlistConfigService.getById(task.getOpenlistConfigId());
    if (config == null) {
      throw new BusinessException("任务关联的 OpenList 配置不存在");
    }
    return new Context(task, config, libraryType);
  }

  private List<OpenlistApiService.OpenlistFile> findVideoFiles(
      OpenlistConfig config, String directoryPath) {
    return scanVideoFiles(config, directoryPath).videoFiles();
  }

  private VideoScan scanVideoFiles(OpenlistConfig config, String directoryPath) {
    List<OpenlistApiService.OpenlistFile> entries =
        openlistApiService.getAllFilesRecursively(config, directoryPath);
    List<OpenlistApiService.OpenlistFile> videoFiles =
        entries.stream()
            .filter(file -> "file".equals(file.getType()))
            .filter(file -> strmFileService.isVideoFile(file.getName()))
            .sorted(Comparator.comparing(OpenlistApiService.OpenlistFile::getPath))
            .toList();
    return new VideoScan(videoFiles, entries, directoryFingerprint(entries, directoryPath));
  }

  private boolean canReusePreviewSnapshot(
      Context context, String selectedPath, String originalPath, PreviewSnapshot previewSnapshot) {
    if (previewSnapshot == null || !selectedPath.equals(originalPath)) {
      return false;
    }
    List<OpenlistApiService.OpenlistFile> currentRoot =
        openlistApiService.getDirectoryContents(context.openlistConfig(), selectedPath);
    return previewSnapshot
        .rootDirectoryFingerprint()
        .equals(directoryFingerprint(currentRoot, selectedPath));
  }

  private Map<String, String> directoryFingerprint(
      List<OpenlistApiService.OpenlistFile> entries, String directoryPath) {
    Map<String, String> fingerprint = new java.util.TreeMap<>();
    String normalizedDirectory = normalizePath(directoryPath);
    for (OpenlistApiService.OpenlistFile entry : entries) {
      if (normalizedDirectory.equals(normalizePath(parentPath(entry.getPath())))) {
        fingerprint.put(
            entry.getPath(),
            String.join(
                "|",
                String.valueOf(entry.getType()),
                String.valueOf(entry.getSize()),
                String.valueOf(entry.getModified()),
                String.valueOf(entry.getSign())));
      }
    }
    return fingerprint;
  }

  private MediaInfo recognize(Context context, OpenlistApiService.OpenlistFile file) {
    Map<String, Object> regexConfig = systemConfigService.getScrapingRegexConfig();
    MediaInfo mediaInfo =
        TaskMediaParser.parse(
            file.getName(),
            relativePath(context.task().getPath(), file.getPath()),
            stringList(regexConfig, "movieRegexps"),
            stringList(regexConfig, "tvDirRegexps"),
            stringList(regexConfig, "tvFileRegexps"),
            context.libraryType().value());

    if (mediaInfo.getConfidence() < 70) {
      AiRecognitionResult aiResult =
          aiFileNameRecognitionService.recognizeFileName(
              file.getName(),
              relativePath(context.task().getPath(), file.getPath()),
              context.libraryType().value());
      if (aiResult != null && aiResult.isSuccess() && aiResult.isNewFormat()) {
        mediaInfo = aiResult.toMediaInfo(file.getName(), context.libraryType().value());
      }
    }
    if (mediaInfo.getSearchQuery() == null || mediaInfo.getSearchQuery().isBlank()) {
      throw new BusinessException("无法从所选目录识别媒体标题");
    }
    return mediaInfo;
  }

  @SuppressWarnings("unchecked")
  private List<String> stringList(Map<String, Object> config, String key) {
    Object value = config.get(key);
    return value instanceof List<?> ? (List<String>) value : List.of();
  }

  private Match findMatch(MediaLibraryType libraryType, MediaInfo mediaInfo, String directoryPath) {
    Integer tmdbId = TmdbIdExtractor.extractTmdbIdFromPath(directoryPath);
    if (tmdbId != null) {
      return loadMatch(libraryType == MediaLibraryType.MOVIE ? "movie" : "tv", tmdbId);
    }

    TmdbSearchResponse response =
        libraryType == MediaLibraryType.MOVIE
            ? tmdbApiService.searchMovies(mediaInfo.getSearchQuery(), mediaInfo.getYear())
            : tmdbApiService.searchTvShows(mediaInfo.getSearchQuery(), mediaInfo.getYear());
    if (response.getResults() == null || response.getResults().isEmpty()) {
      return null;
    }
    TmdbSearchResponse.TmdbSearchResult best =
        selectBestMatch(response.getResults(), mediaInfo.getYear());
    return loadMatch(libraryType == MediaLibraryType.MOVIE ? "movie" : "tv", best.getId());
  }

  private TmdbSearchResponse.TmdbSearchResult selectBestMatch(
      List<TmdbSearchResponse.TmdbSearchResult> results, String year) {
    if (year != null && !year.isBlank()) {
      for (TmdbSearchResponse.TmdbSearchResult result : results) {
        if (year.equals(result.getReleaseYear())) {
          return result;
        }
      }
    }
    return results.stream()
        .filter(result -> result.getVoteAverage() != null)
        .max(Comparator.comparing(TmdbSearchResponse.TmdbSearchResult::getVoteAverage))
        .orElse(results.get(0));
  }

  private Match loadMatch(String mediaType, Integer tmdbId) {
    if ("movie".equals(mediaType)) {
      TmdbMovieDetail detail = tmdbApiService.getMovieDetail(tmdbId);
      return new Match(
          "movie",
          detail.getId(),
          detail.getTitle(),
          detail.getOriginalTitle(),
          detail.getReleaseYear(),
          detail.getOverview(),
          detail.getVoteAverage(),
          tmdbApiService.buildPosterUrl(detail.getPosterPath()),
          tmdbApiService.buildBackdropUrl(detail.getBackdropPath()),
          detail,
          null);
    }
    if ("tv".equals(mediaType)) {
      TmdbTvDetail detail = tmdbApiService.getTvDetail(tmdbId);
      return new Match(
          "tv",
          detail.getId(),
          detail.getName(),
          detail.getOriginalName(),
          detail.getFirstAirYear(),
          detail.getOverview(),
          detail.getVoteAverage(),
          tmdbApiService.buildPosterUrl(detail.getPosterPath()),
          tmdbApiService.buildBackdropUrl(detail.getBackdropPath()),
          null,
          detail);
    }
    throw new BusinessException("不支持的媒体类型: " + mediaType);
  }

  private RenamePlan buildRenamePlan(
      Context context,
      String selectedPath,
      List<OpenlistApiService.OpenlistFile> videoFiles,
      List<OpenlistApiService.OpenlistFile> entries,
      String title,
      String year,
      Integer tmdbId) {
    String directoryName = buildDirectoryName(title, year, tmdbId);
    List<RenameItem> seasonDirectories =
        context.libraryType().isTvLike()
            ? buildSeasonDirectoryRenames(context, selectedPath, entries)
            : List.of();
    List<RenameItem> files = new ArrayList<>();
    List<String> targetBases = new ArrayList<>();
    List<String> extensions = new ArrayList<>();
    Set<String> usedTargets = new HashSet<>();
    Map<String, String> retainedTargets = new LinkedHashMap<>();

    for (OpenlistApiService.OpenlistFile file : videoFiles) {
      String extension = extension(file.getName());
      String targetBase;
      if (context.libraryType() == MediaLibraryType.MOVIE) {
        targetBase = directoryName;
      } else {
        MediaInfo episode =
            TaskMediaParser.parse(
                file.getName(),
                relativePath(context.task().getPath(), file.getPath()),
                List.of(),
                stringList(systemConfigService.getScrapingRegexConfig(), "tvDirRegexps"),
                stringList(systemConfigService.getScrapingRegexConfig(), "tvFileRegexps"),
                context.libraryType().value());
        targetBase =
            episode.getSeasonEpisodeString() == null
                ? stripExtension(file.getName())
                : safeName(title + " - " + episode.getSeasonEpisodeString());
      }
      targetBases.add(targetBase);
      extensions.add(extension);

      if (isGeneratedTargetName(file.getName(), targetBase, extension)) {
        retainedTargets.put(file.getPath(), file.getName());
        usedTargets.add(targetKey(parentPath(file.getPath()), file.getName()));
      }
    }

    for (int index = 0; index < videoFiles.size(); index++) {
      OpenlistApiService.OpenlistFile file = videoFiles.get(index);
      String retainedTarget = retainedTargets.get(file.getPath());
      if (retainedTarget != null) {
        files.add(
            RenameItem.builder()
                .sourcePath(file.getPath())
                .sourceName(file.getName())
                .targetName(retainedTarget)
                .build());
        continue;
      }

      String targetBase = targetBases.get(index);
      String extension = extensions.get(index);
      String parent = parentPath(file.getPath());
      String targetName = targetBase + extension;
      int duplicate = 1;
      while (usedTargets.contains(targetKey(parent, targetName))) {
        duplicate++;
        targetName = targetBase + " - " + String.format("%02d", duplicate) + extension;
      }
      usedTargets.add(targetKey(parent, targetName));
      files.add(
          RenameItem.builder()
              .sourcePath(file.getPath())
              .sourceName(file.getName())
              .targetName(targetName)
              .build());
    }
    return new RenamePlan(directoryName, seasonDirectories, files);
  }

  private List<RenameItem> buildSeasonDirectoryRenames(
      Context context, String selectedPath, List<OpenlistApiService.OpenlistFile> entries) {
    List<RenameItem> seasonDirectories = new ArrayList<>();
    for (OpenlistApiService.OpenlistFile entry : entries) {
      if (!"folder".equals(entry.getType())
          || !normalizePath(selectedPath).equals(normalizePath(parentPath(entry.getPath())))) {
        continue;
      }

      SeasonDirectoryNameParser.Result result =
          SeasonDirectoryNameParser.parseForRename(entry.getName());
      if (result.matched()) {
        seasonDirectories.add(
            RenameItem.builder()
                .sourcePath(entry.getPath())
                .sourceName(entry.getName())
                .targetName(String.format("Season %02d", result.seasonNumber()))
                .build());
      } else if (result.shouldReportFailure()) {
        reportSeasonDirectoryMatchFailure(context, entry.getName(), result);
      }
    }
    return seasonDirectories.stream()
        .sorted(
            Comparator.comparing(RenameItem::getTargetName)
                .thenComparing(RenameItem::getSourcePath))
        .toList();
  }

  private void reportSeasonDirectoryMatchFailure(
      Context context, String directoryName, SeasonDirectoryNameParser.Result result) {
    log.warn(
        "季目录识别失败，跳过重命名: taskId={}, directory={}, reason={}, detectedMarkers={}, detectedSeasons={}",
        context.task().getId(),
        directoryName,
        result.failureReason(),
        result.detectedMarkers(),
        result.detectedSeasons());

    if (!systemConfigService.isDataReportEnabled()) {
      return;
    }
    Map<String, Object> properties = new LinkedHashMap<>();
    properties.put("source", "manual_scraping");
    properties.put("task_id", context.task().getId());
    properties.put("library_type", context.libraryType().value());
    properties.put("directory_name", directoryName);
    properties.put("failure_reason", result.failureReason());
    properties.put("detected_markers", result.detectedMarkers());
    properties.put("detected_seasons", result.detectedSeasons());
    dataReportService.reportEvent("manual_season_directory_match_failed", properties);
  }

  private boolean isGeneratedTargetName(String currentName, String targetBase, String extension) {
    String currentBase = stripExtension(currentName);
    return currentName.equals(targetBase + extension)
        || currentBase.matches(Pattern.quote(targetBase) + " - \\d{2}");
  }

  private String targetKey(String parent, String fileName) {
    return (parent + "/" + fileName).toLowerCase(Locale.ROOT);
  }

  private String buildDirectoryName(String title, String year, Integer tmdbId) {
    return safeName(
        title
            + (year == null || year.isBlank() ? "" : " (" + year + ")")
            + " {tmdbid-"
            + tmdbId
            + "}");
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private void validateRenameCollisions(
      OpenlistConfig config,
      String selectedPath,
      RenamePlan renamePlan,
      List<OpenlistApiService.OpenlistFile> entries) {
    String parent = parentPath(selectedPath);
    boolean directoryCollision =
        openlistApiService.getDirectoryContents(config, parent).stream()
            .anyMatch(
                entry ->
                    "folder".equals(entry.getType())
                        && renamePlan.directoryName().equals(entry.getName())
                        && !selectedPath.equals(entry.getPath()));
    if (directoryCollision) {
      throw new BusinessException("目标文件夹已存在: " + renamePlan.directoryName());
    }

    Set<String> paths = entryPaths(entries);
    Map<String, String> seasonTargets = new LinkedHashMap<>();
    for (RenameItem item : renamePlan.seasonDirectories()) {
      String existingSource =
          seasonTargets.putIfAbsent(
              item.getTargetName().toLowerCase(Locale.ROOT), item.getSourceName());
      if (existingSource != null && !existingSource.equals(item.getSourceName())) {
        throw new BusinessException(
            "多个季目录会重命名为同一目标: "
                + existingSource
                + "、"
                + item.getSourceName()
                + " -> "
                + item.getTargetName());
      }
      String targetPath = join(parentPath(item.getSourcePath()), item.getTargetName());
      if (!item.getSourceName().equals(item.getTargetName())
          && paths.contains(normalizePath(item.getSourcePath()))
          && paths.contains(normalizePath(targetPath))) {
        throw new BusinessException("目标季目录已存在: " + item.getTargetName());
      }
    }

    Set<String> plannedFileTargets = new HashSet<>();
    for (RenameItem item : renamePlan.files()) {
      String itemParent = parentPath(item.getSourcePath());
      String targetPath = normalizePath(join(itemParent, item.getTargetName()));
      if (!plannedFileTargets.add(targetPath.toLowerCase(Locale.ROOT))) {
        throw new BusinessException("多个媒体文件会重命名为同一目标: " + item.getTargetName());
      }
      if (!item.getSourceName().equals(item.getTargetName())
          && paths.contains(normalizePath(item.getSourcePath()))
          && paths.contains(targetPath)) {
        throw new BusinessException("目标媒体文件已存在: " + item.getTargetName());
      }
    }
  }

  private RenameExecutionResult executeRenamePlan(
      Context context,
      String originalPath,
      String selectedPath,
      RenamePlan renamePlan,
      List<OpenlistApiService.OpenlistFile> entries,
      int checkpointOperationIndex,
      int checkpointDirectoryCount,
      int checkpointFileCount,
      String serializedRenamePlan,
      JobProgressListener listener) {
    List<RenameOperation> operations = buildRenameOperations(originalPath, renamePlan);
    Set<String> currentPaths = entryPaths(entries);
    currentPaths.add(normalizePath(selectedPath));
    String finalRoot = normalizePath(join(parentPath(originalPath), renamePlan.directoryName()));
    int directoryCount = checkpointDirectoryCount;
    int fileCount = checkpointFileCount;
    int operationIndex = Math.max(0, Math.min(checkpointOperationIndex, operations.size()));

    for (int index = operationIndex; index < operations.size(); index++) {
      RenameOperation operation = operations.get(index);
      String sourcePath = operationSourcePath(operation, originalPath, finalRoot, renamePlan);
      renamePathIdempotently(
          context.openlistConfig(),
          sourcePath,
          operation.item().getTargetName(),
          operation.directory(),
          currentPaths);
      if (operation.directory()) {
        directoryCount++;
      } else {
        fileCount++;
      }
      operationIndex = index + 1;
      int progress = 15 + (int) Math.round(operationIndex * 30.0 / Math.max(1, operations.size()));
      listener.checkpoint(
          ManualScrapingJobStage.RENAMING,
          progress,
          "已重命名 " + directoryCount + " 个目录、" + fileCount + " 个媒体文件",
          currentPaths.contains(finalRoot) ? finalRoot : selectedPath,
          directoryCount,
          fileCount,
          serializedRenamePlan,
          operationIndex);
    }
    return new RenameExecutionResult(
        currentPaths.contains(finalRoot) ? finalRoot : selectedPath,
        directoryCount,
        fileCount,
        operationIndex);
  }

  private List<RenameOperation> buildRenameOperations(String originalPath, RenamePlan renamePlan) {
    List<RenameOperation> operations = new ArrayList<>();
    if (!lastSegment(originalPath).equals(renamePlan.directoryName())) {
      operations.add(
          new RenameOperation(
              true,
              RenameItem.builder()
                  .sourcePath(originalPath)
                  .sourceName(lastSegment(originalPath))
                  .targetName(renamePlan.directoryName())
                  .build()));
    }
    renamePlan.seasonDirectories().stream()
        .filter(item -> !item.getSourceName().equals(item.getTargetName()))
        .map(item -> new RenameOperation(true, item))
        .forEach(operations::add);
    renamePlan.files().stream()
        .filter(item -> !item.getSourceName().equals(item.getTargetName()))
        .map(item -> new RenameOperation(false, item))
        .forEach(operations::add);
    return operations;
  }

  private String operationSourcePath(
      RenameOperation operation, String originalPath, String finalRoot, RenamePlan renamePlan) {
    RenameItem item = operation.item();
    if (normalizePath(item.getSourcePath()).equals(normalizePath(originalPath))) {
      return normalizePath(originalPath);
    }
    String relativeParent = relativeParent(originalPath, item.getSourcePath());
    if (!operation.directory()) {
      relativeParent = remapSeasonParent(relativeParent, renamePlan.seasonDirectories());
    }
    String currentParent = relativeParent.isBlank() ? finalRoot : join(finalRoot, relativeParent);
    return normalizePath(join(currentParent, item.getSourceName()));
  }

  private String remapSeasonParent(String relativeParent, List<RenameItem> seasonDirectories) {
    if (relativeParent == null || relativeParent.isBlank()) {
      return relativeParent;
    }
    int slash = relativeParent.indexOf('/');
    String first = slash < 0 ? relativeParent : relativeParent.substring(0, slash);
    String remainder = slash < 0 ? "" : relativeParent.substring(slash);
    return seasonDirectories.stream()
        .filter(item -> item.getSourceName().equals(first))
        .findFirst()
        .map(item -> item.getTargetName() + remainder)
        .orElse(relativeParent);
  }

  private void renamePathIdempotently(
      OpenlistConfig config,
      String sourcePath,
      String targetName,
      boolean directory,
      Set<String> currentPaths) {
    String normalizedSource = normalizePath(sourcePath);
    String targetPath = normalizePath(join(parentPath(normalizedSource), targetName));
    String temporaryName = temporaryRenameName(normalizedSource, targetName);
    String temporaryPath = normalizePath(join(parentPath(normalizedSource), temporaryName));
    boolean sourceExists = currentPaths.contains(normalizedSource);
    boolean targetExists = currentPaths.contains(targetPath);
    boolean temporaryExists = currentPaths.contains(temporaryPath);

    if (!sourceExists && targetExists) {
      return;
    }
    if (sourceExists && targetExists && !normalizedSource.equals(targetPath)) {
      throw new BusinessException("重命名目标已存在: " + targetPath);
    }
    if (!sourceExists && temporaryExists) {
      openlistApiService.renameEntry(config, temporaryPath, targetName);
      replacePathPrefix(currentPaths, temporaryPath, targetPath, directory);
      return;
    }
    if (!sourceExists) {
      throw new BusinessException("重命名源和目标均不存在，目录可能已被外部修改: " + sourcePath);
    }

    if (lastSegment(normalizedSource).equalsIgnoreCase(targetName)
        && !lastSegment(normalizedSource).equals(targetName)) {
      openlistApiService.renameEntry(config, normalizedSource, temporaryName);
      replacePathPrefix(currentPaths, normalizedSource, temporaryPath, directory);
      openlistApiService.renameEntry(config, temporaryPath, targetName);
      replacePathPrefix(currentPaths, temporaryPath, targetPath, directory);
      return;
    }
    openlistApiService.renameEntry(config, normalizedSource, targetName);
    replacePathPrefix(currentPaths, normalizedSource, targetPath, directory);
  }

  private void replacePathPrefix(
      Set<String> paths, String sourcePath, String targetPath, boolean directory) {
    Set<String> replacements = new HashSet<>();
    for (String path : List.copyOf(paths)) {
      if (path.equals(sourcePath) || (directory && path.startsWith(sourcePath + "/"))) {
        paths.remove(path);
        replacements.add(targetPath + path.substring(sourcePath.length()));
      }
    }
    paths.addAll(replacements);
  }

  private Set<String> entryPaths(List<OpenlistApiService.OpenlistFile> entries) {
    Set<String> paths = new HashSet<>();
    for (OpenlistApiService.OpenlistFile entry : entries) {
      paths.add(normalizePath(entry.getPath()));
    }
    return paths;
  }

  private String temporaryRenameName(String sourcePath, String targetName) {
    return ".ostrm-renaming-"
        + Integer.toUnsignedString((sourcePath + "->" + targetName).hashCode(), 36);
  }

  private String writeRenamePlan(RenamePlan renamePlan) {
    try {
      return objectMapper.writeValueAsString(renamePlan);
    } catch (JsonProcessingException e) {
      throw new BusinessException("保存重命名计划失败", e);
    }
  }

  private RenamePlan readRenamePlan(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(value, RenamePlan.class);
    } catch (JsonProcessingException e) {
      throw new BusinessException("读取重命名计划失败，请重新创建手动刮削作业", e);
    }
  }

  private List<GeneratedFile> generateMetadata(
      Context context, Match match, RenamePlan renamePlan, boolean renamedMedia, Path workspace) {
    MediaInfo mediaInfo =
        new MediaInfo()
            .setType(
                "movie".equals(match.mediaType())
                    ? MediaInfo.MediaType.MOVIE
                    : MediaInfo.MediaType.TV_SHOW)
            .setTitle(match.title())
            .setYear(match.year())
            .setHasYear(match.year() != null);
    List<GeneratedFile> generated = new ArrayList<>();

    if ("movie".equals(match.mediaType())) {
      String baseName =
          renamePlan.files().isEmpty()
              ? renamePlan.directoryName()
              : stripExtension(
                  renamedMedia
                      ? renamePlan.files().get(0).getTargetName()
                      : renamePlan.files().get(0).getSourceName());
      Path nfo = workspace.resolve(baseName + ".nfo");
      nfoGeneratorService.generateMovieNfo(match.movieDetail(), mediaInfo, nfo.toString());
      generated.add(new GeneratedFile(nfo, baseName + ".nfo", "application/xml"));
      addImage(
          generated,
          coverImageService.downloadPoster(match.posterUrl(), workspace.toString(), baseName),
          baseName + "-poster.jpg");
      addImage(
          generated,
          coverImageService.downloadBackdrop(match.backdropUrl(), workspace.toString(), baseName),
          baseName + "-backdrop.jpg");
    } else {
      Path nfo = workspace.resolve("tvshow.nfo");
      nfoGeneratorService.generateTvShowNfo(match.tvDetail(), mediaInfo, nfo.toString());
      generated.add(new GeneratedFile(nfo, "tvshow.nfo", "application/xml"));
      addImage(
          generated,
          coverImageService.downloadPoster(match.posterUrl(), workspace.toString(), "manual"),
          "poster.jpg");
      addImage(
          generated,
          coverImageService.downloadBackdrop(match.backdropUrl(), workspace.toString(), "manual"),
          "fanart.jpg");
    }
    return generated;
  }

  private List<String> uploadMetadata(
      Context context,
      String directoryPath,
      List<GeneratedFile> generated,
      UploadProgressListener listener)
      throws IOException {
    List<String> uploaded = new ArrayList<>();
    for (GeneratedFile file : generated) {
      openlistApiService.uploadFile(
          context.openlistConfig(),
          join(directoryPath, file.remoteName()),
          file.localPath(),
          file.contentType());
      uploaded.add(file.remoteName());
      listener.uploaded(uploaded.size(), generated.size());
    }
    return uploaded;
  }

  private List<GeneratedFile> loadGeneratedFiles(Path workspace) throws IOException {
    try (var paths = Files.list(workspace)) {
      return paths
          .filter(Files::isRegularFile)
          .sorted()
          .map(
              path ->
                  new GeneratedFile(
                      path,
                      path.getFileName().toString(),
                      path.getFileName().toString().endsWith(".nfo")
                          ? "application/xml"
                          : "image/jpeg"))
          .toList();
    }
  }

  private boolean hasGeneratedFiles(Path workspace) throws IOException {
    if (!Files.isDirectory(workspace)) {
      return false;
    }
    try (var paths = Files.list(workspace)) {
      return paths.anyMatch(Files::isRegularFile);
    }
  }

  private void clearDirectoryContents(Path directory) throws IOException {
    if (!Files.exists(directory)) {
      Files.createDirectories(directory);
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths
          .filter(path -> !directory.equals(path))
          .sorted(Comparator.reverseOrder())
          .forEach(this::deleteQuietly);
    }
  }

  private String resolveExecutionPath(
      Context context,
      String originalPath,
      String checkpointPath,
      String expectedPath,
      String temporaryPath) {
    if (checkpointPath != null
        && !checkpointPath.isBlank()
        && directoryExists(context.openlistConfig(), checkpointPath)) {
      return normalizePath(checkpointPath);
    }
    if (directoryExists(context.openlistConfig(), originalPath)) {
      return originalPath;
    }
    if (expectedPath != null && directoryExists(context.openlistConfig(), expectedPath)) {
      return normalizePath(expectedPath);
    }
    if (temporaryPath != null && directoryExists(context.openlistConfig(), temporaryPath)) {
      return normalizePath(temporaryPath);
    }
    throw new BusinessException("原目录和重命名后的目录均不存在，无法继续刮削");
  }

  private boolean directoryExists(OpenlistConfig config, String path) {
    String normalized = normalizePath(path);
    return openlistApiService.getDirectoryContents(config, parentPath(normalized)).stream()
        .anyMatch(
            entry ->
                "folder".equals(entry.getType())
                    && lastSegment(normalized).equals(entry.getName()));
  }

  private int stageProgress(ManualScrapingJobStage stage) {
    return switch (stage) {
      case PREPARING -> 5;
      case RENAMING -> 15;
      case GENERATING -> 50;
      case UPLOADING -> 75;
      case COMPLETED -> 100;
    };
  }

  private void addImage(List<GeneratedFile> files, String localPath, String remoteName) {
    if (localPath != null) {
      files.add(new GeneratedFile(Paths.get(localPath), remoteName, "image/jpeg"));
    }
  }

  private List<String> metadataNames(Match match, RenamePlan renamePlan, boolean renamedMedia) {
    List<String> files = new ArrayList<>();
    if ("movie".equals(match.mediaType())) {
      String baseName =
          renamePlan.files().isEmpty()
              ? renamePlan.directoryName()
              : stripExtension(
                  renamedMedia
                      ? renamePlan.files().get(0).getTargetName()
                      : renamePlan.files().get(0).getSourceName());
      files.add(baseName + ".nfo");
      if (match.posterUrl() != null) {
        files.add(baseName + "-poster.jpg");
      }
      if (match.backdropUrl() != null) {
        files.add(baseName + "-backdrop.jpg");
      }
      return files;
    }
    files.add("tvshow.nfo");
    if (match.posterUrl() != null) {
      files.add("poster.jpg");
    }
    if (match.backdropUrl() != null) {
      files.add("fanart.jpg");
    }
    return files;
  }

  private void deleteTempDirectory(Path directory) {
    if (directory == null) {
      return;
    }
    try (var paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(this::deleteQuietly);
    } catch (IOException e) {
      log.warn("清理手动刮削临时目录失败: {}", directory, e);
    }
  }

  private void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      log.warn("清理手动刮削临时文件失败: {}", path, e);
    }
  }

  private void validateRequestedType(MediaLibraryType taskType, String requestedType) {
    String expected = taskType == MediaLibraryType.MOVIE ? "movie" : "tv";
    if (!expected.equals(requestedType)) {
      throw new BusinessException("刮削媒体类型与任务媒体库类型不一致");
    }
  }

  private void validateSelectableDirectory(MediaLibraryType libraryType, String selectedPath) {
    if (libraryType.isTvLike() && TaskMediaParser.isSeasonDirectory(lastSegment(selectedPath))) {
      throw new BusinessException("请选择电视剧或动画的根目录，不要直接选择季目录");
    }
  }

  private void validateMediaDirectory(
      MediaLibraryType libraryType,
      String selectedPath,
      List<OpenlistApiService.OpenlistFile> videoFiles) {
    if (libraryType != MediaLibraryType.MOVIE) {
      return;
    }
    boolean hasDirectVideo =
        videoFiles.stream()
            .anyMatch(file -> selectedPath.equals(normalizePath(parentPath(file.getPath()))));
    if (!hasDirectVideo) {
      throw new BusinessException("请选择具体电影目录，不要选择包含多个电影的媒体库根目录");
    }
  }

  private String requireTaskPath(TaskConfig task, String selectedPath) {
    String root = normalizePath(task.getPath());
    String selected = normalizePath(selectedPath);
    if (!selected.equals(root) && !selected.startsWith(root + "/")) {
      throw new BusinessException("所选目录不在当前任务目录内");
    }
    return selected;
  }

  private String normalizePath(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException("目录路径不能为空");
    }
    Path normalized = Paths.get(value.replace('\\', '/')).normalize();
    String result = normalized.toString().replace('\\', '/');
    return result.startsWith("/") ? result : "/" + result;
  }

  private String relativePath(String rootPath, String path) {
    String root = normalizePath(rootPath);
    String target = normalizePath(path);
    if (target.equals(root)) {
      return "";
    }
    return target.startsWith(root + "/") ? target.substring(root.length() + 1) : target;
  }

  private String relativeParent(String rootPath, String filePath) {
    return parentPath(relativePath(rootPath, filePath));
  }

  private String join(String parent, String child) {
    if (parent == null || parent.isBlank() || ".".equals(parent)) {
      return child;
    }
    return (parent.endsWith("/") ? parent : parent + "/") + child;
  }

  private String parentPath(String path) {
    String normalized = path == null ? "" : path.replace('\\', '/').replaceAll("/+$", "");
    int index = normalized.lastIndexOf('/');
    if (index < 0) {
      return "";
    }
    return index == 0 ? "/" : normalized.substring(0, index);
  }

  private String lastSegment(String path) {
    String normalized = path == null ? "" : path.replace('\\', '/').replaceAll("/+$", "");
    int index = normalized.lastIndexOf('/');
    return index >= 0 ? normalized.substring(index + 1) : normalized;
  }

  private String rootName(TaskConfig task) {
    String name = lastSegment(task.getPath());
    return name.isBlank() ? task.getTaskName() : name;
  }

  private String safeName(String value) {
    String safe = value == null ? "" : value.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
    if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
      throw new BusinessException("识别结果无法生成合法文件名");
    }
    return safe;
  }

  private String extension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index > 0 ? fileName.substring(index) : "";
  }

  private String stripExtension(String fileName) {
    int index = fileName.lastIndexOf('.');
    return index > 0 ? fileName.substring(0, index) : fileName;
  }

  private record Context(
      TaskConfig task, OpenlistConfig openlistConfig, MediaLibraryType libraryType) {}

  private record Match(
      String mediaType,
      Integer tmdbId,
      String title,
      String originalTitle,
      String year,
      String overview,
      Double voteAverage,
      String posterUrl,
      String backdropUrl,
      TmdbMovieDetail movieDetail,
      TmdbTvDetail tvDetail) {}

  private record RenamePlan(
      String directoryName, List<RenameItem> seasonDirectories, List<RenameItem> files) {}

  private record RenameOperation(boolean directory, RenameItem item) {}

  private record RenameExecutionResult(
      String finalDirectoryPath,
      int renamedDirectoryCount,
      int renamedFileCount,
      int operationIndex) {}

  private record PreviewCacheKey(Long taskId, String directoryPath, Integer tmdbId) {}

  private record PreviewSnapshot(
      Match match,
      List<OpenlistApiService.OpenlistFile> videoFiles,
      List<OpenlistApiService.OpenlistFile> entries,
      RenamePlan renamePlan,
      Map<String, String> rootDirectoryFingerprint) {}

  private record VideoScan(
      List<OpenlistApiService.OpenlistFile> videoFiles,
      List<OpenlistApiService.OpenlistFile> entries,
      Map<String, String> rootDirectoryFingerprint) {}

  private record GeneratedFile(Path localPath, String remoteName, String contentType) {}

  @FunctionalInterface
  public interface JobProgressListener {
    void checkpoint(
        ManualScrapingJobStage stage,
        int progress,
        String message,
        String finalDirectoryPath,
        int renamedDirectoryCount,
        int renamedFileCount,
        String renamePlan,
        int renameOperationIndex);
  }

  @FunctionalInterface
  private interface UploadProgressListener {
    void uploaded(int uploaded, int total);
  }
}
