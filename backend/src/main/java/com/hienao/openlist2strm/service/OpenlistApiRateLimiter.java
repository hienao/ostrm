package com.hienao.openlist2strm.service;

import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 对 OpenList 文件系统 API 进行按配置隔离的 QPS、QPM 双层令牌桶限速。 */
@Slf4j
@Service
public class OpenlistApiRateLimiter {

  private static final long NANOS_PER_MINUTE = TimeUnit.MINUTES.toNanos(1);
  private static final long NANOS_PER_SECOND = TimeUnit.SECONDS.toNanos(1);
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
   * 获取一次文件系统 API 调用许可。QPS 与 QPM 分别为 0 或空时，表示对应维度不限制。
   *
   * @param config OpenList 配置
   * @param apiPath 即将调用的 API 路径，仅用于日志
   */
  public void acquire(OpenlistConfig config, String apiPath) {
    int qpmLimit =
        config == null || config.getFsApiQpmLimit() == null ? 0 : config.getFsApiQpmLimit();
    int qpsLimit =
        config == null || config.getFsApiQpsLimit() == null ? 0 : config.getFsApiQpsLimit();
    if (qpmLimit <= 0 && qpsLimit <= 0) {
      if (config != null) {
        limiters.remove(buildKey(config));
      }
      return;
    }

    String key = buildKey(config);
    LimiterState state = limiters.computeIfAbsent(key, ignored -> new LimiterState());
    boolean waitLogged = false;

    while (true) {
      long waitNanos = state.tryAcquire(qpsLimit, qpmLimit, nanoTime.getAsLong());
      if (waitNanos <= 0) {
        return;
      }

      if (!waitLogged && waitNanos >= LOG_WAIT_THRESHOLD_NANOS) {
        log.debug(
            "OpenList API 限速等待: configId={}, api={}, qps={}, qpm={}, waitMs={}",
            config.getId(),
            apiPath,
            qpsLimit,
            qpmLimit,
            TimeUnit.NANOSECONDS.toMillis(waitNanos));
        waitLogged = true;
      }

      try {
        sleeper.sleep(waitNanos);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new BusinessException("等待 OpenList API 限速时任务被中断", e);
      }
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
    private int qpsLimit;
    private int qpmLimit;
    private final TokenBucket qpsBucket = new TokenBucket();
    private final TokenBucket qpmBucket = new TokenBucket();

    private synchronized long tryAcquire(
        int requestedQpsLimit, int requestedQpmLimit, long nowNanos) {
      if (qpsLimit != requestedQpsLimit || qpmLimit != requestedQpmLimit) {
        qpsLimit = requestedQpsLimit;
        qpmLimit = requestedQpmLimit;
        qpsBucket.reset(requestedQpsLimit, NANOS_PER_SECOND, nowNanos);
        qpmBucket.reset(requestedQpmLimit, NANOS_PER_MINUTE, nowNanos);
      }

      long waitNanos =
          Math.max(
              qpsBucket.nanosUntilAvailable(nowNanos), qpmBucket.nanosUntilAvailable(nowNanos));
      if (waitNanos > 0) {
        return waitNanos;
      }
      qpsBucket.consume();
      qpmBucket.consume();
      return 0;
    }
  }

  private static class TokenBucket {
    private int capacity;
    private long refillPeriodNanos;
    private double tokens;
    private long lastRefillNanos;

    private void reset(int requestedCapacity, long requestedRefillPeriodNanos, long nowNanos) {
      capacity = Math.max(0, requestedCapacity);
      refillPeriodNanos = requestedRefillPeriodNanos;
      tokens = capacity;
      lastRefillNanos = nowNanos;
    }

    private long nanosUntilAvailable(long nowNanos) {
      if (capacity <= 0) {
        return 0;
      }
      refill(nowNanos);
      if (tokens >= 1.0d) {
        return 0;
      }
      double missingTokens = 1.0d - tokens;
      double waitNanos = missingTokens * refillPeriodNanos / capacity;
      return Math.max(1L, (long) Math.ceil(waitNanos));
    }

    private void consume() {
      if (capacity > 0) {
        tokens = Math.max(0.0d, tokens - 1.0d);
      }
    }

    private void refill(long nowNanos) {
      if (nowNanos <= lastRefillNanos) {
        return;
      }
      long elapsedNanos = nowNanos - lastRefillNanos;
      double refillTokens = (double) elapsedNanos * capacity / refillPeriodNanos;
      tokens = Math.min(capacity, tokens + refillTokens);
      lastRefillNanos = nowNanos;
    }
  }
}
