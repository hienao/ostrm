package com.hienao.openlist2strm.entity;

import com.hienao.openlist2strm.exception.BusinessException;
import java.util.Locale;

/** 支持的媒体服务器类型。 */
public enum MediaServerType {
  EMBY,
  JELLYFIN;

  public static MediaServerType from(String value) {
    if (value == null || value.isBlank()) {
      throw new BusinessException("媒体服务器类型不能为空");
    }
    try {
      return valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException e) {
      throw new BusinessException("媒体服务器类型必须是 EMBY 或 JELLYFIN");
    }
  }
}
