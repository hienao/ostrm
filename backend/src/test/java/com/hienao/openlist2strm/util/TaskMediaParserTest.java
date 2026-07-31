package com.hienao.openlist2strm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hienao.openlist2strm.dto.media.MediaInfo;
import org.junit.jupiter.api.Test;

class TaskMediaParserTest {

  @Test
  void recognizesSeasonDirectoryAliasesAndSpecials() {
    assertEquals(1, TaskMediaParser.parseSeasonNumber("Season 1"));
    assertEquals(2, TaskMediaParser.parseSeasonNumber("S02"));
    assertEquals(3, TaskMediaParser.parseSeasonNumber("第3季"));
    assertEquals(0, TaskMediaParser.parseSeasonNumber("Specials"));
    assertEquals(0, TaskMediaParser.parseSeasonNumber("特别篇"));
    assertNull(TaskMediaParser.parseSeasonNumber("黑袍纠察队 第四季 1080p Remux"));
  }

  @Test
  void movieUsesImmediateParentDirectory() {
    MediaInfo result =
        parse("Inception.1080p.mkv", "Inception (2010)/Inception.1080p.mkv", "movie");

    assertEquals(MediaInfo.MediaType.MOVIE, result.getType());
    assertEquals("Inception", result.getDisplayTitle());
    assertEquals("2010", result.getYear());
    assertTrue(result.getConfidence() >= 70);
  }

  @Test
  void tvUsesShowAndSeasonDirectories() {
    MediaInfo result =
        parse(
            "Breaking.Bad.S05E14.1080p.mkv",
            "Breaking Bad/Season 05/Breaking.Bad.S05E14.1080p.mkv",
            "tv");

    assertEquals(MediaInfo.MediaType.TV_SHOW, result.getType());
    assertEquals("Breaking Bad", result.getDisplayTitle());
    assertEquals(5, result.getSeason());
    assertEquals(14, result.getEpisode());
    assertTrue(result.getConfidence() >= 70);
  }

  @Test
  void animeSupportsAbsoluteEpisodeNumbers() {
    MediaInfo result =
        parse(
            "[ANi] Sousou no Frieren - 05 [1080P].mkv",
            "葬送的芙莉莲/Season 01/[ANi] Sousou no Frieren - 05 [1080P].mkv",
            "anime");

    assertEquals(MediaInfo.MediaType.TV_SHOW, result.getType());
    assertEquals("葬送的芙莉莲", result.getDisplayTitle());
    assertEquals(1, result.getSeason());
    assertEquals(5, result.getEpisode());
  }

  @Test
  void animeDefaultsToSeasonOneWithoutSeasonDirectory() {
    MediaInfo result = parse("[1071].mkv", "海贼王/[1071].mkv", "anime");

    assertEquals("海贼王", result.getDisplayTitle());
    assertEquals(1, result.getSeason());
    assertEquals(1071, result.getEpisode());
  }

  @Test
  void explicitMovieTypeDoesNotBecomeTv() {
    MediaInfo result =
        parse(
            "Movie.Title.S01E01.2024.mkv",
            "Movie Title (2024)/Movie.Title.S01E01.2024.mkv",
            "movie");

    assertEquals(MediaInfo.MediaType.MOVIE, result.getType());
  }

  private MediaInfo parse(String fileName, String relativePath, String libraryType) {
    return TaskMediaParser.parse(
        fileName,
        relativePath,
        EnhancedRegexPatterns.getEnhancedMovieRegexps(),
        EnhancedRegexPatterns.getEnhancedTvDirRegexps(),
        EnhancedRegexPatterns.getEnhancedTvFileRegexps(),
        libraryType);
  }
}
