package dev.driftguard.web;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/** Sliding-window limits per visitor and overall, so one person can't use up the free LLM quota for everyone. */
final class RateLimiter {

    private final int perKey;
    private final int total;
    private final long windowMillis;
    private final LongSupplier clock;
    private final Map<String, Deque<Long>> byKey = new HashMap<>();
    private final Deque<Long> all = new ArrayDeque<>();

    RateLimiter(int perKey, int total, long windowMillis, LongSupplier clock) {
        this.perKey = perKey;
        this.total = total;
        this.windowMillis = windowMillis;
        this.clock = clock;
    }

    /** Records the use and returns true, or returns false (recording nothing) when over a limit. */
    synchronized boolean tryAcquire(String key) {
        long now = clock.getAsLong();
        prune(all, now);
        Deque<Long> mine = byKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        prune(mine, now);
        if (mine.size() >= perKey || all.size() >= total) return false;
        mine.addLast(now);
        all.addLast(now);
        if (byKey.size() > 10_000) byKey.values().removeIf(Deque::isEmpty);
        return true;
    }

    /** Minutes until this visitor can try again, at least 1. */
    synchronized long minutesUntilFree(String key) {
        Deque<Long> mine = byKey.getOrDefault(key, new ArrayDeque<>());
        Long oldest = mine.size() >= perKey ? mine.peekFirst() : all.peekFirst();
        if (oldest == null) return 1;
        return Math.max(1, (oldest + windowMillis - clock.getAsLong() + 59_999) / 60_000);
    }

    private void prune(Deque<Long> times, long now) {
        while (!times.isEmpty() && times.peekFirst() <= now - windowMillis) times.removeFirst();
    }
}
