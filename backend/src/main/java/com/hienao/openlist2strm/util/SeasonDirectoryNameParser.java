package com.hienao.openlist2strm.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 为手动刮削的季目录重命名提取目录名中包含的季号。 */
public final class SeasonDirectoryNameParser {

  private static final Pattern CHINESE_SEASON_PATTERN =
      Pattern.compile("第\\s*([0-9零〇一二两三四五六七八九十百]+)\\s*季");
  private static final Pattern ENGLISH_SEASON_PATTERN =
      Pattern.compile("(?i)(?<![a-z0-9])season[\\s._-]*(\\d{1,3})(?!\\d)(?![a-z0-9])");
  private static final Pattern SHORT_SEASON_PATTERN =
      Pattern.compile("(?i)(?<![a-z0-9])s[\\s._-]*(\\d{1,3})(?!\\d)(?![\\s._-]*e\\d)(?![a-z0-9])");
  private static final Pattern SPECIALS_PATTERN =
      Pattern.compile(
          "(?i)(?<![a-z0-9])(?:specials?|special[\\s._-]+episodes?|特别篇|特别季|特典)(?![a-z0-9])");

  private SeasonDirectoryNameParser() {}

  public static Result parseForRename(String directoryName) {
    if (directoryName == null || directoryName.isBlank()) {
      return Result.noMatch();
    }

    List<DetectedSeason> detected = new ArrayList<>();
    List<String> invalidMarkers = new ArrayList<>();
    collectNumericMatches(
        directoryName,
        CHINESE_SEASON_PATTERN,
        MatchType.CHINESE_SEASON,
        SeasonDirectoryNameParser::parseChineseNumber,
        detected,
        invalidMarkers);
    collectNumericMatches(
        directoryName,
        ENGLISH_SEASON_PATTERN,
        MatchType.ENGLISH_SEASON,
        SeasonDirectoryNameParser::parseArabicNumber,
        detected,
        invalidMarkers);
    collectNumericMatches(
        directoryName,
        SHORT_SEASON_PATTERN,
        MatchType.SHORT_SEASON,
        SeasonDirectoryNameParser::parseArabicNumber,
        detected,
        invalidMarkers);

    Matcher specialsMatcher = SPECIALS_PATTERN.matcher(directoryName);
    while (specialsMatcher.find()) {
      detected.add(
          new DetectedSeason(
              0, MatchType.SPECIALS, specialsMatcher.group(), specialsMatcher.start()));
    }

    if (!invalidMarkers.isEmpty()) {
      return Result.invalid(
          markers(detected, invalidMarkers), detectedSeasons(detected), invalidMarkers);
    }
    if (detected.isEmpty()) {
      return Result.noMatch();
    }

    Set<Integer> seasonNumbers = detectedSeasons(detected);
    if (seasonNumbers.size() > 1) {
      return Result.ambiguous(markers(detected, List.of()), seasonNumbers);
    }

    DetectedSeason selected =
        detected.stream()
            .min(
                Comparator.comparingInt((DetectedSeason item) -> item.matchType().priority())
                    .thenComparingInt(DetectedSeason::position))
            .orElseThrow();
    return Result.matched(
        selected.seasonNumber(), selected.matchType(), markers(detected, List.of()), seasonNumbers);
  }

  private static void collectNumericMatches(
      String directoryName,
      Pattern pattern,
      MatchType matchType,
      NumberParser numberParser,
      List<DetectedSeason> detected,
      List<String> invalidMarkers) {
    Matcher matcher = pattern.matcher(directoryName);
    while (matcher.find()) {
      Integer seasonNumber = numberParser.parse(matcher.group(1));
      if (seasonNumber == null || seasonNumber < 0 || seasonNumber > 99) {
        invalidMarkers.add(matcher.group());
        continue;
      }
      detected.add(new DetectedSeason(seasonNumber, matchType, matcher.group(), matcher.start()));
    }
  }

