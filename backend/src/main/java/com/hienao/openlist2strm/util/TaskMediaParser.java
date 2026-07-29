package com.hienao.openlist2strm.util;

import com.hienao.openlist2strm.dto.media.MediaInfo;
import com.hienao.openlist2strm.entity.MediaLibraryType;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 根据任务媒体库类型解析相对于任务根目录的媒体路径。 */
public final class TaskMediaParser {

  private static final Pattern YEAR_PATTERN =
      Pattern.compile("(?i)^(.*?)[\\s._\\-\\[(]+((?:19|20)\\d{2})[\\])]?.*$");
  private static final Pattern SEASON_PATTERN =
      Pattern.compile("(?i)^(?:season|s)[\\s._-]*(\\d{1,2})$");
  private static final Pattern CHINESE_SEASON_PATTERN = Pattern.compile("^第(\\d{1,2})季$");
  private static final Pattern SEASON_EPISODE_PATTERN =
      Pattern.compile("(?i)(?:^|[\\s._\\-\\[])S(\\d{1,2})[\\s._-]*E(\\d{1,4})(?:\\D|$)");
  private static final Pattern X_EPISODE_PATTERN =
      Pattern.compile("(?i)(?:^|\\D)(\\d{1,2})x(\\d{1,4})(?:\\D|$)");
  private static final Pattern EPISODE_PATTERN =
      Pattern.compile("(?i)(?:^|[\\s._\\-\\[])E(?:PISODE|P)?[\\s._-]*(\\d{1,4})(?:\\D|$)");
  private static final Pattern ABSOLUTE_EPISODE_PATTERN =
      Pattern.compile("(?i)(?:^|[\\s._\\-\\[])(\\d{1,4})(?:v\\d+)?(?:[\\s._\\-\\]]|$)");
  private static final Pattern RELEASE_TAG_PATTERN =
      Pattern.compile(
          "(?i)\\b(?:2160p|1080p|720p|480p|4k|uhd|hdr|bluray|web-?dl|webrip|"
              + "hdtv|x26[45]|h26[45]|hevc|avc|aac|dts|flac|10bit)\\b");
  private static final Pattern SEPARATORS = Pattern.compile("[._\\-\\[\\]()]+");

  private TaskMediaParser() {}

  public static MediaInfo parse(
      String fileName,
      String relativePath,
      List<String> movieRegexps,
      List<String> tvDirRegexps,
      List<String> tvFileRegexps,
      String libraryTypeValue) {
    MediaLibraryType libraryType = MediaLibraryType.from(libraryTypeValue);
    String directoryPath = extractDirectoryPath(relativePath);

    return switch (libraryType) {
      case MOVIE -> parseMovie(fileName, directoryPath, movieRegexps);
      case TV -> parseSeries(fileName, directoryPath, tvDirRegexps, tvFileRegexps, false);
      case ANIME -> parseSeries(fileName, directoryPath, tvDirRegexps, tvFileRegexps, true);
      case AUTO ->
          MediaFileParser.parse(fileName, directoryPath, movieRegexps, tvDirRegexps, tvFileRegexps);
    };
  }

  private static MediaInfo parseMovie(
      String fileName, String directoryPath, List<String> movieRegexps) {
    String directoryName = lastPathSegment(directoryPath);
    if (!directoryName.isBlank()) {
      MediaInfo directoryResult =
          MediaFileParser.parse(
              directoryName + ".video",
              "",
              movieRegexps,
              Collections.emptyList(),
              Collections.emptyList());
      if (directoryResult.isMovie()) {
        directoryResult.setOriginalFileName(fileName);
        return directoryResult;
      }
    }

    MediaInfo fileResult =
        MediaFileParser.parse(
            fileName, "", movieRegexps, Collections.emptyList(), Collections.emptyList());
    if (fileResult.isMovie()) {
      return fileResult;
    }

    String titleSource =
        !directoryName.isBlank() ? directoryName : MediaFileParser.removeFileExtension(fileName);
    ParsedTitle parsedTitle = parseTitle(titleSource);
    return new MediaInfo()
        .setType(MediaInfo.MediaType.MOVIE)
        .setTitle(parsedTitle.title())
        .setCleanTitle(parsedTitle.title())
        .setYear(parsedTitle.year())
        .setHasYear(parsedTitle.year() != null)
        .setOriginalFileName(fileName)
        .setConfidence(parsedTitle.year() == null ? 60 : 80);
  }

