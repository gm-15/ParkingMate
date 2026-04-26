package com.parkingmate.parkingmate.integration;

import com.parkingmate.parkingmate.reservation.domain.Booking;
import com.parkingmate.parkingmate.notification.domain.Notification;
import com.parkingmate.parkingmate.notification.domain.NotificationType;
import com.parkingmate.parkingmate.notification.domain.OutboxEvent;
import com.parkingmate.parkingmate.notification.domain.OutboxEventStatus;
import com.parkingmate.parkingmate.space.domain.ParkingSpace;
import com.parkingmate.parkingmate.user.domain.User;
import com.parkingmate.parkingmate.reservation.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.space.dto.LocationSearchRequest;
import com.parkingmate.parkingmate.common.dto.PageResponse;
import com.parkingmate.parkingmate.space.dto.ParkingSpaceCreateRequestDto;
import com.parkingmate.parkingmate.space.dto.ParkingSpaceResponseDto;
import com.parkingmate.parkingmate.user.repository.UserRepository;
import com.parkingmate.parkingmate.space.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.reservation.repository.BookingRepository;
import com.parkingmate.parkingmate.notification.repository.OutboxRepository;
import com.parkingmate.parkingmate.notification.repository.NotificationRepository;
import com.parkingmate.parkingmate.reservation.service.BookingService;
import com.parkingmate.parkingmate.space.service.ParkingSpaceService;
import com.parkingmate.parkingmate.space.service.GeoCacheService;
import com.parkingmate.parkingmate.notification.service.NotificationService;
import com.parkingmate.parkingmate.notification.service.OutboxPollingPublisher;
import com.parkingmate.parkingmate.common.util.DistributedLockUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ParkingMate 통합 테스트
 *
 * 검증 범위:
 *   1. Outbox 플로우: 예약 → outbox_event PENDING → Publisher 실행 → notification 생성 + PROCESSED
 *   2. 알림 실패 격리: 알림 서비스 예외 → outbox_event FAILED, booking 보존
 *   3. MySQL Spatial Index: POINT 저장 → searchByLocation 반경 내 정확한 결과
 *   4. 동시성 (Pessimistic Lock): 동일 시간대 동시 예약 2건 → 1건만 성공
 *
 * 환경: MySQL Docker (localhost:3307) + Redis Docker (localhost:6379)
 * Profile: mysqltest
 */
