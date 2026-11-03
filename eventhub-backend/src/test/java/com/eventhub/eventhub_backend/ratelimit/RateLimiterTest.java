package com.eventhub.eventhub_backend.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    private static final Duration MINUTE = Duration.ofMinutes(1);

    /** A clock the test can move forward by hand. */
    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2030-01-01T00:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Test
    void allowsUpToTheLimit_thenBlocks() {
        RateLimiter limiter = new RateLimiter(new MutableClock());

        assertThat(limiter.tryAcquire("ip-1", 3, MINUTE)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 3, MINUTE)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 3, MINUTE)).isTrue();
        assertThat(limiter.tryAcquire("ip-1", 3, MINUTE)).isFalse();
    }

    @Test
    void theWindowSlides_soCallsAreAllowedAgainLater() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        for (int i = 0; i < 3; i++) {
            limiter.tryAcquire("ip-1", 3, MINUTE);
        }
        assertThat(limiter.tryAcquire("ip-1", 3, MINUTE)).isFalse();

        clock.advance(Duration.ofSeconds(61));

        assertThat(limiter.tryAcquire("ip-1", 3, MINUTE)).isTrue();
    }

    @Test
    void differentKeysDoNotAffectEachOther() {
        RateLimiter limiter = new RateLimiter(new MutableClock());
        limiter.tryAcquire("ip-1", 1, MINUTE);

        assertThat(limiter.tryAcquire("ip-1", 1, MINUTE)).isFalse();
        assertThat(limiter.tryAcquire("ip-2", 1, MINUTE)).isTrue();
    }

    @Test
    void tellsHowLongToWait() {
        MutableClock clock = new MutableClock();
        RateLimiter limiter = new RateLimiter(clock);
        limiter.tryAcquire("ip-1", 1, MINUTE);
        clock.advance(Duration.ofSeconds(20));

        long wait = limiter.secondsUntilFree("ip-1", MINUTE);

        assertThat(wait).isBetween(39L, 41L);
        assertThat(limiter.secondsUntilFree("nobody", MINUTE)).isZero();
    }

    @Test
    void ahundredCallersAtOnce_onlyTenGetThrough() throws Exception {
        RateLimiter limiter = new RateLimiter(new MutableClock());
        int threads = 100;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                if (limiter.tryAcquire("shared", 10, MINUTE)) {
                    allowed.incrementAndGet();
                }
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> future : futures) {
            future.get(30, TimeUnit.SECONDS);
        }
        pool.shutdown();

        assertThat(allowed.get()).isEqualTo(10);
    }
}