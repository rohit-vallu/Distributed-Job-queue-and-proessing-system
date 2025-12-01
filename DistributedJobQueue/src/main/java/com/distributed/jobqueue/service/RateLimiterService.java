package com.distributed.jobqueue.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory per-tenant rate limiter.
 *
 * Prototype implementation:
 * - Allows up to MAX_JOBS_PER_MINUTE per tenant.
 * - Uses sliding window based on timestamps.
 *
 * NOTE:
 * In a real distributed deployment, this would be backed by Redis
 * or another shared store to work across multiple nodes.
 */
@Service
public class RateLimiterService {

    private static final int MAX_JOBS_PER_MINUTE = 10;
    private static final long WINDOW_MILLIS = 60_000L;

    private final Map<String, Deque<Long>> tenantSubmissions = new ConcurrentHashMap<>();

    public boolean allowSubmission(String tenantId) {
        long now = Instant.now().toEpochMilli();
        Deque<Long> timestamps = tenantSubmissions.computeIfAbsent(tenantId, t -> new ArrayDeque<>());

        synchronized (timestamps) {
            // remove timestamps older than 1 minute
            while (!timestamps.isEmpty() && now - timestamps.peekFirst() > WINDOW_MILLIS) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= MAX_JOBS_PER_MINUTE) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }
}

