package com.hienao.openlist2strm.service;

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
import com.hienao.openlist2strm.entity.MediaLibraryType;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.entity.TaskConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import com.hienao.openlist2strm.util.TaskMediaParser;
import com.hienao.openlist2strm.util.TmdbIdExtractor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

  public DirectoryTree getDirectoryTree(Long taskId) {
    Context context = loadContext(taskId);
    List<OpenlistApiService.OpenlistFile> entries =
        openlistApiService.getAllFilesRecursively(
            context.openlistConfig(), context.task().getPath());
    MutableDirectory root =
        new MutableDirectory(rootName(context.task()), normalizePath(context.task().getPath()));

    for (OpenlistApiService.OpenlistFile entry : entries) {
      if ("folder".equals(entry.getType())) {
        ensureDirectory(root, context.task().getPath(), entry.getPath());
      } else if ("file".equals(entry.getType()) && strmFileService.isVideoFile(entry.getName())) {
        incrementVideoCounts(root, context.task().getPath(), entry.getPath());
      }
    }

    return DirectoryTree.builder()
        .taskId(context.task().getId())
        .taskName(context.task().getTaskName())
        .libraryType(context.libraryType().value())
        .rootPath(normalizePath(context.task().getPath()))
        .tree(toDto(root))
        .build();
  }

  public Preview preview(Long taskId, PreviewRequest request) {
    Context context = loadContext(taskId);
    String selectedPath = requireTaskPath(context.task(), request.getDirectoryPath());
    validateSelectableDirectory(context.libraryType(), selectedPath);
    List<OpenlistApiService.OpenlistFile> videoFiles =
        findVideoFiles(context.openlistConfig(), selectedPath);
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
            context, selectedPath, videoFiles, match.title(), match.year(), match.tmdbId());

    return Preview.builder()
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
        .proposedFileRenames(renamePlan.files())
        .generatedFiles(metadataNames(match, renamePlan, false))
        .renamedGeneratedFiles(metadataNames(match, renamePlan, true))
        .build();
  }

  public Preview preview(Long taskId, String directoryPath) {
    PreviewRequest request = new PreviewRequest();
    request.setDirectoryPath(directoryPath);
    return preview(taskId, request);
  }

  public ExecuteResult execute(Long taskId, ExecuteRequest request) {
    Context context = loadContext(taskId);
    String selectedPath = requireTaskPath(context.task(), request.getDirectoryPath());
    validateSelectableDirectory(context.libraryType(), selectedPath);
    validateRequestedType(context.libraryType(), request.getMediaType());

    List<OpenlistApiService.OpenlistFile> videoFiles =
        findVideoFiles(context.openlistConfig(), selectedPath);
    if (videoFiles.isEmpty()) {
      throw new BusinessException("所选目录下没有可刮削的媒体文件");
    }
    validateMediaDirectory(context.libraryType(), selectedPath, videoFiles);

    Match match = loadMatch(request.getMediaType(), request.getTmdbId());
    RenamePlan renamePlan =
        buildRenamePlan(
            context, selectedPath, videoFiles, match.title(), match.year(), match.tmdbId());
    String finalDirectoryPath = selectedPath;
    int renamedFileCount = 0;

    if (request.isRenameMedia()) {
      if (normalizePath(context.task().getPath()).equals(selectedPath)) {
        throw new BusinessException("不能重命名任务根目录，请选择根目录下的媒体目录");
      }
      validateRenameCollisions(context.openlistConfig(), selectedPath, renamePlan);
      if (!lastSegment(selectedPath).equals(renamePlan.directoryName())) {
        openlistApiService.renameEntry(
            context.openlistConfig(), selectedPath, renamePlan.directoryName());
        finalDirectoryPath = join(parentPath(selectedPath), renamePlan.directoryName());
      }
      for (RenameItem item : renamePlan.files()) {
        if (item.getSourceName().equals(item.getTargetName())) {
          continue;
        }
        String relativeParent = relativeParent(selectedPath, item.getSourcePath());
        String currentParent =
            relativeParent.isBlank()
                ? finalDirectoryPath
                : join(finalDirectoryPath, relativeParent);
        openlistApiService.renameEntry(
            context.openlistConfig(),
            join(currentParent, item.getSourceName()),
            item.getTargetName());
        renamedFileCount++;
      }
    }

    List<String> uploadedFiles =
        generateAndUploadMetadata(
            context, match, renamePlan, finalDirectoryPath, request.isRenameMedia());
    return ExecuteResult.builder()
        .finalDirectoryPath(finalDirectoryPath)
        .renamedFileCount(renamedFileCount)
        .uploadedFiles(uploadedFiles)
        .message("手动刮削完成，已上传 " + uploadedFiles.size() + " 个元数据文件")
        .build();
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
    return openlistApiService.getAllFilesRecursively(config, directoryPath).stream()
        .filter(file -> "file".equals(file.getType()))
        .filter(file -> strmFileService.isVideoFile(file.getName()))
        .sorted(Comparator.comparing(OpenlistApiService.OpenlistFile::getPath))
        .toList();
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
      String title,
      String year,
      Integer tmdbId) {
    String directoryName =
        safeName(
            title
                + (year == null || year.isBlank() ? "" : " (" + year + ")")
                + " {tmdbid-"
                + tmdbId
                + "}");
    List<RenameItem> files = new ArrayList<>();
    Map<String, Integer> usedNames = new LinkedHashMap<>();

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
      String parent = parentPath(file.getPath());
      String uniquenessKey = parent + "/" + targetBase.toLowerCase(Locale.ROOT) + extension;
      int duplicate = usedNames.merge(uniquenessKey, 1, Integer::sum);
      if (duplicate > 1) {
        targetBase += " - " + String.format("%02d", duplicate);
      }
      files.add(
          RenameItem.builder()
              .sourcePath(file.getPath())
              .sourceName(file.getName())
              .targetName(targetBase + extension)
              .build());
    }
    return new RenamePlan(directoryName, files);
  }

  private String trimToNull(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value.trim();
  }

  private void validateRenameCollisions(
      OpenlistConfig config, String selectedPath, RenamePlan renamePlan) {
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

    Map<String, List<OpenlistApiService.OpenlistFile>> contentsByParent = new LinkedHashMap<>();
    for (RenameItem item : renamePlan.files()) {
      String itemParent = parentPath(item.getSourcePath());
      List<OpenlistApiService.OpenlistFile> contents =
          contentsByParent.computeIfAbsent(
              itemParent, ignored -> openlistApiService.getDirectoryContents(config, itemParent));
      boolean collision =
          contents.stream()
              .anyMatch(
                  entry ->
                      "file".equals(entry.getType())
                          && item.getTargetName().equals(entry.getName())
                          && !item.getSourceName().equals(entry.getName()));
      if (collision) {
        throw new BusinessException("目标媒体文件已存在: " + item.getTargetName());
      }
    }
  }

  private List<String> generateAndUploadMetadata(
      Context context,
      Match match,
      RenamePlan renamePlan,
      String directoryPath,
      boolean renamedMedia) {
    Path tempDirectory = null;
    try {
      tempDirectory = Files.createTempDirectory("ostrm-manual-scraping-");
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
        Path nfo = tempDirectory.resolve(baseName + ".nfo");
        nfoGeneratorService.generateMovieNfo(match.movieDetail(), mediaInfo, nfo.toString());
        generated.add(new GeneratedFile(nfo, baseName + ".nfo", "application/xml"));
        addImage(
            generated,
            coverImageService.downloadPoster(match.posterUrl(), tempDirectory.toString(), baseName),
            baseName + "-poster.jpg");
        addImage(
            generated,
            coverImageService.downloadBackdrop(
                match.backdropUrl(), tempDirectory.toString(), baseName),
            baseName + "-backdrop.jpg");
      } else {
        Path nfo = tempDirectory.resolve("tvshow.nfo");
        nfoGeneratorService.generateTvShowNfo(match.tvDetail(), mediaInfo, nfo.toString());
        generated.add(new GeneratedFile(nfo, "tvshow.nfo", "application/xml"));
        addImage(
            generated,
            coverImageService.downloadPoster(match.posterUrl(), tempDirectory.toString(), "manual"),
            "poster.jpg");
        addImage(
            generated,
            coverImageService.downloadBackdrop(
                match.backdropUrl(), tempDirectory.toString(), "manual"),
            "fanart.jpg");
      }

      List<String> uploaded = new ArrayList<>();
      for (GeneratedFile file : generated) {
        openlistApiService.uploadFile(
            context.openlistConfig(),
            join(directoryPath, file.remoteName()),
            Files.readAllBytes(file.localPath()),
            file.contentType());
        uploaded.add(file.remoteName());
      }
      return uploaded;
    } catch (IOException e) {
      throw new BusinessException("生成刮削文件失败: " + e.getMessage(), e);
    } finally {
      deleteTempDirectory(tempDirectory);
    }
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

  private void ensureDirectory(MutableDirectory root, String taskPath, String directoryPath) {
    String relative = relativePath(taskPath, directoryPath);
    MutableDirectory current = root;
    String currentPath = root.path;
    for (String segment : segments(relative)) {
      currentPath = join(currentPath, segment);
      String childPath = currentPath;
      current =
          current.children.computeIfAbsent(
              segment, ignored -> new MutableDirectory(segment, childPath));
    }
  }

  private void incrementVideoCounts(MutableDirectory root, String taskPath, String filePath) {
    root.videoFileCount++;
    String relativeParent = parentPath(relativePath(taskPath, filePath));
    MutableDirectory current = root;
    String currentPath = root.path;
    for (String segment : segments(relativeParent)) {
      currentPath = join(currentPath, segment);
      String childPath = currentPath;
      current =
          current.children.computeIfAbsent(
              segment, ignored -> new MutableDirectory(segment, childPath));
      current.videoFileCount++;
    }
  }

  private DirectoryNode toDto(MutableDirectory directory) {
    List<DirectoryNode> children =
        directory.children.values().stream()
            .sorted(Comparator.comparing(child -> child.name, String.CASE_INSENSITIVE_ORDER))
            .map(this::toDto)
            .toList();
    return DirectoryNode.builder()
        .name(directory.name)
        .path(directory.path)
        .videoFileCount(directory.videoFileCount)
        .children(new ArrayList<>(children))
        .build();
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

  private List<String> segments(String path) {
    if (path == null || path.isBlank() || ".".equals(path) || "/".equals(path)) {
      return List.of();
    }
    return List.of(path.replace('\\', '/').split("/")).stream()
        .filter(segment -> !segment.isBlank() && !".".equals(segment))
        .toList();
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

  private record RenamePlan(String directoryName, List<RenameItem> files) {}

  private record GeneratedFile(Path localPath, String remoteName, String contentType) {}

  private static final class MutableDirectory {
    private final String name;
    private final String path;
    private int videoFileCount;
    private final Map<String, MutableDirectory> children = new LinkedHashMap<>();

    private MutableDirectory(String name, String path) {
      this.name = name;
      this.path = path;
    }
  }
}