  private static MediaInfo parseSeries(
      String fileName,
      String directoryPath,
      List<String> tvDirRegexps,
      List<String> tvFileRegexps,
      boolean anime) {
    MediaInfo regexResult =
        MediaFileParser.parse(
            fileName, directoryPath, Collections.emptyList(), tvDirRegexps, tvFileRegexps);

    List<String> directories = splitPath(directoryPath);
    Integer season = regexResult.getSeason();
    String titleSource = null;

    for (int i = 0; i < directories.size(); i++) {
      Integer directorySeason = parseSeason(directories.get(i));
      if (directorySeason != null) {
        season = season != null ? season : directorySeason;
        if (i > 0) {
          titleSource = directories.get(i - 1);
        }
        break;
      }
    }
    if (titleSource == null && !directories.isEmpty()) {
      titleSource = directories.get(directories.size() - 1);
    }
    if (titleSource == null || titleSource.isBlank()) {
      titleSource = regexResult.getTitle();
    }

    EpisodeInfo episodeInfo = parseEpisode(fileName, anime);
    if (episodeInfo.season() != null) {
      season = episodeInfo.season();
    }
    Integer episode =
        episodeInfo.episode() != null ? episodeInfo.episode() : regexResult.getEpisode();
    if (anime && episode != null && season == null) {
      season = 1;
    }

    ParsedTitle parsedTitle = parseTitle(titleSource);
    int confidence = 20;
    if (parsedTitle.title() != null && !parsedTitle.title().isBlank()) {
      confidence += 40;
    }
    if (season != null) {
      confidence += 10;
    }
    if (episode != null) {
      confidence += 20;
    }
    if (parsedTitle.year() != null) {
      confidence += 10;
    }

    return new MediaInfo()
        .setType(MediaInfo.MediaType.TV_SHOW)
        .setTitle(parsedTitle.title())
        .setCleanTitle(parsedTitle.title())
        .setYear(parsedTitle.year())
        .setSeason(season)
        .setEpisode(episode)
        .setHasYear(parsedTitle.year() != null)
        .setHasSeasonEpisode(season != null && episode != null)
        .setOriginalFileName(fileName)
        .setConfidence(Math.min(100, confidence));
  }

  private static EpisodeInfo parseEpisode(String fileName, boolean allowAbsoluteEpisode) {
    String name = MediaFileParser.removeFileExtension(fileName);
    Matcher matcher = SEASON_EPISODE_PATTERN.matcher(name);
    if (matcher.find()) {
      return new EpisodeInfo(
          Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }
    matcher = X_EPISODE_PATTERN.matcher(name);
    if (matcher.find()) {
      return new EpisodeInfo(
          Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }
    matcher = EPISODE_PATTERN.matcher(name);
    if (matcher.find()) {
      return new EpisodeInfo(null, Integer.parseInt(matcher.group(1)));
    }
    if (allowAbsoluteEpisode) {
      matcher = ABSOLUTE_EPISODE_PATTERN.matcher(name);
      while (matcher.find()) {
        int candidate = Integer.parseInt(matcher.group(1));
        if (candidate > 0 && candidate < 10000 && !isReleaseNumber(candidate)) {
          return new EpisodeInfo(null, candidate);
        }
      }
    }
    return new EpisodeInfo(null, null);
  }

  private static boolean isReleaseNumber(int value) {
    return value == 480 || value == 720 || value == 1080 || value == 2160;
  }

  private static Integer parseSeason(String directory) {
    Matcher matcher = SEASON_PATTERN.matcher(directory.trim());
    if (matcher.matches()) {
      return Integer.parseInt(matcher.group(1));
    }
    matcher = CHINESE_SEASON_PATTERN.matcher(directory.trim());
    return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
  }

  /** 判断目录名是否为任务目录结构支持的季目录。 */
  public static boolean isSeasonDirectory(String directory) {
    return directory != null && parseSeason(directory) != null;
  }

  private static ParsedTitle parseTitle(String value) {
    if (value == null || value.isBlank()) {
      return new ParsedTitle(null, null);
    }
    String title = value.trim();
    String year = null;
    Matcher yearMatcher = YEAR_PATTERN.matcher(title);
    if (yearMatcher.matches()) {
      title = yearMatcher.group(1);
      year = yearMatcher.group(2);
    }
    title = RELEASE_TAG_PATTERN.matcher(title).replaceAll(" ");
    title = SEPARATORS.matcher(title).replaceAll(" ").replaceAll("\\s+", " ").trim();
    return new ParsedTitle(title, year);
  }

  private static String extractDirectoryPath(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) {
      return "";
    }
    try {
      Path parent = Paths.get(relativePath).getParent();
      return parent == null ? "" : parent.toString();
    } catch (Exception e) {
      return "";
    }
  }

  private static List<String> splitPath(String directoryPath) {
    if (directoryPath == null || directoryPath.isBlank()) {
      return Collections.emptyList();
    }
    return Pattern.compile("[/\\\\]+")
        .splitAsStream(directoryPath)
        .filter(segment -> !segment.isBlank())
        .toList();
  }

  private static String lastPathSegment(String directoryPath) {
    List<String> segments = splitPath(directoryPath);
    return segments.isEmpty() ? "" : segments.get(segments.size() - 1);
  }

  private record ParsedTitle(String title, String year) {}

  private record EpisodeInfo(Integer season, Integer episode) {}
}
