package com.hienao.openlist2strm.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SeasonDirectoryNameParserTest {

  @Test
  void matchesContainedChineseSeasonNumber() {
    var result = SeasonDirectoryNameParser.parseForRename("黑袍纠察队 第四季 1080p Remux");

    assertTrue(result.matched());
    assertEquals(4, result.seasonNumber());
    assertEquals(SeasonDirectoryNameParser.MatchType.CHINESE_SEASON, result.matchType());
    assertEquals(12, SeasonDirectoryNameParser.parseForRename("某剧 第十二季 Remux").seasonNumber());
  }

  @Test
  void followsPriorityWhenMultipleMarkersHaveTheSameSeason() {
    var result = SeasonDirectoryNameParser.parseForRename("The Boys S04 Season 4 第四季");

    assertTrue(result.matched());
    assertEquals(4, result.seasonNumber());
    assertEquals(SeasonDirectoryNameParser.MatchType.CHINESE_SEASON, result.matchType());
  }

  @Test
  void rejectsDifferentSeasonNumbersAsAmbiguous() {
    var result = SeasonDirectoryNameParser.parseForRename("S03 第四季");

    assertFalse(result.matched());
    assertEquals(SeasonDirectoryNameParser.Status.AMBIGUOUS, result.status());
    assertEquals(2, result.detectedSeasons().size());
  }

  @Test
  void doesNotTreatOrdinaryChineseTitleAsSeasonDirectory() {
    var result = SeasonDirectoryNameParser.parseForRename("四季酒店 1080p Remux");

    assertEquals(SeasonDirectoryNameParser.Status.NO_MATCH, result.status());
  }

  @Test
  void doesNotTreatEpisodeMarkerAsShortSeasonDirectoryMarker() {
    var result = SeasonDirectoryNameParser.parseForRename("The Boys S04E01 1080p");

    assertEquals(SeasonDirectoryNameParser.Status.NO_MATCH, result.status());
  }

  @Test
  void matchesEnglishShortAndSpecialDirectoryNames() {
    assertEquals(
        4, SeasonDirectoryNameParser.parseForRename("The Boys Season 4 Remux").seasonNumber());
    assertEquals(4, SeasonDirectoryNameParser.parseForRename("The.Boys.S04.2160p").seasonNumber());
    assertEquals(0, SeasonDirectoryNameParser.parseForRename("The Boys Specials").seasonNumber());
  }
}
