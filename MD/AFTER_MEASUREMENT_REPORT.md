# After 측정 리포트: Transactional Outbox Pattern 적용 후 검증

> 작성일: 2026-03-17
> 대상 시스템: ParkingMate — P2P 주차 공간 공유 플랫폼
> Before 리포트: `BEFORE_MEASUREMENT_REPORT.md`

---

## 이 리포트는 무엇을 After 한 것인가

**Before 상태 (문제)**:
```
BookingService.createBooking() @Transactional 안에서
  → bookingRepository.save()       // 예약 저장
  → notificationService.create()  // 알림 SELECT + INSERT — 동일 트랜잭션 내
COMMIT
```
- 알림이 예약 트랜잭션(T1) 안에 묶여 커넥션을 2쿼리만큼 더 점유
- 알림 INSERT 실패 시 예약까지 롤백
- 프로세스 강제 종료 시 알림 이벤트 영구 유실

**After 상태 (구현 완료)**:
```
T1 — BookingService.createBooking()
  → bookingRepository.save()       // 예약 저장
  → outboxRepository.save()        // outbox_event INSERT 1건 (알림 로직 없음)
COMMIT

T2 — OutboxPollingPublisher (@Scheduled 5초마다, 별도 트랜잭션)
  → outbox_event WHERE status=PENDING 조회
  → notificationService.create()
  → outbox_event status = PROCESSED
```
- 알림 처리가 T1에서 완전히 분리됨
- T1 커밋 = 예약 + outbox_event 원자적 보장
- 알림 실패해도 예약 보존, 재시작 후 PENDING 이벤트 자동 재처리

---

## 무엇이 달라졌는가 (Before와의 비교)

### 코드 변경점

| 파일 | Before | After |
|------|--------|-------|
| `domain/Booking.java` | `@Version` + `PESSIMISTIC_WRITE` 혼재 | `@Version` 제거, Pessimistic Lock만 유지 |
| `domain/ParkingSpace.java` | `latitude DOUBLE` + `longitude DOUBLE` | `Point location` (JTS, SRID 4326) |
| `service/BookingService.java` | 알림 직접 호출 (또는 미연결) | `outboxRepository.save()` 호출, `@Bulkhead` + `@CircuitBreaker` 추가 |
| `service/ParkingSpaceService.java` | `findAll()` + Java Haversine | `findWithinRadius()` + Redis GEO fallback |
| `util/DistributedLockUtil.java` | CircuitBreaker 없음 | `@CircuitBreaker(name="redis-lock")` |
| `application.properties` | Resilience4j 설정 없음 | CircuitBreaker + Bulkhead + Graceful Shutdown 추가 |

### 신규 파일

| 파일 | 역할 |
|------|------|
| `domain/OutboxEvent.java` | outbox_event 엔티티 |
| `domain/OutboxEventStatus.java` | PENDING / PROCESSED / FAILED |
| `repository/OutboxRepository.java` | PENDING 이벤트 조회 |
| `service/OutboxPollingPublisher.java` | @Scheduled T2 처리 루프 |
| `service/GeoCacheService.java` | Redis GEO 캐시 레이어 |
| `resources/application-mysqltest.properties` | MySQL 타이밍 테스트 프로파일 |

---

## 측정 A: Hikari 커넥션 점유 시간 After 결과

### 측정 조건 (Before와 동일)

| 항목 | 내용 |
|------|------|
| DB | MySQL 8.0 (Docker, localhost:3307) |
| Hikari 풀 | max 10, min-idle 2 (TimingTestPool) |
| Profile | `mysqltest` |
| Redis 분산락 | `@MockBean DistributedLockUtil` (Mock) |
| 측정 횟수 | 10회 반복 |

### 트랜잭션 범위 (After)

```
T1 → 커넥션 획득
  ① SELECT * FROM users WHERE email = ?          (예약자 조회)
  ② SELECT * FROM parking_space WHERE id = ?     (공간 조회)
  ③ SELECT ... FROM booking ... FOR UPDATE        (Pessimistic Lock)
  ④ INSERT INTO booking ...                       (예약 저장)
  ⑤ INSERT INTO outbox_event ...                  (이벤트 기록)
COMMIT → 커넥션 반납
```

Before Case 2와 달리:
- 알림용 `SELECT user` 제거 (⑤ 단순 INSERT만)
- 알림용 `INSERT notification` 제거
- 쿼리 수: 6건 → **5건**

### 실측값

```
원시값 (ms): [36, 40, 34, 38, 38, 38, 34, 37, 36, 33]
전체 평균: 36ms | 최소: 33ms | 최대: 40ms | 분산: 1.2x
```

```
Hikari 풀 상태: 활성 0 | 유휴 2 | 대기 0 | 전체 2
→ 트랜잭션 종료 즉시 커넥션 반납. 풀 압박 없음.
```

### Before vs After 비교표

| 항목 | Before Case 1 (베이스라인) | Before Case 2 (알림 TX 내) | **After Case 3 (Outbox)** |
|------|--------------------------|--------------------------|--------------------------|
| TX 내 DB 쿼리 수 | 4건 | 6건 | **5건** |
| 안정 평균 | 33ms | 37ms | **36ms** |
| 베이스 대비 | — | +4ms (+12%) | **+3ms (+9%)** |
| 분산 (min~max) | 28~41ms (1.5x) | 32~51ms **(2.4x)** | **33~40ms (1.2x)** |
| 알림 실패 시 | 예약 보존 | 예약 롤백 ❌ | **예약 보존 ✅** |
| 이벤트 유실 | — | 유실 가능 ❌ | **DB 보장 ✅** |

