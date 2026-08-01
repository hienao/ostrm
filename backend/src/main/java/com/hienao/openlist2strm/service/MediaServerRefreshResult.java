package com.hienao.openlist2strm.service;

/** 任务结束后媒体库刷新请求的结果；成功只表示服务器已接受请求。 */
public record MediaServerRefreshResult(
    Status status,
    String serverName,
    String serverType,
    String scope,
    String libraryName,
    String failureCode,
    String message) {

  public enum Status {
    TRIGGERED,
    SKIPPED,
    FAILED
  }

  public static MediaServerRefreshResult skipped(String message) {
    return new MediaServerRefreshResult(Status.SKIPPED, null, null, "NONE", null, null, message);
  }
}