  private static Integer parseArabicNumber(String value) {
    try {
      return Integer.valueOf(value);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private static Integer parseChineseNumber(String value) {
    if (value.chars().allMatch(Character::isDigit)) {
      return parseArabicNumber(value);
    }

    String normalized = value.replace('〇', '零').replace('两', '二');
    int tenIndex = normalized.indexOf('十');
    if (tenIndex >= 0) {
      if (normalized.indexOf('十', tenIndex + 1) >= 0) {
        return null;
      }
      String tensPart = normalized.substring(0, tenIndex);
      String unitsPart = normalized.substring(tenIndex + 1);
      Integer tens = tensPart.isEmpty() ? 1 : chineseDigit(tensPart);
      Integer units = unitsPart.isEmpty() ? 0 : chineseDigit(unitsPart);
      return tens == null || units == null ? null : tens * 10 + units;
    }
    return chineseDigit(normalized);
  }

  private static Integer chineseDigit(String value) {
    if (value.length() != 1) {
      return null;
    }
    return switch (value.charAt(0)) {
      case '零' -> 0;
      case '一' -> 1;
      case '二' -> 2;
      case '三' -> 3;
      case '四' -> 4;
      case '五' -> 5;
      case '六' -> 6;
      case '七' -> 7;
      case '八' -> 8;
      case '九' -> 9;
      default -> null;
    };
  }

  private static List<String> markers(List<DetectedSeason> detected, List<String> invalidMarkers) {
    List<String> result = new ArrayList<>(detected.size() + invalidMarkers.size());
    detected.stream()
        .sorted(Comparator.comparingInt(DetectedSeason::position))
        .map(DetectedSeason::marker)
        .forEach(result::add);
    result.addAll(invalidMarkers);
    return List.copyOf(result);
  }

  private static Set<Integer> detectedSeasons(List<DetectedSeason> detected) {
    Set<Integer> result = new LinkedHashSet<>();
    detected.stream()
        .sorted(Comparator.comparingInt(DetectedSeason::position))
        .map(DetectedSeason::seasonNumber)
        .forEach(result::add);
    return Set.copyOf(result);
  }

  public enum Status {
    MATCHED,
    NO_MATCH,
    AMBIGUOUS,
    INVALID
  }

  public enum MatchType {
    CHINESE_SEASON(0),
    ENGLISH_SEASON(1),
    SHORT_SEASON(2),
    SPECIALS(3);

    private final int priority;

    MatchType(int priority) {
      this.priority = priority;
    }

    int priority() {
      return priority;
    }
  }

  public record Result(
      Status status,
      Integer seasonNumber,
      MatchType matchType,
      List<String> detectedMarkers,
      Set<Integer> detectedSeasons,
      List<String> invalidMarkers) {

    private static Result matched(
        Integer seasonNumber,
        MatchType matchType,
        List<String> detectedMarkers,
        Set<Integer> detectedSeasons) {
      return new Result(
          Status.MATCHED, seasonNumber, matchType, detectedMarkers, detectedSeasons, List.of());
    }

    private static Result noMatch() {
      return new Result(Status.NO_MATCH, null, null, List.of(), Set.of(), List.of());
    }

    private static Result ambiguous(List<String> detectedMarkers, Set<Integer> detectedSeasons) {
      return new Result(Status.AMBIGUOUS, null, null, detectedMarkers, detectedSeasons, List.of());
    }

    private static Result invalid(
        List<String> detectedMarkers, Set<Integer> detectedSeasons, List<String> invalidMarkers) {
      return new Result(
          Status.INVALID,
          null,
          null,
          detectedMarkers,
          detectedSeasons,
          List.copyOf(invalidMarkers));
    }

    public boolean matched() {
      return status == Status.MATCHED;
    }

    public boolean shouldReportFailure() {
      return status == Status.AMBIGUOUS || status == Status.INVALID;
    }

    public String failureReason() {
      return status.name().toLowerCase(Locale.ROOT);
    }
  }

  private record DetectedSeason(
      int seasonNumber, MatchType matchType, String marker, int position) {}

  @FunctionalInterface
  private interface NumberParser {
    Integer parse(String value);
  }
}
