package com.vitc.security.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

/**
 * PART 2A/7 - minimal in-memory fixed-window limiter shared by every rule
 * {@link RateLimitingFilter} enforces. Deliberately dependency-free (no
 * Redis / Bucket4j): this project runs as a single instance today. If it is
 * ever scaled horizontally, replace the backing map here with a shared store
 * (e.g. Redis) - every caller of {@link #tryAcquire} stays unchanged.
 */
@Service
public class RateLimiterService {

    private static final class Window {
        volatile long windowStartMillis;
        final AtomicInteger count = new AtomicInteger(0);

        Window(long start) {
            this.windowStartMillis = start;
        }
    }

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    public RateLimiterService() {
        // Periodic sweep so keys for one-off visitors don't sit in memory forever.
        ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limit-cleanup");
            t.setDaemon(true);
            return t;
        });
        cleaner.scheduleAtFixedRate(this::evictStale, 5, 5, TimeUnit.MINUTES);
    }

    /**
     * Records one attempt for {@code key} and reports whether it is still
     * within the allowed budget for the given fixed window.
     *
     * @return true if the call should proceed, false if it should be
     *         rejected with 429 Too Many Requests.
     */
    public boolean tryAcquire(String key, int maxAttempts, int windowSeconds) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        Window window = windows.computeIfAbsent(key, k -> new Window(now));
        synchronized (window) {
            if (now - window.windowStartMillis >= windowMillis) {
                window.windowStartMillis = now;
                window.count.set(0);
            }
            int attempts = window.count.incrementAndGet();
            return attempts <= maxAttempts;
        }
    }

    private void evictStale() {
        long now = System.currentTimeMillis();
        windows.entrySet().removeIf(e -> now - e.getValue().windowStartMillis > TimeUnit.HOURS.toMillis(1));
    }
}
