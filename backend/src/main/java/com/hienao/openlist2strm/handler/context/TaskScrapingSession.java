package com.hienao.openlist2strm.handler.context;

import com.hienao.openlist2strm.dto.tmdb.TmdbMovieDetail;
import com.hienao.openlist2strm.dto.tmdb.TmdbTvDetail;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 单次任务共享的刮削缓存，任务结束后随上下文释放。 */
public class TaskScrapingSession {

  private final Map<String, TmdbMovieDetail> movieMatches = new ConcurrentHashMap<>();
  private final Map<String, TmdbTvDetail> tvMatches = new ConcurrentHashMap<>();
  private final Map<Integer, TmdbMovieDetail> movieDetails = new ConcurrentHashMap<>();
  private final Map<Integer, TmdbTvDetail> tvDetails = new ConcurrentHashMap<>();
  private final Map<String, Path> downloadedImages = new ConcurrentHashMap<>();
  private final Set<String> negativeMatches = ConcurrentHashMap.newKeySet();
  private final Set<String> processedSubtitleDirectories = ConcurrentHashMap.newKeySet();

  public Map<String, TmdbMovieDetail> movieMatches() {
    return movieMatches;
  }

  public Map<String, TmdbTvDetail> tvMatches() {
    return tvMatches;
  }

  public Map<Integer, TmdbMovieDetail> movieDetails() {
    return movieDetails;
  }

  public Map<Integer, TmdbTvDetail> tvDetails() {
    return tvDetails;
  }

  public Map<String, Path> downloadedImages() {
    return downloadedImages;
  }

  public Set<String> negativeMatches() {
    return negativeMatches;
  }

  public Set<String> processedSubtitleDirectories() {
    return processedSubtitleDirectories;
  }
}