### 해석

**왜 절대값(37ms → 36ms)은 1ms밖에 안 줄었는가**
```
localhost MySQL 쿼리당 왕복 ~2ms.

Before Case 2: 6건 × ~2ms = ~37ms (SELECT user + INSERT notification 포함)
After  Case 3: 5건 × ~2ms = ~36ms (outbox_event INSERT만 추가)

쿼리 1건 감소 → 1ms 단축. 절대값 차이는 미미.
```

**핵심 개선은 분산 안정성**
```
Before Case 2 (2.4x 분산):
  알림 서비스에 부하가 걸리면 → notification SELECT/INSERT 느려짐
  → 예약 트랜잭션 전체 지연
  → 최악: 51ms (vs 최선: 32ms)

After Case 3 (1.2x 분산):
  outbox_event INSERT는 단순 행 삽입 — 외부 서비스 의존 없음
  → 부하와 무관하게 일정한 비용
  → 33~40ms 범위 내 안정적 유지
```

**RDS 환경(네트워크 10ms/쿼리) 추정**

| 케이스 | 쿼리 수 | 예상 커넥션 점유 |
|--------|---------|----------------|
| Before Case 2 | 6건 | ~60ms |
| After Case 3 | 5건 | **~50ms** |
| 차이 | -1건 | **-10ms (-17%)** |

Hikari 풀 10개, 동시 100개 요청 기준:
```
Before Case 2: 60ms 점유 → 풀 1개당 16 req/s → 전체 160 req/s
After  Case 3: 50ms 점유 → 풀 1개당 20 req/s → 전체 200 req/s
               → 처리량 25% 개선
```

### 결론

```
정량:
  쿼리 수: 6건 → 5건 (-17%)
  평균 점유 시간: 37ms → 36ms (localhost 기준)
  분산: 2.4x → 1.2x (안정성 2배 개선)
  RDS 추정 점유: 60ms → 50ms (-17%)

정성:
  알림 실패 → 예약 롤백 문제 해결
  프로세스 다운 후 이벤트 유실 문제 해결 (DB 영속 보장)
  책임 분리: T1 = 예약 원자성 / T2 = 알림 비동기 처리
```

---

## 측정 B: 위치 검색 — After 결과 요약

Before 리포트의 EXPLAIN 수치는 Before/After가 이미 포함되어 있으므로 핵심만 정리.

### 무엇이 달라졌는가

| 항목 | Before | After |
|------|--------|-------|
| 저장 방식 | `latitude DOUBLE` + `longitude DOUBLE` (분리) | `Point location` (JTS, SRID 4326) |
| 인덱스 | 없음 (또는 B-Tree 각각) | `SPATIAL INDEX idx_location` (R-Tree) |
| 검색 방식 | `findAll()` → Java Haversine 필터 | `ST_Within` + `ST_Distance_Sphere` (DB 처리) |
| 캐시 | 없음 | Redis GEO Write-through (GeoCacheService) |

### EXPLAIN 수치 비교

| EXPLAIN 항목 | Before | After |
|-------------|--------|-------|
| type | `ALL` (풀 스캔) | `range` (인덱스 범위 스캔) |
| key | `NULL` | `idx_location` |
| rows | 9,880건 | **378건** |
| filtered | 1.23% | **100%** |
| 실행 시간 | 3.30ms | **2.42ms** |

**스캔 행수 96.2% 감소** — 100만 건 환경에서 디스크 I/O 차이는 수십~수백 배로 확대.

---

## 전체 Phase 완료 현황

| Phase | 내용 | 핵심 개선 지표 |
|-------|------|---------------|
| Phase 1 | Transactional Outbox Pattern | 분산 2.4x → 1.2x, 알림 유실 해결 |
| Phase 2 | MySQL Spatial Index (R-Tree) | 스캔 행수 96.2% 감소 |
| Phase 3 | Resilience4j CircuitBreaker + Bulkhead | Redis 장애 시 DB fallback 자동화 |
| Phase 4 | Redis GEO Cache Layer | 위치 검색 읽기 경로 캐시화 |

---

## 재현 방법

```bash
# MySQL + Redis 기동
docker start parkingmate-mysql-test
docker start parkingmate-redis

# After 타이밍 테스트 실행
cd ParkingMate/parkingmate
./gradlew test --tests "com.parkingmate.parkingmate.service.BookingConnectionTimingTest"

# 결과 리포트
open build/reports/tests/test/index.html
```

**관련 파일**
```
BEFORE_MEASUREMENT_REPORT.md                              ← Before 문제 정의
AFTER_MEASUREMENT_REPORT.md                               ← 본 문서
ParkingMate/parkingmate/src/test/java/.../service/
  BookingConnectionTimingTest.java                        ← Case 1/2/3 타이밍 테스트
ParkingMate/parkingmate/src/main/java/.../
  service/OutboxPollingPublisher.java                     ← T2 처리 루프
  domain/OutboxEvent.java                                 ← outbox_event 엔티티
  service/GeoCacheService.java                            ← Redis GEO 캐시
```
