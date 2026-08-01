package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.dto.media.AiRecognitionResult;
import com.hienao.openlist2strm.dto.media.MediaInfo;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import com.hienao.openlist2strm.entity.MediaLibraryType;
import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.handler.context.TaskScrapingSession;
import com.hienao.openlist2strm.notification.ScrapeOutcome;
import com.hienao.openlist2strm.service.AiFileNameRecognitionService;
import com.hienao.openlist2strm.service.CoverImageService;
import com.hienao.openlist2strm.service.NfoGeneratorService;
import com.hienao.openlist2strm.service.TmdbApiService;
import com.hienao.openlist2strm.util.TaskMediaParser;
import com.hienao.openlist2strm.util.TmdbIdExtractor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 媒体刮削处理器
 *
 * <p>负责执行媒体刮削，从 TMDB API 获取媒体信息并生成 NFO 和下载图片。
 *
 * <p>Order: 50
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Component
@Order(50)
@RequiredArgsConstructor
public class MediaScrapingHandler implements FileProcessorHandler {

  private final TmdbApiService tmdbApiService;
  private final NfoGeneratorService nfoGeneratorService;
  private final CoverImageService coverImageService;
  private final AiFileNameRecognitionService aiFileNameRecognitionService;

  // ==================== 接口实现 ====================

