package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.*;
import com.parkingmate.parkingmate.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.repository.*;
import com.parkingmate.parkingmate.util.DistributedLockUtil;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * [커넥션 점유 시간 실측] Hikari Connection Hold Time 측정
 *
 * Case 1: 현재 코드 (알림 없음)           → 베이스라인
 * Case 2: 알림을 트랜잭션 안에 포함       → 문제 재현 (추가 SELECT + INSERT)
 * Case 3: Outbox 패턴 시뮬레이션          → 알림 로직 제거, INSERT 1건만
 */
@SpringBootTest
@ActiveProfiles("mysqltest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BookingConnectionTimingTest {

    private static final Logger log = LoggerFactory.getLogger(BookingConnectionTimingTest.class);

    @Autowired BookingService bookingService;
    @Autowired NotificationService notificationService;
    @Autowired UserRepository userRepository;
    @Autowired ParkingSpaceRepository parkingSpaceRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired DataSource dataSource;

    @MockBean
    DistributedLockUtil distributedLockUtil;

    private static Long savedUserId;
    private static Long savedSpaceId;

    @BeforeEach
    void setupMockAndData() {
        // Redis 분산락 Mock: 락 없이 람다 바로 실행
        when(distributedLockUtil.executeWithLock(anyString(), any(Supplier.class)))
                .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());

        if (savedUserId == null) {
            User user = new User("timing@test.com", "hashed_pw", "타이밍테스터");
            savedUserId = userRepository.save(user).getId();

            ParkingSpace space = new ParkingSpace(
                    userRepository.findById(savedUserId).get(),
                    "서울시 강남구 테헤란로 123",
                    5000,
                    "타이밍 테스트용 주차 공간"
            );
            savedSpaceId = parkingSpaceRepository.save(space).getId();
            log.info("테스트 데이터 생성 완료. userId={}, spaceId={}", savedUserId, savedSpaceId);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Case 1: 현재 코드 — 알림 없음 (베이스라인)
    // TX 범위: SELECT user → SELECT space → SELECT FOR UPDATE → INSERT booking
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("[BEFORE] Case 1: 현재 코드 - 알림 없음 (베이스라인)")
    void case1_currentCodeWithoutNotification() {
        List<Long> measurements = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            BookingCreateRequestDto req = buildDto(savedSpaceId,
                    LocalDateTime.now().plusDays(i + 10).withHour(10).withMinute(0),
                    LocalDateTime.now().plusDays(i + 10).withHour(12).withMinute(0));

            long start = System.currentTimeMillis();
            bookingService.createBooking(req, "timing@test.com");
            measurements.add(System.currentTimeMillis() - start);
        }

        printResult("Case 1: 현재 코드 (알림 없음)", measurements);
        printHikariStats("Case 1 완료 후");
    }

    // ─────────────────────────────────────────────────────────────
    // Case 2: 알림을 동일 트랜잭션 안에 포함 (문제 재현)
    // TX 범위: SELECT user → SELECT space → SELECT FOR UPDATE → INSERT booking
    //        + SELECT user (알림용) → INSERT notification  ← 동일 커넥션!
    //
    // 문제: 커넥션 점유 시간 증가 + 알림 실패 시 예약까지 롤백
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(2)
    @DisplayName("[BEFORE] Case 2: 알림을 트랜잭션 안에 포함 (문제 재현)")
    void case2_withNotificationInsideTransaction() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Long> measurements = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            long start = System.currentTimeMillis();

            txTemplate.execute(status -> {
                BookingCreateRequestDto req = buildDto(savedSpaceId,
                        LocalDateTime.now().plusDays(idx + 30).withHour(10).withMinute(0),
                        LocalDateTime.now().plusDays(idx + 30).withHour(12).withMinute(0));
                bookingService.createBooking(req, "timing@test.com");

                // ❌ 같은 트랜잭션(커넥션) 안에서 알림 처리
                notificationService.createNotification(
                        savedUserId,
                        "예약이 완료되었습니다",
                        "테헤란로 123 예약 완료",
                        NotificationType.BOOKING_CREATED
                );
                return null;
            });

            measurements.add(System.currentTimeMillis() - start);
        }

        printResult("Case 2: 알림 포함 트랜잭션", measurements);
        printHikariStats("Case 2 완료 후");
    }

    // ─────────────────────────────────────────────────────────────
    // Case 3: Outbox 패턴 (실제 구현)
    // TX 범위: SELECT user → SELECT space → SELECT FOR UPDATE → INSERT booking
    //        + INSERT outbox_event 1건 (알림 SELECT/INSERT 없음)
    // 알림 처리는 OutboxPollingPublisher(T2)가 별도 트랜잭션에서 처리
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(3)
    @DisplayName("[AFTER] Case 3: Outbox 패턴 - 실제 outbox_event INSERT")
    void case3_outboxPatternReal() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        List<Long> measurements = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            final int idx = i;
            long start = System.currentTimeMillis();

            txTemplate.execute(status -> {
                BookingCreateRequestDto req = buildDto(savedSpaceId,
                        LocalDateTime.now().plusDays(idx + 60).withHour(10).withMinute(0),
                        LocalDateTime.now().plusDays(idx + 60).withHour(12).withMinute(0));
                // ✅ createBooking() 내부에서 outboxRepository.save() 호출 (T1 동일 트랜잭션)
                // → 알림 SELECT/INSERT 없이 outbox_event 1건만 추가
                bookingService.createBooking(req, "timing@test.com");
                return null;
            });

            measurements.add(System.currentTimeMillis() - start);
        }

        printResult("Case 3: Outbox 패턴 (실제 INSERT)", measurements);
        printHikariStats("Case 3 완료 후");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────

    private BookingCreateRequestDto buildDto(Long spaceId, LocalDateTime start, LocalDateTime end) {
        BookingCreateRequestDto dto = new BookingCreateRequestDto();
        dto.setParkingSpaceId(spaceId);
        dto.setStartTime(start);
        dto.setEndTime(end);
        return dto;
    }

    private void printResult(String label, List<Long> measurements) {
        long avg = (long) measurements.stream().mapToLong(Long::longValue).average().orElse(0);
        long max = measurements.stream().mapToLong(Long::longValue).max().orElse(0);
        long min = measurements.stream().mapToLong(Long::longValue).min().orElse(0);

        log.info("=".repeat(60));
        log.info("[{}] 10회 측정", label);
        log.info("측정값 (ms): {}", measurements);
        log.info("평균: {}ms | 최소: {}ms | 최대: {}ms", avg, min, max);
        log.info("=".repeat(60));
    }

    private void printHikariStats(String label) {
        if (dataSource instanceof HikariDataSource hikari) {
            HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
            if (pool != null) {
                log.info("[Hikari - {}] 활성: {}, 유휴: {}, 대기: {}, 전체: {}",
                        label,
                        pool.getActiveConnections(),
                        pool.getIdleConnections(),
                        pool.getThreadsAwaitingConnection(),
                        pool.getTotalConnections());
            }
        }
    }
}