@SpringBootTest
@ActiveProfiles("mysqltest")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ParkingMateIntegrationTest {

    // ── 서비스 ──────────────────────────────────────────────────
    @Autowired BookingService bookingService;
    @Autowired ParkingSpaceService parkingSpaceService;
    @Autowired OutboxPollingPublisher outboxPollingPublisher;
    @Autowired NotificationService notificationService;
    @Autowired GeoCacheService geoCacheService;

    // ── 리포지토리 ──────────────────────────────────────────────
    @Autowired UserRepository userRepository;
    @Autowired ParkingSpaceRepository parkingSpaceRepository;
    @Autowired BookingRepository bookingRepository;
    @Autowired OutboxRepository outboxRepository;
    @Autowired NotificationRepository notificationRepository;

    private static final Logger log = LoggerFactory.getLogger(ParkingMateIntegrationTest.class);

    // ── Mock ────────────────────────────────────────────────────
    @MockBean DistributedLockUtil distributedLockUtil;

    // ── 공유 테스트 데이터 ───────────────────────────────────────
    private static Long ownerId;
    private static Long userId;
    private static Long spaceId;     // 위치 좌표 있음 (강남역 근처)
    private static Long spaceIdFar;  // 위치 좌표 있음 (부산, 반경 밖)

    // ─────────────────────────────────────────────────────────────
    // 공통 셋업
    // ─────────────────────────────────────────────────────────────
    @BeforeEach
    void setupMockLock() {
        when(distributedLockUtil.executeWithLock(anyString(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<?>) inv.getArgument(1)).get());
    }

    @Test
    @Order(0)
    @DisplayName("[Setup] 테스트 데이터 생성")
    void setup() {
        // 공간 소유자
        User owner = new User("owner@test.com", "pass", "오너");
        ownerId = userRepository.save(owner).getId();

        // 예약자
        User user = new User("user@test.com", "pass", "유저");
        userId = userRepository.save(user).getId();

        // 공간 — 강남역 근처 (37.4979, 127.0276)
        ParkingSpaceCreateRequestDto dto = new ParkingSpaceCreateRequestDto();
        dto.setAddress("서울시 강남구 강남대로 396");
        dto.setPricePerHour(3000);
        dto.setLatitude(37.4979);
        dto.setLongitude(127.0276);
        spaceId = parkingSpaceService.createParkingSpace(dto, "owner@test.com");

        // 공간 — 부산 해운대 (35.1631, 129.1638) — 반경 5km 밖
        ParkingSpaceCreateRequestDto dtoFar = new ParkingSpaceCreateRequestDto();
        dtoFar.setAddress("부산시 해운대구 해운대해변로 264");
        dtoFar.setPricePerHour(2000);
        dtoFar.setLatitude(35.1631);
        dtoFar.setLongitude(129.1638);
        spaceIdFar = parkingSpaceService.createParkingSpace(dtoFar, "owner@test.com");

        log.info("[Setup] ownerId={}, userId={}, spaceId={}, spaceIdFar={}", ownerId, userId, spaceId, spaceIdFar);
        assertThat(spaceId).isNotNull();
        assertThat(spaceIdFar).isNotNull();
    }

    // ─────────────────────────────────────────────────────────────
    // 1. Outbox 플로우 통합 테스트
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(1)
    @DisplayName("[Outbox-1] 예약 생성 시 outbox_event PENDING 행 생성 확인")
    void outbox_booking_creates_pending_event() {
        // Given
        long pendingBefore = outboxRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING).size();

        BookingCreateRequestDto req = bookingRequest(spaceId, 1, 10, 12);

        // When
        Long bookingId = bookingService.createBooking(req, "user@test.com");

        // Then
        List<OutboxEvent> pendingEvents = outboxRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        long pendingAfter = pendingEvents.size();

        assertThat(bookingId).isNotNull();
        assertThat(pendingAfter).isGreaterThan(pendingBefore);

        OutboxEvent event = pendingEvents.stream()
                .filter(e -> e.getAggregateId().equals(bookingId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("bookingId=" + bookingId + "에 대한 outbox_event 없음"));

        assertThat(event.getEventType()).isEqualTo("BOOKING_CREATED");
        assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
        assertThat(event.getPayload()).contains("userId");
        log.info("[Outbox-1] PASS — bookingId={}, outboxEventId={}, payload={}",
                bookingId, event.getId(), event.getPayload());
    }

    @Test
    @Order(2)
    @DisplayName("[Outbox-2] OutboxPollingPublisher 실행 후 notification 생성 및 PROCESSED 전환 확인")
    void outbox_publisher_processes_pending_and_creates_notification() throws InterruptedException {
        // Given: 새 예약 생성 (PENDING 이벤트 1건 추가)
        long notificationBefore = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId).size();
        BookingCreateRequestDto req = bookingRequest(spaceId, 2, 14, 16);
        Long bookingId = bookingService.createBooking(req, "user@test.com");

        OutboxEvent pendingEvent = outboxRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
                .stream().filter(e -> e.getAggregateId().equals(bookingId)).findFirst()
                .orElseThrow();
        assertThat(pendingEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);

        // When: Publisher 수동 실행 (스케줄 대기 없이)
        outboxPollingPublisher.processOutboxEvents();

        // Then: notification 생성 확인
        List<Notification> notifications = notificationRepository.findByUser_IdOrderByCreatedAtDesc(userId);
        assertThat(notifications.size()).isGreaterThan((int) notificationBefore);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.BOOKING_CREATED);

        // Then: outbox_event PROCESSED 전환 확인
        OutboxEvent processed = outboxRepository.findById(pendingEvent.getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(OutboxEventStatus.PROCESSED);
        assertThat(processed.getProcessedAt()).isNotNull();

        log.info("[Outbox-2] PASS — notification created, outboxEventId={} → PROCESSED", processed.getId());
    }

    @Test
    @Order(3)
    @DisplayName("[Outbox-3] 알림 서비스 예외 발생 시 outbox_event FAILED, booking 보존 확인")
    void outbox_notification_failure_preserves_booking() {
        // Given: 예약 생성
        BookingCreateRequestDto req = bookingRequest(spaceId, 3, 16, 18);
        Long bookingId = bookingService.createBooking(req, "user@test.com");

        OutboxEvent pendingEvent = outboxRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
                .stream().filter(e -> e.getAggregateId().equals(bookingId)).findFirst()
                .orElseThrow();

        // NotificationService를 SpyBean 대신 payload 파싱 오류로 실패 유도:
        // payload의 userId를 강제로 null로 만들 수 없으므로 DB에서 직접 이벤트 조작
        // → payload를 잘못된 JSON으로 교체
        OutboxEvent broken = outboxRepository.findById(pendingEvent.getId()).orElseThrow();
        // broken payload를 직접 수정할 수 없으므로 publisher에서 예외가 발생하도록
        // eventType을 UNKNOWN으로 변경 → dispatch()에서 default 분기 → markFailed 없이 통과
        // 대신: payload를 broken JSON으로 직접 update 쿼리 없이,
        // processInNewTransaction에서 dispatch 예외가 나도록 payload를 corrupt
        //
        // 가장 단순한 방법: Publisher에서 처리 전 이벤트의 payload를 broken json으로 update
        // → JPA로 직접 save 후 flush
        //
        // broken payload → objectMapper.readValue 실패 → RuntimeException → markFailed
        //
        // 우회: 실제 broken outbox event를 새로 INSERT
        OutboxEvent brokenEvent = new OutboxEvent("BOOKING", bookingId, "BOOKING_CREATED", "INVALID_JSON{{{");
        outboxRepository.save(brokenEvent);
        outboxRepository.delete(broken); // 원본 PENDING 제거

        // When: Publisher 실행
        outboxPollingPublisher.processOutboxEvents();

        // Then: booking은 여전히 존재
        assertThat(bookingRepository.findById(bookingId)).isPresent();

        // Then: brokenEvent는 FAILED
        OutboxEvent result = outboxRepository.findById(brokenEvent.getId()).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(OutboxEventStatus.FAILED);

        log.info("[Outbox-3] PASS — booking id={} 보존, outboxEvent id={} → FAILED",
                bookingId, brokenEvent.getId());
    }

    // ─────────────────────────────────────────────────────────────
    // 2. MySQL Spatial Index + Redis GEO 통합 테스트
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(4)
    @DisplayName("[Spatial-1] 반경 5km 검색: 강남역 근처 공간만 반환, 부산 공간 제외 확인")
    void spatial_search_returns_only_nearby_spaces() {
        // Given: 강남역 좌표 (37.4979, 127.0276) 기준 5km 반경 검색
        LocationSearchRequest req = new LocationSearchRequest();
        req.setLatitude(37.4979);
        req.setLongitude(127.0276);
        req.setRadiusKm(5.0);
        req.setPage(0);
        req.setSize(10);

        // When: Redis GEO → MySQL Spatial fallback 중 하나로 처리
        PageResponse<ParkingSpaceResponseDto> result = parkingSpaceService.searchByLocation(req);

        // Then: 강남 공간 포함
        List<Long> returnedIds = result.getContent().stream()
                .map(ParkingSpaceResponseDto::getId)
                .toList();
        assertThat(returnedIds).contains(spaceId);

        // Then: 부산 공간 미포함
        assertThat(returnedIds).doesNotContain(spaceIdFar);

        log.info("[Spatial-1] PASS — 반경 5km 결과: {}건, ids={}", result.getTotalElements(), returnedIds);
    }

    @Test
    @Order(5)
    @DisplayName("[Spatial-2] 반경 1km 협소 검색: 결과 없음 또는 정확한 근거리 공간만 반환")
    void spatial_search_narrow_radius() {
        // Given: 강남역 기준 1km 반경 (강남 공간이 정확히 강남역이므로 포함 가능)
        LocationSearchRequest req = new LocationSearchRequest();
        req.setLatitude(37.4979);
        req.setLongitude(127.0276);
        req.setRadiusKm(1.0);
        req.setPage(0);
        req.setSize(10);

        // When
        PageResponse<ParkingSpaceResponseDto> result = parkingSpaceService.searchByLocation(req);

        // Then: 부산 공간 없음
        assertThat(result.getContent().stream()
                .map(ParkingSpaceResponseDto::getId)
                .toList())
                .doesNotContain(spaceIdFar);

        log.info("[Spatial-2] PASS — 1km 반경 결과: {}건", result.getTotalElements());
    }

    @Test
    @Order(6)
    @DisplayName("[GEO-1] Redis GEO 캐시에 공간 등록 확인 (GEOADD Write-through)")
    void geo_cache_write_through_on_create() {
        // When: spaceId는 이미 setup()에서 createParkingSpace 시 geoCacheService.addSpace() 호출됨
        List<Long> nearby = geoCacheService.findNearbySpaceIds(127.0276, 37.4979, 5.0);

        // Then: Redis GEO에 spaceId 포함
        assertThat(nearby).contains(spaceId);
        assertThat(nearby).doesNotContain(spaceIdFar);

        log.info("[GEO-1] PASS — Redis GEO hit: {}건, ids={}", nearby.size(), nearby);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. 동시성 (Pessimistic Lock) 통합 테스트
    // ─────────────────────────────────────────────────────────────
    @Test
    @Order(7)
    @DisplayName("[Concurrency] 동일 시간대 동시 예약 2건 → 1건만 성공, 1건 예외 발생 확인")
    void concurrency_only_one_booking_succeeds() throws InterruptedException {
        // Given: 동일 시간대 2개 스레드가 동시에 예약 시도
        LocalDateTime start = LocalDateTime.now().plusDays(90).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        BookingCreateRequestDto req1 = buildDto(spaceId, start, end);
        BookingCreateRequestDto req2 = buildDto(spaceId, start, end);

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);

        ExecutorService exec = Executors.newFixedThreadPool(2);

        Runnable task1 = () -> {
            ready.countDown();
            try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try {
                bookingService.createBooking(req1, "user@test.com");
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.info("[Concurrency] thread1 예외: {}", e.getMessage());
            }
        };

        Runnable task2 = () -> {
            ready.countDown();
            try { go.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            try {
                bookingService.createBooking(req2, "user@test.com");
                successCount.incrementAndGet();
            } catch (Exception e) {
                failCount.incrementAndGet();
                log.info("[Concurrency] thread2 예외: {}", e.getMessage());
            }
        };

        exec.submit(task1);
        exec.submit(task2);
        ready.await(); // 두 스레드 모두 준비될 때까지 대기
        go.countDown(); // 동시 출발

        exec.shutdown();
        exec.awaitTermination(10, TimeUnit.SECONDS);

        // Then: 정확히 1건 성공, 1건 실패
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);

        log.info("[Concurrency] PASS — 성공: {}건, 실패: {}건 (중복 예약 차단 확인)",
                successCount.get(), failCount.get());
    }

    // ─────────────────────────────────────────────────────────────
    // 헬퍼
    // ─────────────────────────────────────────────────────────────
    private BookingCreateRequestDto bookingRequest(Long spaceId, int dayOffset, int startHour, int endHour) {
        LocalDateTime start = LocalDateTime.now().plusDays(dayOffset + 10)
                .withHour(startHour).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.withHour(endHour);
        return buildDto(spaceId, start, end);
    }

    private BookingCreateRequestDto buildDto(Long spaceId, LocalDateTime start, LocalDateTime end) {
        BookingCreateRequestDto dto = new BookingCreateRequestDto();
        dto.setParkingSpaceId(spaceId);
        dto.setStartTime(start);
        dto.setEndTime(end);
        return dto;
    }
}
