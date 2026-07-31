package com.hienao.openlist2strm.mapper;

import com.hienao.openlist2strm.entity.ManualScrapingJob;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 手动刮削作业持久化。 */
@Mapper
public interface ManualScrapingJobMapper {

  int insert(ManualScrapingJob job);

  ManualScrapingJob selectById(@Param("id") Long id);

  ManualScrapingJob selectActiveByTaskId(@Param("taskId") Long taskId);

  ManualScrapingJob selectLatestByTaskId(@Param("taskId") Long taskId);

  int markRunningIfPending(@Param("id") Long id);

  int updateCheckpoint(ManualScrapingJob job);

  int prepareRetry(@Param("id") Long id);

  int markInterruptedJobsFailed();
}
