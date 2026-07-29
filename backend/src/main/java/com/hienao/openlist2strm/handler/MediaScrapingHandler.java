package com.hienao.openlist2strm.handler;

import com.hienao.openlist2strm.dto.media.AiRecognitionResult;
import com.hienao.openlist2strm.dto.media.MediaInfo;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import com.hienao.openlist2strm.handler.context.FileProcessingContext;
import com.hienao.openlist2strm.service.AiFileNameRecognitionService;
import com.hienao.openlist2strm.service.CoverImageService;
import com.hienao.openlist2strm.service.NfoGeneratorService;
import com.hienao.openlist2strm.service.SystemConfigService;
import com.hienao.openlist2strm.service.TmdbApiService;
import com.hienao.openlist2strm.util.MediaFileParser;
import com.hienao.openlist2strm.util.TmdbIdExtractor;
import java.nio.file.Paths;
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
  private final SystemConfigService systemConfigService;
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

      // 执行刮削
      scrapMedia(context);

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
  private void scrapMedia(FileProcessingContext context) {
    String fileName = context.getCurrentFile().getName();
    String relativePath = context.getRelativePath();
    String saveDirectory = context.getSaveDirectory();

    log.info("开始刮削媒体文件: {}", fileName);

    // 获取配置
    Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
    Map<String, Object> regexConfig = systemConfigService.getScrapingRegexConfig();

    List<String> movieRegexps =
        (List<String>) regexConfig.getOrDefault("movieRegexps", Collections.emptyList());
    List<String> tvDirRegexps =
        (List<String>) regexConfig.getOrDefault("tvDirRegexps", Collections.emptyList());
    List<String> tvFileRegexps =
        (List<String>) regexConfig.getOrDefault("tvFileRegexps", Collections.emptyList());

    // 提取目录路径
    String directoryPath = extractDirectoryPath(relativePath);

    // 检查路径中是否包含 TMDB ID
    Integer tmdbIdFromPath = TmdbIdExtractor.extractTmdbIdFromPath(relativePath);
    Integer tmdbIdFromFileName = TmdbIdExtractor.extractTmdbIdFromFileName(fileName);
    Integer tmdbId = tmdbIdFromPath != null ? tmdbIdFromPath : tmdbIdFromFileName;

    // 解析文件名
    MediaInfo mediaInfo =
        MediaFileParser.parse(fileName, directoryPath, movieRegexps, tvDirRegexps, tvFileRegexps);
    log.debug("正则解析媒体信息: {}", mediaInfo);

    // 如果路径中有 TMDB ID，直接使用
    if (tmdbId != null) {
      log.info("检测到路径中的 TMDB ID: {}, 直接从 TMDB 获取信息", tmdbId);
      scrapeWithTmdbId(context, fileName, saveDirectory, tmdbId, mediaInfo);
      return;
    }

    // 如果正则解析置信度低，尝试使用 AI
    if (mediaInfo.getConfidence() < 70) {
      log.info("正则解析置信度低 ({}%)，尝试使用 AI 识别: {}", mediaInfo.getConfidence(), fileName);

      Map<String, Object> aiConfig = systemConfigService.getAiConfig();
      boolean aiRecognitionEnabled = (Boolean) aiConfig.getOrDefault("enabled", false);

      if (aiRecognitionEnabled) {
        AiRecognitionResult aiResult =
            aiFileNameRecognitionService.recognizeFileName(fileName, relativePath);
        if (aiResult != null && aiResult.isSuccess()) {
          if (aiResult.isNewFormat()) {
            mediaInfo = aiResult.toMediaInfo(fileName);
          } else if (aiResult.isLegacyFormat()) {
            mediaInfo =
                MediaFileParser.parse(
                    aiResult.getFilename(),
                    directoryPath,
                    movieRegexps,
                    tvDirRegexps,
                    tvFileRegexps);
          }
          log.info("使用 AI 识别结果重新解析: {}", mediaInfo);
        }
      }
    }

    if (mediaInfo.getConfidence() < 70) {
      log.warn(
          "最终解析置信度过低 ({}%)，跳过刮削: {}", mediaInfo.getConfidence(), mediaInfo.getOriginalFileName());
      return;
    }

    // 根据媒体类型执行刮削
    String baseFileName = getStrmCompatibleBaseFileName(fileName);

    if (mediaInfo.isMovie()) {
      scrapMovie(mediaInfo, saveDirectory, baseFileName, scrapingConfig);
    } else if (mediaInfo.isTvShow()) {
      scrapTvShow(mediaInfo, saveDirectory, baseFileName, scrapingConfig);
    } else {
      log.warn("未知媒体类型，跳过刮削: {}", fileName);
    }
  }

  /** 使用 TMDB ID 直接刮削 */
  private void scrapeWithTmdbId(
      FileProcessingContext context,
      String fileName,
      String saveDirectory,
      Integer tmdbId,
      MediaInfo mediaInfo) {

    boolean isMovie = MediaFileParser.isVideoFile(fileName);

    try {
      if (isMovie) {
        TmdbMovieDetail movieDetail = tmdbApiService.getMovieDetail(tmdbId);
        if (movieDetail != null) {
          String baseFileName = getStrmCompatibleBaseFileName(fileName);
          scrapMovieWithDetail(mediaInfo, saveDirectory, baseFileName, movieDetail);
        }
      } else {
        TmdbTvDetail tvDetail = tmdbApiService.getTvDetail(tmdbId);
        if (tvDetail != null) {
          String baseFileName = getStrmCompatibleBaseFileName(fileName);
          scrapTvShowWithDetail(mediaInfo, saveDirectory, baseFileName, tvDetail);
        }
      }
    } catch (Exception e) {
      log.error("使用 TMDB ID 刮削失败: {}", tmdbId, e);
    }
  }

  /** 刮削电影 */
  private void scrapMovie(
      MediaInfo mediaInfo,
      String saveDirectory,
      String baseFileName,
      Map<String, Object> scrapingConfig) {
    try {
      TmdbSearchResponse searchResult =
          tmdbApiService.searchMovies(mediaInfo.getSearchQuery(), mediaInfo.getYear());

      if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
        log.warn("刮削失败 - 未找到匹配的电影: {} (年份: {})", mediaInfo.getSearchQuery(), mediaInfo.getYear());
        return;
      }

      TmdbSearchResponse.TmdbSearchResult bestMatch =
          selectBestMatch(searchResult.getResults(), mediaInfo);
      if (bestMatch == null) {
        log.warn("刮削失败 - 未找到合适的电影匹配");
        return;
      }

      TmdbMovieDetail movieDetail = tmdbApiService.getMovieDetail(bestMatch.getId());
      log.info("找到匹配电影: {} ({})", movieDetail.getTitle(), movieDetail.getId());

      scrapMovieWithDetail(mediaInfo, saveDirectory, baseFileName, movieDetail);

    } catch (Exception e) {
      log.error("刮削电影失败: {}", mediaInfo.getSearchQuery(), e);
    }
  }

  /** 使用电影详情刮削 */
  private void scrapMovieWithDetail(
      MediaInfo mediaInfo, String saveDirectory, String baseFileName, TmdbMovieDetail movieDetail) {

    // 生成NFO文件（始终执行）
    String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
    nfoGeneratorService.generateMovieNfo(movieDetail, mediaInfo, nfoFilePath);

    // 下载图片
    String posterUrl = tmdbApiService.buildPosterUrl(movieDetail.getPosterPath());
    String backdropUrl = tmdbApiService.buildBackdropUrl(movieDetail.getBackdropPath());
    coverImageService.downloadImages(posterUrl, backdropUrl, saveDirectory, baseFileName);
  }

  /** 刮削电视剧 */
  private void scrapTvShow(
      MediaInfo mediaInfo,
      String saveDirectory,
      String baseFileName,
      Map<String, Object> scrapingConfig) {
    try {
      TmdbSearchResponse searchResult =
          tmdbApiService.searchTvShows(mediaInfo.getSearchQuery(), mediaInfo.getYear());

      if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
        log.warn("刮削失败 - 未找到匹配的电视剧: {} (年份: {})", mediaInfo.getSearchQuery(), mediaInfo.getYear());
        return;
      }

      TmdbSearchResponse.TmdbSearchResult bestMatch =
          selectBestMatch(searchResult.getResults(), mediaInfo);
      if (bestMatch == null) {
        log.warn("刮削失败 - 未找到合适的电视剧匹配");
        return;
      }

      TmdbTvDetail tvDetail = tmdbApiService.getTvDetail(bestMatch.getId());
      log.info("找到匹配电视剧: {} ({})", tvDetail.getName(), tvDetail.getId());

      scrapTvShowWithDetail(mediaInfo, saveDirectory, baseFileName, tvDetail);

    } catch (Exception e) {
      log.error("刮削电视剧失败: {}", mediaInfo.getSearchQuery(), e);
    }
  }

  /** 使用电视剧详情刮削 */
  private void scrapTvShowWithDetail(
      MediaInfo mediaInfo, String saveDirectory, String baseFileName, TmdbTvDetail tvDetail) {

    // 生成NFO文件（始终执行）
    String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
    nfoGeneratorService.generateTvShowNfo(tvDetail, mediaInfo, nfoFilePath);

    // 下载图片
    String posterUrl = tmdbApiService.buildPosterUrl(tvDetail.getPosterPath());
    String backdropUrl = tmdbApiService.buildBackdropUrl(tvDetail.getBackdropPath());
    coverImageService.downloadImages(posterUrl, backdropUrl, saveDirectory, baseFileName);
  }

  // ==================== 辅助方法 ====================

  private boolean isScrapingEnabled(FileProcessingContext context) {
    Map<String, Object> config = systemConfigService.getScrapingConfig();
    return Boolean.TRUE.equals(config.getOrDefault("enabled", true));
  }

  private boolean isTmdbConfigured(FileProcessingContext context) {
    Map<String, Object> config = systemConfigService.getTmdbConfig();
    String apiKey = (String) config.getOrDefault("apiKey", "");
    return apiKey != null && !apiKey.trim().isEmpty();
  }

  private String extractDirectoryPath(String relativePath) {
    if (relativePath == null || relativePath.isEmpty()) {
      return "";
    }
    try {
      java.nio.file.Path path = java.nio.file.Paths.get(relativePath);
      java.nio.file.Path parent = path.getParent();
      return parent == null ? "" : parent.toString();
    } catch (Exception e) {
      return "";
    }
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
