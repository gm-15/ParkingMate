package com.parkingmate.parkingmate.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Redis를 활용한 분산 락 유틸리티
 * 주차 공간 예약 시 동시성 문제 해결을 위한 분산 락 구현
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLockUtil {

    private final StringRedisTemplate redisTemplate;
    private static final String LOCK_PREFIX = "lock:booking:";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    /**
     * 분산 락을 획득하고 작업을 수행한 후 자동으로 락을 해제합니다.
     *
     * @param key 락의 키 (예: "parking-space-{id}")
     * @param timeout 락 대기 시간
     * @param work 수행할 작업
     * @return 작업 결과
     */
    public <T> T executeWithLock(String key, Duration timeout, Supplier<T> work) {
        String lockKey = LOCK_PREFIX + key;
        String lockValue = Thread.currentThread().getId() + "-" + System.currentTimeMillis();
        
        try {
            // 락 획득 시도 (SET NX EX 명령어: key가 없을 때만 설정하고 만료 시간 설정)
            Boolean acquired = redisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, timeout);
            
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired: {}", lockKey);
                try {
                    // 락 획득 성공 - 작업 수행
                    return work.get();
                } finally {
                    // 락 해제 (본인의 락인지 확인 후 해제)
                    if (lockValue.equals(redisTemplate.opsForValue().get(lockKey))) {
                        redisTemplate.delete(lockKey);
                        log.debug("Lock released: {}", lockKey);
                    }
                }
            } else {
                log.warn("Failed to acquire lock: {}", lockKey);
                throw new IllegalStateException("다른 사용자가 이미 예약을 진행 중입니다. 잠시 후 다시 시도해주세요.");
            }
        } catch (Exception e) {
            log.error("Error while executing with lock: {}", lockKey, e);
            // 예외 발생 시 락 해제 시도
            if (lockValue.equals(redisTemplate.opsForValue().get(lockKey))) {
                redisTemplate.delete(lockKey);
            }
            throw e;
        }
    }

    /**
     * 기본 타임아웃(10초)으로 분산 락을 획득하고 작업을 수행합니다.
     */
    public <T> T executeWithLock(String key, Supplier<T> work) {
        return executeWithLock(key, DEFAULT_TIMEOUT, work);
    }
}