  @Override
  public ProcessingResult process(FileProcessingContext context) {
    try {
      // 检查刮削是否启用
      if (!isScrapingEnabled(context)) {
        log.debug("刮削功能未启用，跳过");
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      // 检查 TMDB API Key
      if (!isTmdbConfigured(context)) {
        log.warn("TMDB API Key 未配置，跳过刮削");
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      if (Boolean.TRUE.equals(context.getAttribute("metadataAvailable"))) {
        log.debug("已有本地或 OpenList NFO，跳过 TMDB 刮削: {}", context.getBaseFileName());
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }

      // 执行刮削并保存结构化结果，供任务通知汇总。
      ScrapeOutcome outcome = scrapMedia(context);
      context.setAttribute("scrapeOutcome", outcome);
      if (outcome.status() == ScrapeOutcome.Status.FAILED) {
        context.getStats().incrementFailed();
        return ProcessingResult.FAILED;
      }
      if (outcome.isUnrecognized()) {
        context.getStats().incrementSkipped();
        return ProcessingResult.SKIPPED;
      }
      context.getStats().incrementProcessed();
      return ProcessingResult.SUCCESS;

    } catch (Exception e) {
      log.error("媒体刮削失败: {}", context.getBaseFileName(), e);
      context.getStats().incrementFailed();
      return ProcessingResult.FAILED;
    }
  }

  @Override
  public java.util.Set<FileType> getHandledTypes() {
    return java.util.Set.of(FileType.VIDEO);
  }

  // ==================== 刮削逻辑 ====================

  /** 执行媒体刮削 */
  @SuppressWarnings("unchecked")
  private ScrapeOutcome scrapMedia(FileProcessingContext context) {
    String fileName = context.getCurrentFile().getName();
    String relativePath = context.getRelativePath();
    String saveDirectory = context.getSaveDirectory();

    log.info("开始刮削媒体文件: {}", fileName);

    // 获取配置
    Map<String, Object> regexConfig = context.getAttribute("scrapingRegexConfig", Map.of());
    TaskScrapingSession session = getSession(context);

    List<String> movieRegexps =
        (List<String>) regexConfig.getOrDefault("movieRegexps", Collections.emptyList());
    List<String> tvDirRegexps =
        (List<String>) regexConfig.getOrDefault("tvDirRegexps", Collections.emptyList());
    List<String> tvFileRegexps =
        (List<String>) regexConfig.getOrDefault("tvFileRegexps", Collections.emptyList());

    String libraryType = context.getTaskConfig().getLibraryType();

    // 检查路径中是否包含 TMDB ID
    Integer tmdbIdFromPath = TmdbIdExtractor.extractTmdbIdFromPath(relativePath);
    Integer tmdbIdFromFileName = TmdbIdExtractor.extractTmdbIdFromFileName(fileName);
    Integer tmdbId = tmdbIdFromPath != null ? tmdbIdFromPath : tmdbIdFromFileName;

    // 解析文件名
    MediaInfo mediaInfo =
        TaskMediaParser.parse(
            fileName, relativePath, movieRegexps, tvDirRegexps, tvFileRegexps, libraryType);
    log.debug("正则解析媒体信息: {}", mediaInfo);

    // 如果路径中有 TMDB ID，直接使用
    if (tmdbId != null) {
      log.info("检测到路径中的 TMDB ID: {}, 直接从 TMDB 获取信息", tmdbId);
      return scrapeWithTmdbId(context, fileName, saveDirectory, tmdbId, mediaInfo, session);
    }

    // 如果正则解析置信度低，尝试使用 AI
    if (mediaInfo.getConfidence() < 70) {
      log.info("正则解析置信度低 ({}%)，尝试使用 AI 识别: {}", mediaInfo.getConfidence(), fileName);

      Map<String, Object> aiConfig = context.getAttribute("aiConfig", Map.of());
      boolean aiRecognitionEnabled = (Boolean) aiConfig.getOrDefault("enabled", false);

      if (aiRecognitionEnabled) {
        AiRecognitionResult aiResult =
            aiFileNameRecognitionService.recognizeFileName(fileName, relativePath, libraryType);
        if (aiResult != null && aiResult.isSuccess()) {
          if (aiResult.isNewFormat()) {
            mediaInfo = aiResult.toMediaInfo(fileName, libraryType);
          } else if (aiResult.isLegacyFormat()) {
            mediaInfo =
                TaskMediaParser.parse(
                    aiResult.getFilename(),
                    relativePath,
                    movieRegexps,
                    tvDirRegexps,
                    tvFileRegexps,
                    libraryType);
          }
          log.info("使用 AI 识别结果重新解析: {}", mediaInfo);
        }
      }
    }

    if (mediaInfo.getConfidence() < 70) {
      log.warn(
          "最终解析置信度过低 ({}%)，跳过刮削: {}", mediaInfo.getConfidence(), mediaInfo.getOriginalFileName());
      return ScrapeOutcome.unmatched(
          ScrapeOutcome.Status.LOW_CONFIDENCE, "媒体识别置信度过低（" + mediaInfo.getConfidence() + "%）");
    }

    if (mediaInfo.getSearchQuery() == null || mediaInfo.getSearchQuery().isBlank()) {
      return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TITLE_UNAVAILABLE, "无法从文件名和目录中提取媒体标题");
    }

    // 根据媒体类型执行刮削
    String baseFileName = getStrmCompatibleBaseFileName(fileName);

    if (mediaInfo.isMovie()) {
      return scrapMovie(mediaInfo, saveDirectory, baseFileName, session);
    } else if (mediaInfo.isTvShow()) {
      return scrapTvShow(mediaInfo, saveDirectory, baseFileName, session);
    } else {
      log.warn("未知媒体类型，跳过刮削: {}", fileName);
      return ScrapeOutcome.unmatched(ScrapeOutcome.Status.UNSUPPORTED_MEDIA_TYPE, "无法确定媒体类型");
    }
  }

  /** 使用 TMDB ID 直接刮削 */
  private ScrapeOutcome scrapeWithTmdbId(
      FileProcessingContext context,
      String fileName,
      String saveDirectory,
      Integer tmdbId,
      MediaInfo mediaInfo,
      TaskScrapingSession session) {

    MediaLibraryType libraryType = MediaLibraryType.from(context.getTaskConfig().getLibraryType());
    boolean isMovie =
        libraryType == MediaLibraryType.MOVIE
            || (libraryType == MediaLibraryType.AUTO && !mediaInfo.isTvShow());

    try {
      if (isMovie) {
        TmdbMovieDetail movieDetail = getMovieDetail(session, tmdbId);
        if (movieDetail != null) {
          String baseFileName = getStrmCompatibleBaseFileName(fileName);
          scrapMovieWithDetail(mediaInfo, saveDirectory, baseFileName, movieDetail, session);
          return ScrapeOutcome.matched(movieDetail.getTitle(), movieDetail.getId());
        }
      } else {
        TmdbTvDetail tvDetail = getTvDetail(session, tmdbId);
        if (tvDetail != null) {
          String baseFileName = getStrmCompatibleBaseFileName(fileName);
          scrapTvShowWithDetail(mediaInfo, saveDirectory, baseFileName, tvDetail, session);
          return ScrapeOutcome.matched(tvDetail.getName(), tvDetail.getId());
        }
      }
      return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 条目不存在");
    } catch (Exception e) {
      log.error("使用 TMDB ID 刮削失败: {}", tmdbId, e);
      return new ScrapeOutcome(ScrapeOutcome.Status.FAILED, e.getMessage(), null, tmdbId);
    }
  }

  /** 刮削电影 */
  private ScrapeOutcome scrapMovie(
      MediaInfo mediaInfo, String saveDirectory, String baseFileName, TaskScrapingSession session) {
    try {
      String cacheKey = matchCacheKey("movie", mediaInfo);
      TmdbMovieDetail cachedDetail = session.movieMatches().get(cacheKey);
      if (cachedDetail != null) {
        scrapMovieWithDetail(mediaInfo, saveDirectory, baseFileName, cachedDetail, session);
        return ScrapeOutcome.matched(cachedDetail.getTitle(), cachedDetail.getId());
      }
      if (session.negativeMatches().contains(cacheKey)) {
        log.debug("命中电影搜索负缓存，跳过重复请求: {}", mediaInfo.getSearchQuery());
        return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 未找到匹配电影");
      }

      TmdbSearchResponse searchResult =
          tmdbApiService.searchMovies(mediaInfo.getSearchQuery(), mediaInfo.getYear());

      if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
        session.negativeMatches().add(cacheKey);
        log.warn("刮削失败 - 未找到匹配的电影: {} (年份: {})", mediaInfo.getSearchQuery(), mediaInfo.getYear());
        return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 未找到匹配电影");
      }

      TmdbSearchResponse.TmdbSearchResult bestMatch =
          selectBestMatch(searchResult.getResults(), mediaInfo);
      if (bestMatch == null) {
        log.warn("刮削失败 - 未找到合适的电影匹配");
        return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 未找到合适的电影匹配");
      }

      TmdbMovieDetail movieDetail = getMovieDetail(session, bestMatch.getId());
      session.movieMatches().put(cacheKey, movieDetail);
      log.info("找到匹配电影: {} ({})", movieDetail.getTitle(), movieDetail.getId());

      scrapMovieWithDetail(mediaInfo, saveDirectory, baseFileName, movieDetail, session);
      return ScrapeOutcome.matched(movieDetail.getTitle(), movieDetail.getId());

    } catch (Exception e) {
      log.error("刮削电影失败: {}", mediaInfo.getSearchQuery(), e);
      return new ScrapeOutcome(ScrapeOutcome.Status.FAILED, e.getMessage(), null, null);
    }
  }

