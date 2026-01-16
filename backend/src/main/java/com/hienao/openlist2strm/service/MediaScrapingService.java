package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.dto.media.AiRecognitionResult;
import com.hienao.openlist2strm.dto.media.MediaInfo;
import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbSearchResponse;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.util.MediaFileParser;
import com.hienao.openlist2strm.util.TmdbIdExtractor;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 媒体刮削服务 整合TMDB API、NFO生成、图片下载等功能 支持从路径中提取TMDB ID直接获取媒体信息
 *
 * @author hienao
 * @since 2024-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MediaScrapingService {

  private final TmdbApiService tmdbApiService;
  private final NfoGeneratorService nfoGeneratorService;
  private final CoverImageService coverImageService;
  private final SystemConfigService systemConfigService;
  private final AiFileNameRecognitionService aiFileNameRecognitionService;
  private final OpenlistApiService openlistApiService;
  private final DataReportService dataReportService;

  /**
   * 执行媒体刮削
   *
   * @param fileName      文件名
   * @param strmDirectory STRM文件目录
   * @param relativePath  相对路径
   */
  public void scrapMedia(
      OpenlistConfig openlistConfig, String fileName, String strmDirectory, String relativePath) {
    scrapMedia(openlistConfig, fileName, strmDirectory, relativePath, null, null);
  }

  /**
   * 执行媒体刮削（优化版本，可传入目录文件列表避免重复API调用）
   *
   * @param fileName       文件名
   * @param strmDirectory  STRM文件目录
   * @param relativePath   相对路径
   * @param directoryFiles 目录文件列表（可选，为null时不会调用API获取）
   * @param fullFilePath   完整的文件路径（用于上报）
   */
  public void scrapMedia(
      OpenlistConfig openlistConfig,
      String fileName,
      String strmDirectory,
      String relativePath,
      List<OpenlistApiService.OpenlistFile> directoryFiles,
      String fullFilePath) {
    try {
      log.info("开始处理媒体文件: {}", fileName);

      // 获取配置选项
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean scrapingEnabled = (Boolean) scrapingConfig.getOrDefault("enabled", true);
      boolean keepSubtitleFiles = (Boolean) scrapingConfig.getOrDefault("keepSubtitleFiles", false);
      boolean useExistingScrapingInfo = (Boolean) scrapingConfig.getOrDefault("useExistingScrapingInfo", false);

      // 构建保存目录（在解析之前就需要知道保存位置）
      String saveDirectory = buildSaveDirectory(strmDirectory, relativePath);

      // 处理字幕文件复制（在解析媒体之前执行）
      // 处理字幕文件复制（在解析媒体之前执行）
      if (keepSubtitleFiles) {
        String[] subtitleExtensions = { ".srt", ".ass", ".vtt", ".ssa", ".sub", ".idx" };
        copyRelatedFiles(openlistConfig, saveDirectory, directoryFiles, subtitleExtensions, "字幕文件");
      }

      // 检查是否优先使用已存在的刮削信息（独立于刮削功能启用状态）
      if (useExistingScrapingInfo) {
        boolean foundNfo = copyRelatedFiles(
            openlistConfig, saveDirectory, directoryFiles, new String[]{".nfo"}, "NFO文件");
        boolean foundImages = copyRelatedFiles(
            openlistConfig, saveDirectory, directoryFiles, 
            new String[]{".jpg", ".jpeg", ".png", ".webp", ".bmp", ".tiff"}, "刮削图片");
            
        if (foundNfo || foundImages) {
          log.info("已复制现有刮削信息，跳过后续处理: {}", fileName);
          return;
        }
      }

      // 检查刮削是否启用
      if (!scrapingEnabled) {
        log.info("刮削功能已禁用，跳过刮削: {}", fileName);
        return;
      }

      // 检查TMDB API Key是否已配置
      Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
      String tmdbApiKey = (String) tmdbConfig.getOrDefault("apiKey", "");
      if (tmdbApiKey == null || tmdbApiKey.trim().isEmpty()) {
        log.warn("TMDB API Key未配置，跳过刮削功能: {}", fileName);
        return;
      }

      // 获取刮削正则配置
      Map<String, Object> regexConfig = systemConfigService.getScrapingRegexConfig();
      @SuppressWarnings("unchecked")
      List<String> movieRegexps = (List<String>) regexConfig.getOrDefault("movieRegexps", Collections.emptyList());
      @SuppressWarnings("unchecked")
      List<String> tvDirRegexps = (List<String>) regexConfig.getOrDefault("tvDirRegexps", Collections.emptyList());
      @SuppressWarnings("unchecked")
      List<String> tvFileRegexps = (List<String>) regexConfig.getOrDefault("tvFileRegexps", Collections.emptyList());

      // 提取目录路径
      String directoryPath = extractDirectoryPath(relativePath);

      // 检查路径中是否包含TMDB ID
      Integer tmdbIdFromPath = TmdbIdExtractor.extractTmdbIdFromPath(relativePath);
      Integer tmdbIdFromFileName = TmdbIdExtractor.extractTmdbIdFromFileName(fileName);
      Integer tmdbId = tmdbIdFromPath != null ? tmdbIdFromPath : tmdbIdFromFileName;

      // 解析文件名
      MediaInfo mediaInfo = MediaFileParser.parse(fileName, directoryPath, movieRegexps, tvDirRegexps, tvFileRegexps);
      log.debug("正则解析媒体信息: {}", mediaInfo);

      // 如果路径中有TMDB ID，直接使用TMDB ID获取信息，跳过文件名解析
      if (tmdbId != null) {
        log.info("检测到路径中的TMDB ID: {}, 直接从TMDB获取信息", tmdbId);

        // 根据文件扩展名判断媒体类型
        boolean isMovie = MediaFileParser.isVideoFile(fileName);

        try {
          if (isMovie) {
            // 直接获取电影详情
            TmdbMovieDetail movieDetail = tmdbApiService.getMovieDetail(tmdbId);
            if (movieDetail != null) {
              log.info("直接获取电影详情成功: {} ({})", movieDetail.getTitle(), movieDetail.getId());

              // 创建MediaInfo对象
              mediaInfo = new MediaInfo()
                  .setType(MediaInfo.MediaType.MOVIE)
                  .setTitle(movieDetail.getTitle())
                  .setYear(
                      String.valueOf(
                          movieDetail.getReleaseDate() != null
                              ? new SimpleDateFormat("yyyy")
                                  .format(movieDetail.getReleaseDate())
                              : null))
                  .setHasYear(movieDetail.getReleaseDate() != null)
                  .setOriginalFileName(fileName)
                  .setConfidence(100); // 使用TMDB ID时置信度为100%

              // 执行电影刮削
              scrapMovieWithDirectInfo(
                  mediaInfo, saveDirectory, getStrmCompatibleBaseFileName(fileName), movieDetail);
              return; // 直接返回，跳过后续逻辑
            }
          } else {
            // 直接获取电视剧详情
            TmdbTvDetail tvDetail = tmdbApiService.getTvDetail(tmdbId);
            if (tvDetail != null) {
              log.info("直接获取电视剧详情成功: {} ({})", tvDetail.getName(), tvDetail.getId());
              // 创建MediaInfo对象
              mediaInfo = new MediaInfo()
                  .setType(MediaInfo.MediaType.TV_SHOW)
                  .setTitle(tvDetail.getName())
                  .setYear(
                      String.valueOf(
                          tvDetail.getFirstAirDate() != null
                              ? new SimpleDateFormat("yyyy").format(tvDetail.getFirstAirDate())
                              : null))
                  .setHasYear(tvDetail.getFirstAirDate() != null)
                  .setOriginalFileName(fileName)
                  .setConfidence(100); // 使用TMDB ID时置信度为100%

              // 尝试从文件名中提取季集信息
              String nameWithoutExt = MediaFileParser.removeFileExtension(fileName);
              List<String> tvFileRegexpsOnly = (List<String>) regexConfig.getOrDefault("tvFileRegexps",
                  Collections.emptyList());
              for (String regex : tvFileRegexpsOnly) {
                try {
                  java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
                      regex, java.util.regex.Pattern.CASE_INSENSITIVE);
                  java.util.regex.Matcher matcher = pattern.matcher(nameWithoutExt);
                  if (matcher.find()) {
                    com.hienao.openlist2strm.util.MediaFileParser.extractNamedGroups(
                        matcher, mediaInfo);
                    log.debug("从文件名中提取季集信息成功: {}", mediaInfo);
                    break;
                  }
                } catch (Exception e) {
                  log.warn("无效的电视剧文件正则表达式: '{}', 错误: {}", regex, e.getMessage());
                }
              }

              // 执行电视剧刮削
              scrapTvShowWithDirectInfo(
                  mediaInfo, saveDirectory, getStrmCompatibleBaseFileName(fileName), tvDetail);
              return; // 直接返回，跳过后续逻辑
            }
          }
        } catch (Exception e) {
          log.error("使用TMDB ID直接获取信息失败: {}", tmdbId, e);
          // 继续使用常规的文件名解析方式
        }
      }

      // 如果正则解析置信度低，尝试使用AI
      if (mediaInfo.getConfidence() < 70) {
        log.info("正则解析置信度低 ({}%)，尝试使用 AI 识别: {}", mediaInfo.getConfidence(), fileName);

        // 上报正则匹配失败事件
        Map<String, Object> regFailProperties = new HashMap<>();
        regFailProperties.put("file_path", fullFilePath != null ? fullFilePath : relativePath);
        regFailProperties.put("file_name", fileName);
        regFailProperties.put("confidence", mediaInfo.getConfidence());
        dataReportService.reportEvent("reg_match_fail", regFailProperties);
        Map<String, Object> aiConfig = systemConfigService.getAiConfig();
        boolean aiRecognitionEnabled = (Boolean) aiConfig.getOrDefault("enabled", false);

        if (aiRecognitionEnabled) {
          AiRecognitionResult aiResult = aiFileNameRecognitionService.recognizeFileName(
              fileName, fullFilePath != null ? fullFilePath : relativePath);
          if (aiResult != null && aiResult.isSuccess()) {
            if (aiResult.isNewFormat()) {
              // 新格式：直接从AI结果构建MediaInfo
              mediaInfo = aiResult.toMediaInfo(fileName);
              log.info("使用 AI 识别结果（新格式）重新解析: {}", mediaInfo);
            } else if (aiResult.isLegacyFormat()) {
              // 旧格式：使用filename字段重新解析
              mediaInfo = MediaFileParser.parse(
                  aiResult.getFilename(),
                  directoryPath,
                  movieRegexps,
                  tvDirRegexps,
                  tvFileRegexps);
              log.info("使用 AI 识别结果（旧格式）重新解析: {}", mediaInfo);
            }
          } else if (aiResult != null && !aiResult.isSuccess()) {
            log.info("AI 无法识别文件名: {}, 原因: {}", fileName, aiResult.getReason());

            // 上报AI识别失败事件
            Map<String, Object> aiFailProperties = new HashMap<>();
            aiFailProperties.put("file_path", fullFilePath != null ? fullFilePath : relativePath);
            aiFailProperties.put("file_name", fileName);
            aiFailProperties.put("reason", aiResult.getReason());
            dataReportService.reportEvent("ai_match_fail", aiFailProperties);
          } else {
            // AI服务调用失败或返回null
            log.warn("AI识别服务调用失败: {}", fileName);

            // 上报AI识别失败事件
            Map<String, Object> aiFailProperties = new HashMap<>();
            aiFailProperties.put("file_path", fullFilePath != null ? fullFilePath : relativePath);
            aiFailProperties.put("file_name", fileName);
            aiFailProperties.put("reason", "AI服务调用失败");
            dataReportService.reportEvent("ai_match_fail", aiFailProperties);
          }
        }
      }

      if (mediaInfo.getConfidence() < 70) {
        log.warn(
            "最终解析置信度过低 ({}%)，跳过刮削: {}", mediaInfo.getConfidence(), mediaInfo.getOriginalFileName());
        return;
      }

      // 获取与STRM文件一致的baseFileName（只移除扩展名，不进行标准化处理）
      String baseFileName = getStrmCompatibleBaseFileName(fileName);

      // 检查是否已刮削（增量模式下跳过已刮削的文件）
      if (isAlreadyScraped(saveDirectory, baseFileName, mediaInfo)) {
        log.info("文件已刮削，跳过: {}", fileName);
        return;
      }

      // 根据媒体类型执行不同的刮削逻辑
      if (mediaInfo.isMovie()) {
        scrapMovie(mediaInfo, saveDirectory, baseFileName);
      } else if (mediaInfo.isTvShow()) {
        scrapTvShow(mediaInfo, saveDirectory, baseFileName);
      } else {
        log.warn("未知媒体类型，跳过刮削: {}", fileName);

        // 上报媒体类型匹配失败事件
        Map<String, Object> mediaTypeFailProperties = new HashMap<>();
        mediaTypeFailProperties.put(
            "file_path", fullFilePath != null ? fullFilePath : relativePath);
        mediaTypeFailProperties.put("file_name", fileName);
        mediaTypeFailProperties.put("media_info", mediaInfo.toString());
        dataReportService.reportEvent("media_type_match_fail", mediaTypeFailProperties);
      }

    } catch (Exception e) {
      log.error("刮削媒体文件失败: {}", fileName, e);
    }
  }

  /** 使用直接获取的电影信息进行刮削 */
  private void scrapMovieWithDirectInfo(
      MediaInfo mediaInfo, String saveDirectory, String baseFileName, TmdbMovieDetail movieDetail) {
    try {
      log.info("使用直接获取的电影信息进行刮削: {} ({})", movieDetail.getTitle(), movieDetail.getId());

      // 生成NFO文件
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean generateNfo = (Boolean) scrapingConfig.getOrDefault("generateNfo", true);

      if (generateNfo) {
        String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
        nfoGeneratorService.generateMovieNfo(movieDetail, mediaInfo, nfoFilePath);
      }

      // 下载图片
      String posterUrl = tmdbApiService.buildPosterUrl(movieDetail.getPosterPath());
      String backdropUrl = tmdbApiService.buildBackdropUrl(movieDetail.getBackdropPath());
      coverImageService.downloadImages(posterUrl, backdropUrl, saveDirectory, baseFileName);

      log.info("直接TMDB ID刮削完成: {}", movieDetail.getTitle());

    } catch (Exception e) {
      log.error("使用直接获取的电影信息刮削失败: {}", movieDetail.getTitle(), e);
    }
  }

  /** 使用直接获取的电视剧信息进行刮削 */
  private void scrapTvShowWithDirectInfo(
      MediaInfo mediaInfo, String saveDirectory, String baseFileName, TmdbTvDetail tvDetail) {
    try {
      log.info("使用直接获取的电视剧信息进行刮削: {} ({})", tvDetail.getName(), tvDetail.getId());

      // 生成NFO文件
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean generateNfo = (Boolean) scrapingConfig.getOrDefault("generateNfo", true);

      if (generateNfo) {
        String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
        nfoGeneratorService.generateTvShowNfo(tvDetail, mediaInfo, nfoFilePath);
      }

      // 下载图片
      String posterUrl = tmdbApiService.buildPosterUrl(tvDetail.getPosterPath());
      String backdropUrl = tmdbApiService.buildBackdropUrl(tvDetail.getBackdropPath());
      coverImageService.downloadImages(posterUrl, backdropUrl, saveDirectory, baseFileName);

      log.info("直接TMDB ID刮削完成: {}", tvDetail.getName());

    } catch (Exception e) {
      log.error("使用直接获取的电视剧信息刮削失败: {}", tvDetail.getName(), e);
    }
  }

  /** 刮削电影 */
  private void scrapMovie(MediaInfo mediaInfo, String saveDirectory, String baseFileName) {
    try {
      // 搜索电影
      TmdbSearchResponse searchResult = tmdbApiService.searchMovies(mediaInfo.getSearchQuery(), mediaInfo.getYear());

      if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
        log.warn(
            "刮削失败 - 未找到匹配的电影: {} (年份: {}), TMDB搜索返回空结果",
            mediaInfo.getSearchQuery(),
            mediaInfo.getYear());
        return;
      }

      // 选择最佳匹配结果
      TmdbSearchResponse.TmdbSearchResult bestMatch = selectBestMovieMatch(searchResult.getResults(), mediaInfo);

      if (bestMatch == null) {
        log.warn(
            "刮削失败 - 未找到合适的电影匹配: {} (年份: {}), 搜索到 {} 个结果但无合适匹配",
            mediaInfo.getSearchQuery(),
            mediaInfo.getYear(),
            searchResult.getResults().size());
        return;
      }

      // 获取详细信息
      TmdbMovieDetail movieDetail = tmdbApiService.getMovieDetail(bestMatch.getId());
      log.info("找到匹配电影: {} ({})", movieDetail.getTitle(), movieDetail.getId());

      // 生成NFO文件
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean generateNfo = (Boolean) scrapingConfig.getOrDefault("generateNfo", true);

      if (generateNfo) {
        String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
        nfoGeneratorService.generateMovieNfo(movieDetail, mediaInfo, nfoFilePath);
      }

      // 下载图片
      String posterUrl = tmdbApiService.buildPosterUrl(movieDetail.getPosterPath());
      String backdropUrl = tmdbApiService.buildBackdropUrl(movieDetail.getBackdropPath());
      coverImageService.downloadImages(posterUrl, backdropUrl, saveDirectory, baseFileName);

    } catch (Exception e) {
      log.error("刮削电影失败: {}", mediaInfo.getSearchQuery(), e);
    }
  }

  /** 刮削电视剧 */
  private void scrapTvShow(MediaInfo mediaInfo, String saveDirectory, String baseFileName) {
    try {
      // 搜索电视剧
      TmdbSearchResponse searchResult = tmdbApiService.searchTvShows(mediaInfo.getSearchQuery(), mediaInfo.getYear());

      if (searchResult.getResults() == null || searchResult.getResults().isEmpty()) {
        log.warn(
            "刮削失败 - 未找到匹配的电视剧: {} (年份: {}), TMDB搜索返回空结果",
            mediaInfo.getSearchQuery(),
            mediaInfo.getYear());
        return;
      }

      // 选择最佳匹配结果
      TmdbSearchResponse.TmdbSearchResult bestMatch = selectBestTvMatch(searchResult.getResults(), mediaInfo);

      if (bestMatch == null) {
        log.warn(
            "刮削失败 - 未找到合适的电视剧匹配: {} (年份: {}), 搜索到 {} 个结果但无合适匹配",
            mediaInfo.getSearchQuery(),
            mediaInfo.getYear(),
            searchResult.getResults().size());
        return;
      }

      // 获取详细信息
      TmdbTvDetail tvDetail = tmdbApiService.getTvDetail(bestMatch.getId());
      log.info("找到匹配电视剧: {} ({})", tvDetail.getName(), tvDetail.getId());

      // 生成NFO文件
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean generateNfo = (Boolean) scrapingConfig.getOrDefault("generateNfo", true);

      if (generateNfo) {
        String nfoFilePath = Paths.get(saveDirectory, baseFileName + ".nfo").toString();
        nfoGeneratorService.generateTvShowNfo(tvDetail, mediaInfo, nfoFilePath);
      }

      // 下载图片
      String posterUrl = tmdbApiService.buildPosterUrl(tvDetail.getPosterPath());
      String backdropUrl = tmdbApiService.buildBackdropUrl(tvDetail.getBackdropPath());
      coverImageService.downloadImages(posterUrl, backdropUrl, saveDirectory, baseFileName);

    } catch (Exception e) {
      log.error("刮削电视剧失败: {}", mediaInfo.getSearchQuery(), e);
    }
  }

  /** 选择最佳电影匹配结果 */
  private TmdbSearchResponse.TmdbSearchResult selectBestMovieMatch(
      List<TmdbSearchResponse.TmdbSearchResult> results, MediaInfo mediaInfo) {

    if (results == null || results.isEmpty()) {
      return null;
    }

    // 如果只有一个结果，直接返回
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

  /** 选择最佳电视剧匹配结果 */
  private TmdbSearchResponse.TmdbSearchResult selectBestTvMatch(
      List<TmdbSearchResponse.TmdbSearchResult> results, MediaInfo mediaInfo) {

    if (results == null || results.isEmpty()) {
      return null;
    }

    // 如果只有一个结果，直接返回
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

  /** 构建保存目录路径 */
  private String buildSaveDirectory(String strmDirectory, String relativePath) {
    if (relativePath == null || relativePath.isEmpty()) {
      return strmDirectory;
    }

    return Paths.get(strmDirectory, relativePath).toString();
  }

  /**
   * 从相对路径中提取目录部分
   *
   * @param relativePath 文件的相对路径
   * @return 文件所在的目录路径
   */
  private String extractDirectoryPath(String relativePath) {
    if (relativePath == null || relativePath.isEmpty()) {
      return "";
    }
    try {
      Path path = Paths.get(relativePath);
      Path parent = path.getParent();
      return parent == null ? "" : parent.toString();
    } catch (Exception e) {
      log.warn("无法从相对路径中提取目录: {}", relativePath, e);
      return "";
    }
  }

  /** 检查是否应该执行刮削 */
  public boolean shouldScrap(String fileName) {
    // 检查是否为视频文件
    if (!MediaFileParser.isVideoFile(fileName)) {
      return false;
    }

    // 检查刮削配置
    Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
    boolean scrapingEnabled = (Boolean) scrapingConfig.getOrDefault("enabled", true);

    if (!scrapingEnabled) {
      return false;
    }

    // 检查TMDB API Key是否已配置
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String tmdbApiKey = (String) tmdbConfig.getOrDefault("apiKey", "");
    if (tmdbApiKey == null || tmdbApiKey.trim().isEmpty()) {
      return false;
    }

    return true;
  }

  /**
   * 检查文件是否已经被刮削过
   *
   * @param saveDirectory 保存目录
   * @param baseFileName  基础文件名
   * @param mediaInfo     媒体信息
   * @return 是否已刮削
   */
  private boolean isAlreadyScraped(String saveDirectory, String baseFileName, MediaInfo mediaInfo) {
    try {
      Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
      boolean generateNfo = (Boolean) scrapingConfig.getOrDefault("generateNfo", true);
      boolean downloadPoster = (Boolean) scrapingConfig.getOrDefault("downloadPoster", true);
      boolean downloadBackdrop = (Boolean) scrapingConfig.getOrDefault("downloadBackdrop", false);

      // 确保保存目录存在
      File saveDir = new File(saveDirectory);
      if (!saveDir.exists()) {
        log.debug("保存目录不存在，需要刮削: {}", saveDirectory);
        return false;
      }

      // 检查单个文件的刮削文件
      if (mediaInfo.isMovie()) {
        return isMovieScraped(
            saveDirectory, baseFileName, generateNfo, downloadPoster, downloadBackdrop);
      } else if (mediaInfo.isTvShow()) {
        return isTvShowEpisodeScraped(
            saveDirectory, baseFileName, generateNfo, downloadPoster, downloadBackdrop);
      }

      return false;

    } catch (Exception e) {
      log.warn("检查刮削状态时出错，继续刮削: {}", baseFileName, e);
      return false;
    }
  }

  /** 检查电影是否已刮削 */
  private boolean isMovieScraped(
      String saveDirectory,
      String baseFileName,
      boolean generateNfo,
      boolean downloadPoster,
      boolean downloadBackdrop) {
    // 检查 NFO 文件 - 如果目录中存在任何NFO文件就视为已刮削
    if (generateNfo) {
      File saveDir = new File(saveDirectory);
      if (saveDir.exists() && saveDir.isDirectory()) {
        File[] nfoFiles = saveDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".nfo"));
        if (nfoFiles == null || nfoFiles.length == 0) {
          log.debug("目录中没有NFO文件，需要刮削: {}", saveDirectory);
          return false;
        }
        log.debug("目录中存在NFO文件，视为已刮削: {}", saveDirectory);
      } else {
        log.debug("保存目录不存在，需要刮削: {}", saveDirectory);
        return false;
      }
    }

    // 检查海报文件
    if (downloadPoster) {
      String posterPath = saveDirectory + "/" + baseFileName + "-poster.jpg";
      if (!new File(posterPath).exists()) {
        log.debug("电影海报文件不存在，需要刮削: {}", posterPath);
        return false;
      }
    }

    // 检查背景图文件
    if (downloadBackdrop) {
      String backdropPath = saveDirectory + "/" + baseFileName + "-fanart.jpg";
      if (!new File(backdropPath).exists()) {
        log.debug("电影背景图文件不存在，需要刮削: {}", backdropPath);
        return false;
      }
    }

    log.debug("电影所有刮削文件都已存在，跳过刮削: {}", baseFileName);
    return true;
  }

  /** 检查电视剧集是否已刮削 */
  private boolean isTvShowEpisodeScraped(
      String saveDirectory,
      String baseFileName,
      boolean generateNfo,
      boolean downloadPoster,
      boolean downloadBackdrop) {
    // 检查NFO文件 - 如果目录中存在任何NFO文件就视为已刮削
    if (generateNfo) {
      File saveDir = new File(saveDirectory);
      if (saveDir.exists() && saveDir.isDirectory()) {
        File[] nfoFiles = saveDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".nfo"));
        if (nfoFiles == null || nfoFiles.length == 0) {
          log.debug("目录中没有NFO文件，需要刮削: {}", saveDirectory);
          return false;
        }
        log.debug("目录中存在NFO文件，视为已刮削: {}", saveDirectory);
      } else {
        log.debug("保存目录不存在，需要刮削: {}", saveDirectory);
        return false;
      }
    }

    // 检查剧集海报文件
    if (downloadPoster) {
      String episodePosterPath = saveDirectory + "/" + baseFileName + "-thumb.jpg";
      if (!new File(episodePosterPath).exists()) {
        log.debug("剧集海报文件不存在，需要刮削: {}", episodePosterPath);
        return false;
      }
    }

    // 检查电视剧海报和背景图（在剧集目录的父目录或当前目录）
    if (downloadPoster) {
      String tvShowPosterPath = saveDirectory + "/poster.jpg";
      if (!new File(tvShowPosterPath).exists()) {
        log.debug("电视剧海报文件不存在，需要刮削: {}", tvShowPosterPath);
        return false;
      }
    }

    if (downloadBackdrop) {
      String tvShowBackdropPath = saveDirectory + "/fanart.jpg";
      if (!new File(tvShowBackdropPath).exists()) {
        log.debug("电视剧背景图文件不存在，需要刮削: {}", tvShowBackdropPath);
        return false;
      }
    }

    log.debug("电视剧集所有刮削文件都已存在，跳过刮削: {}", baseFileName);
    return true;
  }

  /**
   * 检查目录是否已完全刮削 用于批量处理时的目录级别检查
   *
   * @param directoryPath 目录路径
   * @return 是否已完全刮削
   */
  public boolean isDirectoryFullyScraped(String directoryPath) {
    try {
      File directory = new File(directoryPath);
      if (!directory.exists() || !directory.isDirectory()) {
        return false;
      }

      // 获取刮削正则配置
      Map<String, Object> regexConfig = systemConfigService.getScrapingRegexConfig();
      @SuppressWarnings("unchecked")
      List<String> movieRegexps = (List<String>) regexConfig.getOrDefault("movieRegexps", Collections.emptyList());
      @SuppressWarnings("unchecked")
      List<String> tvDirRegexps = (List<String>) regexConfig.getOrDefault("tvDirRegexps", Collections.emptyList());
      @SuppressWarnings("unchecked")
      List<String> tvFileRegexps = (List<String>) regexConfig.getOrDefault("tvFileRegexps", Collections.emptyList());

      File[] files = directory.listFiles();
      if (files == null || files.length == 0) {
        return false;
      }

      boolean hasVideoFiles = false;
      boolean allVideoFilesScraped = true;

      for (File file : files) {
        if (file.isFile() && MediaFileParser.isVideoFile(file.getName())) {
          hasVideoFiles = true;

          // 使用新的解析器
          MediaInfo mediaInfo = MediaFileParser.parse(
              file.getName(), directoryPath, movieRegexps, tvDirRegexps, tvFileRegexps);

          if (mediaInfo.getConfidence() >= 70) {
            String baseFileName = coverImageService.getStandardizedFileName(file.getName());
            if (!isAlreadyScraped(directoryPath, baseFileName, mediaInfo)) {
              allVideoFilesScraped = false;
              break;
            }
          } else {
            // 如果任何一个文件解析失败，则认为目录没有完全刮削
            allVideoFilesScraped = false;
            break;
          }
        }
      }

      boolean result = hasVideoFiles && allVideoFilesScraped;
      if (result) {
        log.debug("目录已完全刮削: {}", directoryPath);
      } else {
        log.debug(
            "目录需要刮削: {} (hasVideoFiles: {}, allScraped: {})",
            directoryPath,
            hasVideoFiles,
            allVideoFilesScraped);
      }

      return result;

    } catch (Exception e) {
      log.warn("检查目录刮削状态时出错: {}", directoryPath, e);
      return false;
    }
  }

  /** 获取刮削统计信息 */
  public Map<String, Object> getScrapingStats() {
    // 检查刮削功能是否启用
    Map<String, Object> scrapingConfig = systemConfigService.getScrapingConfig();
    boolean scrapingEnabled = (Boolean) scrapingConfig.getOrDefault("enabled", true);

    // 检查TMDB API Key是否已配置
    Map<String, Object> tmdbConfig = systemConfigService.getTmdbConfig();
    String tmdbApiKey = (String) tmdbConfig.getOrDefault("apiKey", "");
    boolean tmdbConfigured = tmdbApiKey != null && !tmdbApiKey.trim().isEmpty();

    return Map.of(
        "enabled", scrapingEnabled,
        "tmdbConfigured", tmdbConfigured,
        "canScrap", scrapingEnabled && tmdbConfigured);
  }

  /**
   * 复制字幕文件到STRM目录
   *
   * @param fileName       媒体文件名
   * @param relativePath   相对路径
   * @param saveDirectory  保存目录
   * @param directoryFiles 目录文件列表（可选，为null时不会调用API获取）
   */
  /**
   * 复制目录中的相关文件（字幕、NFO、图片等）
   *
   * @param openlistConfig    OpenList配置
   * @param saveDirectory     保存目录
   * @param directoryFiles    目录文件列表
   * @param allowedExtensions 允许的文件后缀数组
   * @param fileTypeDescription 文件类型描述（用于日志）
   * @return 是否成功复制了至少一个文件
   */
  private boolean copyRelatedFiles(
      OpenlistConfig openlistConfig,
      String saveDirectory,
      List<OpenlistApiService.OpenlistFile> directoryFiles,
      String[] allowedExtensions,
      String fileTypeDescription) {
    try {
      if (directoryFiles == null || directoryFiles.isEmpty()) {
        return false;
      }

      boolean foundAndCopied = false;

      // 遍历目录文件
      for (OpenlistApiService.OpenlistFile file : directoryFiles) {
        if ("file".equals(file.getType())) {
          String fileName_lower = file.getName().toLowerCase();
          boolean isMatch = false;
          for (String ext : allowedExtensions) {
            if (fileName_lower.endsWith(ext)) {
              isMatch = true;
              break;
            }
          }

          if (isMatch) {
            log.debug("准备复制{}文件: {}", fileTypeDescription, file.getName());

            // 下载文件内容 (不使用URL编码)
            byte[] content = openlistApiService.getFileContent(openlistConfig, file, false);

            if (content != null && content.length > 0) {
              Path targetFile = Paths.get(saveDirectory, file.getName());
              Files.createDirectories(targetFile.getParent());
              Files.write(targetFile, content);

              log.info(
                  "已复制{}: {} -> {} (大小: {} bytes)",
                  fileTypeDescription,
                  file.getName(),
                  targetFile,
                  content.length);
              foundAndCopied = true;
            } else {
              log.debug("{}内容为空: {}", fileTypeDescription, file.getName());
            }
          }
        }
      }
      return foundAndCopied;
    } catch (Exception e) {
      log.warn("复制{}失败: {}", fileTypeDescription, e.getMessage());
      return false;
    }
  }

  /**
   * 检测文件内容类型（基于文件头）
   *
   * @param content 文件内容字节数组
   * @return 文件类型描述
   */
  private String detectFileType(byte[] content) {
    if (content == null || content.length < 4) {
      return "UNKNOWN";
    }

    // 检查常见的文件头
    // JPEG: FF D8 FF
    if (content.length >= 3
        && (content[0] & 0xFF) == 0xFF
        && (content[1] & 0xFF) == 0xD8
        && (content[2] & 0xFF) == 0xFF) {
      return "JPEG";
    }

    // PNG: 89 50 4E 47
    if (content.length >= 4
        && (content[0] & 0xFF) == 0x89
        && (content[1] & 0xFF) == 0x50
        && (content[2] & 0xFF) == 0x4E
        && (content[3] & 0xFF) == 0x47) {
      return "PNG";
    }

    // GIF: 47 49 46 38
    if (content.length >= 4
        && (content[0] & 0xFF) == 0x47
        && (content[1] & 0xFF) == 0x49
        && (content[2] & 0xFF) == 0x46
        && (content[3] & 0xFF) == 0x38) {
      return "GIF";
    }

    // WebP: 52 49 46 46 (RIFF) + WebP signature
    if (content.length >= 12
        && (content[0] & 0xFF) == 0x52
        && (content[1] & 0xFF) == 0x49
        && (content[2] & 0xFF) == 0x46
        && (content[3] & 0xFF) == 0x46) {
      if ((content[8] & 0xFF) == 0x57
          && (content[9] & 0xFF) == 0x45
          && (content[10] & 0xFF) == 0x42
          && (content[11] & 0xFF) == 0x50) {
        return "WEBP";
      }
    }

    // 检查是否是XML/NFO文件（以 < 开头）
    if ((content[0] & 0xFF) == 0x3C) {
      return "XML/NFO";
    }

    // 检查是否是文本文件（前100个字节都是可打印字符）
    boolean isText = true;
    int checkLength = Math.min(content.length, 100);
    for (int i = 0; i < checkLength; i++) {
      int b = content[i] & 0xFF;
      if (b < 32 && b != 9 && b != 10 && b != 13) { // 不是制表符、换行符、回车符的控制字符
        isText = false;
        break;
      }
    }

    if (isText) {
      return "TEXT";
    }

    return "BINARY";
  }

  /**
   * 获取与STRM文件一致的baseFileName 只移除扩展名，不进行标准化处理，确保与STRM文件命名一致
   *
   * @param fileName 原始文件名
   * @return 与STRM文件一致的baseFileName
   */
  private String getStrmCompatibleBaseFileName(String fileName) {
    if (fileName == null || fileName.isEmpty()) {
      return "unknown";
    }

    // 只移除扩展名，保持与StrmFileService.processFileName()中的逻辑一致
    int lastDotIndex = fileName.lastIndexOf('.');
    if (lastDotIndex > 0) {
      return fileName.substring(0, lastDotIndex);
    }
    return fileName;
  }
}
