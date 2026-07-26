package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 对 OpenList 文件系统 API 进行按配置隔离的平滑限速。 */
@Slf4j
@Service
public class OpenlistApiRateLimiter {

  private static final long NANOS_PER_MINUTE = TimeUnit.MINUTES.toNanos(1);
  private static final long LOG_WAIT_THRESHOLD_NANOS = TimeUnit.SECONDS.toNanos(1);

  private final ConcurrentMap<String, LimiterState> limiters = new ConcurrentHashMap<>();
  private final LongSupplier nanoTime;
  private final NanoSleeper sleeper;

  public OpenlistApiRateLimiter() {
    this(System::nanoTime, TimeUnit.NANOSECONDS::sleep);
  }

  OpenlistApiRateLimiter(LongSupplier nanoTime, NanoSleeper sleeper) {
    this.nanoTime = nanoTime;
    this.sleeper = sleeper;
  }

  /**
   * 获取一次文件系统 API 调用许可。配置值为 0 或空时立即返回。
   *
   * @param config OpenList 配置
   * @param apiPath 即将调用的 API 路径，仅用于日志
   */
  public void acquire(OpenlistConfig config, String apiPath) {
    int qpmLimit =
        config == null || config.getFsApiQpmLimit() == null ? 0 : config.getFsApiQpmLimit();
    if (qpmLimit <= 0) {
      if (config != null) {
        limiters.remove(buildKey(config));
      }
      return;
    }

    String key = buildKey(config);
    LimiterState state = limiters.computeIfAbsent(key, ignored -> new LimiterState());
    long waitNanos = state.reserve(qpmLimit, nanoTime.getAsLong());

    if (waitNanos >= LOG_WAIT_THRESHOLD_NANOS) {
      log.debug(
          "OpenList API 限速等待: configId={}, api={}, qpm={}, waitMs={}",
          config.getId(),
          apiPath,
          qpmLimit,
          TimeUnit.NANOSECONDS.toMillis(waitNanos));
    }

    if (waitNanos <= 0) {
      return;
    }

    try {
      sleeper.sleep(waitNanos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException("等待 OpenList API 限速时任务被中断", e);
    }
  }

  private String buildKey(OpenlistConfig config) {
    if (config.getId() != null) {
      return "id:" + config.getId();
    }
    return "url:" + String.valueOf(config.getBaseUrl()) + ":user:" + config.getUsername();
  }

  @FunctionalInterface
  interface NanoSleeper {
    void sleep(long nanos) throws InterruptedException;
  }

  private static class LimiterState {
    private int qpmLimit;
    private long nextAllowedNanos;

    private synchronized long reserve(int requestedQpmLimit, long nowNanos) {
      if (qpmLimit != requestedQpmLimit) {
        qpmLimit = requestedQpmLimit;
        nextAllowedNanos = nowNanos;
      }

      long intervalNanos = divideRoundingUp(NANOS_PER_MINUTE, requestedQpmLimit);
      long scheduledNanos = Math.max(nowNanos, nextAllowedNanos);
      nextAllowedNanos = addSaturated(scheduledNanos, intervalNanos);
      return Math.max(0, scheduledNanos - nowNanos);
    }

    private static long divideRoundingUp(long dividend, long divisor) {
      return dividend / divisor + (dividend % divisor == 0 ? 0 : 1);
    }

    private static long addSaturated(long value, long increment) {
      if (value > Long.MAX_VALUE - increment) {
        return Long.MAX_VALUE;
      }
      return value + increment;
    }
  }
}
