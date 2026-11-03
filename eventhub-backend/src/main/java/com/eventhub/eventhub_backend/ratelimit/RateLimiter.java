package com.eventhub.eventhub_backend.ratelimit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Component
public class RateLimiter {

    private final Clock clock;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public RateLimiter() {
        this(Clock.systemUTC());
    }

    // Tests pass a clock they can move forward
    RateLimiter(Clock clock) {
        this.clock = clock;
    }

    /** Records a call and returns true if it is allowed, false if the key is over its limit. */
    public boolean tryAcquire(String key, int limit, Duration window) {
        long now = clock.millis();
        long cutoff = now - window.toMillis();

        Deque<Long> times = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() <= cutoff) {
                times.pollFirst();
            }
            if (times.size() >= limit) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }

    /** How long until the oldest call in the window expires, rounded up to whole seconds. */
    public long secondsUntilFree(String key, Duration window) {
        Deque<Long> times = hits.get(key);
        if (times == null) {
            return 0;
        }
        synchronized (times) {
            Long oldest = times.peekFirst();
            if (oldest == null) {
                return 0;
            }
            long millis = oldest + window.toMillis() - clock.millis();
            return Math.max(1L, (millis + 999) / 1000);
        }
    }

    /** Stops the map from growing forever. */
    @Scheduled(fixedDelay = 10, initialDelay = 10, timeUnit = TimeUnit.MINUTES)
    public void forgetOldKeys() {
        long cutoff = clock.millis() - Duration.ofDays(1).toMillis();
        hits.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                Long last = entry.getValue().peekLast();
                return last == null || last < cutoff;
            }
        });
    }
}