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
    OpenlistConfig config = config(1L, 0);

    limiter.acquire(config, "/api/fs/list");
    limiter.acquire(config, "/api/fs/get");

    assertTrue(fakeTime.waits.isEmpty());
  }

  @Test
  void requestsForSameConfigAreEvenlySpaced() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 60);

    limiter.acquire(config, "/api/fs/list");
    limiter.acquire(config, "/api/fs/get");
    limiter.acquire(config, "/api/fs/list");

    assertEquals(List.of(TimeUnit.SECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(1)), fakeTime.waits);
  }

  @Test
  void differentConfigsHaveIndependentLimits() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);

    limiter.acquire(config(1L, 60), "/api/fs/list");
    limiter.acquire(config(2L, 60), "/api/fs/list");

    assertTrue(fakeTime.waits.isEmpty());
  }

  @Test
  void changingLimitStartsANewSchedule() {
    FakeTime fakeTime = new FakeTime();
    OpenlistApiRateLimiter limiter = new OpenlistApiRateLimiter(fakeTime::now, fakeTime::sleep);
    OpenlistConfig config = config(1L, 60);

    limiter.acquire(config, "/api/fs/list");
    config.setFsApiQpmLimit(30);
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
    OpenlistConfig config = config(1L, 60);
    limiter.acquire(config, "/api/fs/list");

    assertThrows(BusinessException.class, () -> limiter.acquire(config, "/api/fs/list"));
    assertTrue(Thread.currentThread().isInterrupted());
  }

  private OpenlistConfig config(long id, int qpmLimit) {
    return new OpenlistConfig().setId(id).setFsApiQpmLimit(qpmLimit);
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
