package com.hienao.openlist2strm.dto.media;

import com.hienao.openlist2strm.entity.MediaLibraryType;
import java.util.List;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * AI识别结果DTO 用于承载AI返回的结构化数据，支持新旧两种格式
 *
 * @author hienao
 * @since 2024-01-01
 */
@Data
@Accessors(chain = true)
public class AiRecognitionResult {

  /** 识别是否成功 */
  private boolean success;

  /** 媒体类型 */
  private String type;

  /** 失败原因（仅失败时） */
  private String reason;

  // === 新格式字段（分离字段） ===

  /** 标题 */
  private String title;

  /** 可用于 TMDB 搜索的候选标题，按优先级排序 */
  private List<String> titleCandidates;

  /** 年份 */
  private String year;

  /** 季数（电视剧） */
  private Integer season;

  /** 集数（电视剧） */
  private Integer episode;

  // === 旧格式字段（兼容性） ===

  /** 标准化文件名（旧格式兼容） */
  private String filename;

  /**
   * 判断是否为新格式 新格式包含分离的title字段
   *
   * @return 是否为新格式
   */
  public boolean isNewFormat() {
    return title != null && !title.trim().isEmpty();
  }

  /**
   * 判断是否为旧格式 旧格式只包含filename字段
   *
   * @return 是否为旧格式
   */
  public boolean isLegacyFormat() {
    return !isNewFormat() && filename != null && !filename.trim().isEmpty();
  }

  /**
   * 获取媒体类型枚举
   *
   * @return 媒体类型
   */
  public MediaInfo.MediaType getMediaType() {
    if (type == null) {
      return MediaInfo.MediaType.UNKNOWN;
    }
    switch (type.toLowerCase()) {
      case "movie":
        return MediaInfo.MediaType.MOVIE;
      case "tv":
      case "tv_show":
        return MediaInfo.MediaType.TV_SHOW;
      default:
        return MediaInfo.MediaType.UNKNOWN;
    }
  }

  /**
   * 构建MediaInfo对象 根据新旧格式自动选择构建方式
   *
   * @param originalFileName 原始文件名
   * @return MediaInfo对象
   */
  public MediaInfo toMediaInfo(String originalFileName) {
    return toMediaInfo(originalFileName, MediaLibraryType.AUTO.value());
  }

  /**
   * 根据任务媒体库类型构建媒体信息。任务的明确类型优先于模型推断。
   *
   * @param originalFileName 原始文件名
   * @param libraryTypeValue 任务媒体库类型
   * @return MediaInfo对象
   */
  public MediaInfo toMediaInfo(String originalFileName, String libraryTypeValue) {
    MediaLibraryType libraryType = MediaLibraryType.from(libraryTypeValue);
    MediaInfo.MediaType resolvedType =
        libraryType == MediaLibraryType.MOVIE
            ? MediaInfo.MediaType.MOVIE
            : libraryType.isTvLike() ? MediaInfo.MediaType.TV_SHOW : getMediaType();
    MediaInfo mediaInfo =
        new MediaInfo().setOriginalFileName(originalFileName).setType(resolvedType);

    if (isNewFormat()) {
      // 新格式：直接使用分离的字段
      mediaInfo
          .setTitle(title)
          .setYear(year)
          .setSeason(season)
          .setEpisode(episode)
          .setHasYear(year != null && !year.trim().isEmpty())
          .setHasSeasonEpisode(season != null && episode != null)
          .setConfidence(95); // 新格式置信度较高
    } else if (isLegacyFormat()) {
      // 旧格式：需要通过MediaFileParser重新解析
      // 这里只设置基本信息，具体解析在调用方处理
      mediaInfo.setTitle(filename).setConfidence(80); // 旧格式置信度中等
    } else {
      // 无效格式
      mediaInfo.setType(MediaInfo.MediaType.UNKNOWN).setConfidence(0);
    }

    return mediaInfo;
  }

  @Override
  public String toString() {
    if (isNewFormat()) {
      return String.format(
          "AiRecognitionResult{success=%s, type='%s', title='%s', year='%s', season=%d,"
              + " episode=%d}",
          success, type, title, year, season, episode);
    } else {
      return String.format(
          "AiRecognitionResult{success=%s, type='%s', filename='%s'}", success, type, filename);
    }
  }
}