  /** 使用电影详情刮削 */
  private void scrapMovieWithDetail(
      MediaInfo mediaInfo,
      String saveDirectory,
      String baseFileName,
      TmdbMovieDetail movieDetail,
      TaskScrapingSession session) {

    // 生成NFO文件（始终执行）
    String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
    nfoGeneratorService.generateMovieNfo(movieDetail, mediaInfo, nfoFilePath);

    // 下载图片
    String posterUrl = tmdbApiService.buildPosterUrl(movieDetail.getPosterPath());
    String backdropUrl = tmdbApiService.buildBackdropUrl(movieDetail.getBackdropPath());
    materializeImages(session, posterUrl, backdropUrl, saveDirectory, baseFileName);
  }

  /** 刮削电视剧 */
  private ScrapeOutcome scrapTvShow(
      MediaInfo mediaInfo, String saveDirectory, String baseFileName, TaskScrapingSession session) {
    try {
      String cacheKey = matchCacheKey("tv", mediaInfo);
      TmdbTvDetail cachedDetail = session.tvMatches().get(cacheKey);
      if (cachedDetail != null) {
        scrapTvShowWithDetail(mediaInfo, saveDirectory, baseFileName, cachedDetail, session);
        return ScrapeOutcome.matched(cachedDetail.getName(), cachedDetail.getId());
      }
      if (session.negativeMatches().contains(cacheKey)) {
        log.debug("命中电视剧搜索负缓存，跳过重复请求: {}", mediaInfo.getSearchQuery());
        return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 未找到匹配电视剧");
      }

      TmdbSearchResponse searchResult =
          tmdbApiService.searchTvShows(mediaInfo.getSearchQuery(), mediaInfo.getYear());

      if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
        session.negativeMatches().add(cacheKey);
        log.warn("刮削失败 - 未找到匹配的电视剧: {} (年份: {})", mediaInfo.getSearchQuery(), mediaInfo.getYear());
        return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 未找到匹配电视剧");
      }

      TmdbSearchResponse.TmdbSearchResult bestMatch =
          selectBestMatch(searchResult.getResults(), mediaInfo);
      if (bestMatch == null) {
        log.warn("刮削失败 - 未找到合适的电视剧匹配");
        return ScrapeOutcome.unmatched(ScrapeOutcome.Status.TMDB_NOT_MATCHED, "TMDB 未找到合适的电视剧匹配");
      }

      TmdbTvDetail tvDetail = getTvDetail(session, bestMatch.getId());
      session.tvMatches().put(cacheKey, tvDetail);
      log.info("找到匹配电视剧: {} ({})", tvDetail.getName(), tvDetail.getId());

      scrapTvShowWithDetail(mediaInfo, saveDirectory, baseFileName, tvDetail, session);
      return ScrapeOutcome.matched(tvDetail.getName(), tvDetail.getId());

    } catch (Exception e) {
      log.error("刮削电视剧失败: {}", mediaInfo.getSearchQuery(), e);
      return new ScrapeOutcome(ScrapeOutcome.Status.FAILED, e.getMessage(), null, null);
    }
  }

  /** 使用电视剧详情刮削 */
  private void scrapTvShowWithDetail(
      MediaInfo mediaInfo,
      String saveDirectory,
      String baseFileName,
      TmdbTvDetail tvDetail,
      TaskScrapingSession session) {

    // 生成NFO文件（始终执行）
    String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
    nfoGeneratorService.generateTvShowNfo(tvDetail, mediaInfo, nfoFilePath);

    // 下载图片
    String posterUrl = tmdbApiService.buildPosterUrl(tvDetail.getPosterPath());
    String backdropUrl = tmdbApiService.buildBackdropUrl(tvDetail.getBackdropPath());
    materializeImages(session, posterUrl, backdropUrl, saveDirectory, baseFileName);
  }

  // ==================== 辅助方法 ====================

  private TaskScrapingSession getSession(FileProcessingContext context) {
    TaskScrapingSession session = context.getAttribute("scrapingSession");
    if (session == null) {
      session = new TaskScrapingSession();
      context.setAttribute("scrapingSession", session);
    }
    return session;
  }

  private TmdbMovieDetail getMovieDetail(TaskScrapingSession session, Integer tmdbId) {
    TmdbMovieDetail detail = session.movieDetails().get(tmdbId);
    if (detail == null) {
      detail = tmdbApiService.getMovieDetail(tmdbId);
      session.movieDetails().put(tmdbId, detail);
    }
    return detail;
  }

  private TmdbTvDetail getTvDetail(TaskScrapingSession session, Integer tmdbId) {
    TmdbTvDetail detail = session.tvDetails().get(tmdbId);
    if (detail == null) {
      detail = tmdbApiService.getTvDetail(tmdbId);
      session.tvDetails().put(tmdbId, detail);
    }
    return detail;
  }

  private String matchCacheKey(String mediaType, MediaInfo mediaInfo) {
    return mediaType
        + ":"
        + String.valueOf(mediaInfo.getSearchQuery()).trim().toLowerCase(java.util.Locale.ROOT)
        + ":"
        + String.valueOf(mediaInfo.getYear());
  }

  private void materializeImages(
      TaskScrapingSession session,
      String posterUrl,
      String backdropUrl,
      String saveDirectory,
      String baseFileName) {
    materializeImage(
        session,
        posterUrl,
        Paths.get(saveDirectory, baseFileName + "-poster.jpg"),
        () -> coverImageService.downloadPoster(posterUrl, saveDirectory, baseFileName));
    materializeImage(
        session,
        backdropUrl,
        Paths.get(saveDirectory, baseFileName + "-backdrop.jpg"),
        () -> coverImageService.downloadBackdrop(backdropUrl, saveDirectory, baseFileName));
  }

  private void materializeImage(
      TaskScrapingSession session,
      String imageUrl,
      Path target,
      java.util.function.Supplier<String> downloader) {
    if (imageUrl == null || imageUrl.isBlank() || Files.exists(target)) {
      return;
    }
    try {
      Path cached = session.downloadedImages().get(imageUrl);
      if (cached != null && Files.exists(cached)) {
        Files.createDirectories(target.getParent());
        Files.copy(cached, target, StandardCopyOption.REPLACE_EXISTING);
        return;
      }
      String downloaded = downloader.get();
      if (downloaded != null) {
        session.downloadedImages().put(imageUrl, Paths.get(downloaded));
      }
    } catch (Exception e) {
      log.warn("复用刮削图片失败: {}, 将跳过当前图片", target, e);
    }
  }

  private boolean isScrapingEnabled(FileProcessingContext context) {
    Map<String, Object> config = context.getAttribute("scrapingConfig", Map.of());
    return Boolean.TRUE.equals(config.getOrDefault("enabled", true))
        && Boolean.TRUE.equals(context.getTaskConfig().getNeedScrap());
  }

  private boolean isTmdbConfigured(FileProcessingContext context) {
    Map<String, Object> config = context.getAttribute("tmdbConfig", Map.of());
    String apiKey = (String) config.getOrDefault("apiKey", "");
    return apiKey != null && !apiKey.trim().isEmpty();
  }

  private String getStrmCompatibleBaseFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return "unknown";
    }
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return fileName.substring(0, lastDotIndex);
    }
    return fileName;
  }

  private TmdbSearchResponse.TmdbSearchResult selectBestMatch(
      List<TmdbSearchResponse.TmdbSearchResult> results, MediaInfo mediaInfo) {
    if (results == null || results.isEmpty()) {
      return null;
    }
    if (results.size() == 1) {
      return results.get(0);
    }

    // 优先选择有年份匹配的结果
    if (mediaInfo.isHasYear() && mediaInfo.getYear() != null) {
      for (TmdbSearchResponse.TmdbSearchResult result : results) {
        if (mediaInfo.getYear().equals(result.getReleaseYear())) {
          return result;
        }
      }
    }

    // 选择评分最高的结果
    return results.stream()
        .filter(r -> r.getVoteAverage() != null)
        .max((r1, r2) -> Double.compare(r1.getVoteAverage(), r2.getVoteAverage()))
        .orElse(results.get(0));
  }
}
