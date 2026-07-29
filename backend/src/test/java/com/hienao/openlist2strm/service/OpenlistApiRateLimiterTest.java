package com.hienao.openlist2strm.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hienao.openlist2strm.entity.OpenlistConfig;
import com.hienao.openlist2strm.exception.BusinessException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenlistApiRateLimiterTest {

  @AfterEach
  void clearInterruptedFlag() {
    Thread.interrupted();
  }

  @Test
  void zeroLimitDoesNotWait() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 0, 0);

    limiter.acquire(config, "/api/fs/list");
    limiter.acquire(config, "/api/fs/get");

    assertTrue(fakeTime.waits.isEmpty());
  }

  @Test
  void qpsBucketAllowsBurstThenRefillsContinuously() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 0, 2);

    limiter.acquire(config, "/api/fs/list");
    limiter.acquire(config, "/api/fs/get");
    limiter.acquire(config, "/api/fs/list");

    assertEquals(List.of(TimeUnit.MILLISECONDS.toNanos(500)), fakeTime.waits);
  }

  @Test
  void qpmBucketLimitsSustainedTrafficAfterInitialCapacity() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 2, 0);

    limiter.acquire(config, "/api/fs/list");
    limiter.acquire(config, "/api/fs/get");
    limiter.acquire(config, "/api/fs/list");

    assertEquals(List.of(TimeUnit.SECONDS.toNanos(30)), fakeTime.waits);
  }

  @Test
  void requestMustPassBothBuckets() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 2, 1);

    limiter.acquire(config, "/api/fs/list");
    limiter.acquire(config, "/api/fs/get");
    limiter.acquire(config, "/api/fs/list");

    assertEquals(
        List.of(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(29)), fakeTime.waits);
  }

  @Test
  void differentConfigsHaveIndependentLimits() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);

    limiter.acquire(config(1L, 1, 1), "/api/fs/list");
    limiter.acquire(config(2L, 1, 1), "/api/fs/list");

    assertTrue(fakeTime.waits.isEmpty());
  }

  @Test
  void changingLimitStartsANewSchedule() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 1, 1);

    limiter.acquire(config, "/api/fs/list");
    config.setFsApiQpsLimit(2);
    limiter.acquire(config, "/api/fs/list");

    assertTrue(fakeTime.waits.isEmpty());
  }

  @Test
  void interruptionIsPropagatedAsBusinessException() {
    OpenlistApiRateLimiter limiter =
        new OpenlistApiRateLimiter(
            () -> 0,
            ignored -> {
              throw new InterruptedException("interrupted");
            });
    OpenlistConfig config = config(1L, 0, 1);
    limiter.acquire(config, "/api/fs/list");

    assertThrows(BusinessException.class, () -> limiter.acquire(config, "/api/fs/list"));
    assertTrue(Thread.currentThread().isInterrupted());
  }

  private OpenlistConfig config(long id, int qpmLimit, int qpsLimit) {
    return new OpenlistConfig().setId(id).setFsApiQpmLimit(qpmLimit).setFsApiQpsLimit(qpsLimit);
  }

  private static class FakeTime {
    private final AtomicLong now = new AtomicLong();
    private final List<Long> waits = new ArrayList<>();

    private long now() {
      return now.get();
    }

    private void sleep(long nanos) {
      waits.add(nanos);
      now.addAndGet(nanos);
    }
  }
}
