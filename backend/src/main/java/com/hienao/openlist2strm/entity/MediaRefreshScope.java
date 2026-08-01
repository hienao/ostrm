package com.hienao.openlist2strm.entity;

import com.hienao.openlist2strm.exception.BusinessException;
import java.util.Locale;

/** 任务完成后的媒体库刷新范围。 */
public enum MediaRefreshScope {
  NONE,
  ALL,
  LIBRARY;

  public static MediaRefreshScope from(String value) {
    if (value == null || value.isBlank()) {
      return NONE;
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessException("媒体库刷新范围必须是 NONE、ALL 或 LIBRARY");
    }
  }
}
