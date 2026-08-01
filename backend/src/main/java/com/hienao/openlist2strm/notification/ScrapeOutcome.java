package com.hienao.openlist2strm.notification;

/** 单个媒体文件的 TMDB 刮削结果，用于准确区分未识别与技术失败。 */
public record ScrapeOutcome(Status status, String reason, String mediaTitle, Integer tmdbId) {

  public enum Status {
    MATCHED,
    LOW_CONFIDENCE,
    TITLE_UNAVAILABLE,
    TMDB_NOT_MATCHED,
    UNSUPPORTED_MEDIA_TYPE,
    FAILED
  }

  public static ScrapeOutcome matched(String title, Integer tmdbId) {
    return new ScrapeOutcome(Status.MATCHED, null, title, tmdbId);
  }

  public static ScrapeOutcome unmatched(Status status, String reason) {
    return new ScrapeOutcome(status, reason, null, null);
  }

  public boolean isUnrecognized() {
    return status == Status.LOW_CONFIDENCE
        || status == Status.TITLE_UNAVAILABLE
        || status == Status.TMDB_NOT_MATCHED
        || status == Status.UNSUPPORTED_MEDIA_TYPE;
  }
}
