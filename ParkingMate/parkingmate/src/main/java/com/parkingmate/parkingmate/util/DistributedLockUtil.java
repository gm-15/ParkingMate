package com.parkingmate.parkingmate.util;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis 분산 락 유틸리티
 *
 * Phase 3: CircuitBreaker "redis-lock" 적용
 *   - Redis 장애 시 Circuit Open → BookingService fallback 호출
 *   - fallback: DB Pessimistic Lock만으로 처리 (안전)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockUtil {

    private final StringRedisTemplate redisTemplate;
    private static final String LOCK_PREFIX = "lock:booking:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    @CircuitBreaker(name = "redis-lock")
    public <T> T executeWithLock(String key, Duration timeout, Supplier<T> work) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = Thread.currentThread().getId() + "-" + System.currentTimeMillis();

        try {
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, timeout);

            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired: {}", lockKey);
                try {
                    return work.get();
                } finally {
                    if (lockValue.equals(redisTemplate.opsForValue().get(lockKey))) {
                        redisTemplate.delete(lockKey);
                        log.debug("Lock released: {}", lockKey);
                    }
                }
            } else {
                log.warn("Failed to acquire lock: {}", lockKey);
                throw new IllegalStateException("다른 사용자가 이미 예약을 진행 중입니다. 잠시 후 다시 시도해주세요.");
            }
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis lock error: {}", lockKey, e);
            if (lockValue.equals(redisTemplate.opsForValue().get(lockKey))) {
                redisTemplate.delete(lockKey);
            }
            throw e;
        }
    }

    public <T> T executeWithLock(String key, Supplier<T> work) {
        return executeWithLock(key, DEFAULT_TIMEOUT, work);
    }
}
