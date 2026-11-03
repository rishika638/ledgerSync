package com.ledgersync.queue.worker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides transient distributed coordination via Redis when available.
 * If Redis is offline, disconnected, or degraded, workers automatically fall back
 * to PostgreSQL native row-level locking (SELECT ... FOR UPDATE SKIP LOCKED).
 *
 * PostgreSQL remains the sole source of truth; Redis failure never causes event loss
 * or duplicate financial transactions.
 */
@Component
public class OptionalRedisCoordinator {

    private static final Logger log = LoggerFactory.getLogger(OptionalRedisCoordinator.class);

    private final StringRedisTemplate redisTemplate;
    private final AtomicBoolean lastRedisStatus = new AtomicBoolean(true);

    public OptionalRedisCoordinator(@Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Tries to acquire a transient coordination lease in Redis.
     * If Redis is unavailable or fails, returns true so PostgreSQL SKIP LOCKED
     * takes over concurrency control safely.
     */
    public boolean tryAcquireCoordination(String key, String workerId, Duration ttl) {
        if (redisTemplate == null) {
            return true;
        }

        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, workerId, ttl);
            if (!lastRedisStatus.get()) {
                log.info("Redis connection restored. Resuming Redis-accelerated worker coordination.");
                lastRedisStatus.set(true);
            }
            return acquired != null && acquired;
        } catch (Exception e) {
            if (lastRedisStatus.compareAndSet(true, false)) {
                log.warn("Redis coordination unavailable ({}). Safely falling back to PostgreSQL row-level locking (SKIP LOCKED).",
                        e.getMessage());
            }
            // Fall back safely to PostgreSQL SKIP LOCKED
            return true;
        }
    }

    public boolean isRedisHealthy() {
        return lastRedisStatus.get();
    }
}
