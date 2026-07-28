package com.hienao.openlist2strm.entity;

import java.util.Locale;

/** 任务媒体库类型。 */
public enum MediaLibraryType {
  AUTO,
  MOVIE,
  TV,
  ANIME;

  public static MediaLibraryType from(String value) {
    if (value == null || value.isBlank()) {
      return AUTO;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("不支持的媒体库类型: " + value, e);
    }
  }

  public String value() {
    return name().toLowerCase(Locale.ROOT);
  }

  public boolean isTvLike() {
    return this == TV || this == ANIME;
  }
}
