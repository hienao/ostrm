package com.hienao.openlist2strm.entity;

/** 手动刮削可恢复阶段，枚举顺序代表执行顺序。 */
public enum ManualScrapingJobStage {
  PREPARING,
  RENAMING,
  GENERATING,
  UPLOADING,
  COMPLETED;

  public boolean isAtLeast(ManualScrapingJobStage other) {
    return ordinal() >= other.ordinal();
  }
}
