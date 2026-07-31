package com.hienao.openlist2strm.entity;

/** 手动刮削异步作业状态。 */
public enum ManualScrapingJobStatus {
  PENDING,
  RUNNING,
  SUCCEEDED,
  FAILED;

  public boolean isActive() {
    return this == PENDING || this == RUNNING;
  }
}
