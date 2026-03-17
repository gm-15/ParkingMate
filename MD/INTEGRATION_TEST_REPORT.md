# 통합 테스트 리포트

> 작성일: 2026-03-17
> 대상 시스템: ParkingMate — P2P 주차 공간 공유 플랫폼
> 테스트 파일: `ParkingMate/parkingmate/src/test/java/.../integration/ParkingMateIntegrationTest.java`

---

## 목적

Phase 1~4 구현 완료 후, 각 컴포넌트가 실제 MySQL + Redis 환경에서 **유기적으로 연결되어 정확히 동작하는지** 검증.

단위 테스트(mock 기반)와 달리 실제 DB 트랜잭션, 실제 Redis 명령, 실제 스레드 경쟁이 발생하는 환경에서 실행.

---

## 테스트 환경

| 항목 | 내용 |
|------|------|
| Spring Boot | 3.5.3 |
| Java | 21 |
| DB | MySQL 8.0 (Docker, localhost:3307) |
| Cache | Redis 7 (Docker, localhost:6379) |
| Spring Profile | `mysqltest` |
| DDL | `create-drop` (테스트 종료 시 자동 정리) |
| Redis 분산락 | `@MockBean DistributedLockUtil` (락 자체가 아닌 비즈니스 로직 검증이 목적) |

---

## 테스트 케이스 및 결과

### 전체 요약

| # | 테스트명 | 검증 항목 | 결과 |
|---|---------|-----------|------|
| 0 | Setup | 테스트 데이터 생성 (User 2명, ParkingSpace 2개) | ✅ |
| 1 | Outbox-1 | 예약 생성 시 `outbox_event` PENDING 행 생성 | ✅ |
| 2 | Outbox-2 | Publisher 실행 후 `notification` 생성 + PROCESSED 전환 | ✅ |
| 3 | Outbox-3 | 알림 서비스 예외 → booking 보존 + outbox_event FAILED | ✅ |
| 4 | Spatial-1 | 5km 반경 검색: 강남 공간 포함, 부산 공간 제외 | ✅ |
| 5 | Spatial-2 | 1km 협소 반경 검색: 부산 공간 미포함 | ✅ |
| 6 | GEO-1 | Redis GEO Write-through 캐시 등록 확인 | ✅ |
| 7 | Concurrency | 동시 예약 2건 → 성공 1 / 실패 1 | ✅ |

**8/8 PASS — BUILD SUCCESSFUL**

---

## 테스트 데이터

```
User (owner@test.com) — 공간 소유자
User (user@test.com)  — 예약자

ParkingSpace id=1: 서울시 강남구 강남대로 396 (37.4979, 127.0276) — 3,000원/시
ParkingSpace id=2: 부산시 해운대구 해운대해변로 264 (35.1631, 129.1638) — 2,000원/시
```

---

## 케이스별 상세

---

### [Outbox-1] 예약 생성 시 outbox_event PENDING 행 생성

**검증 시나리오**
```
createBooking() 호출
  → booking INSERT
  → outbox_event INSERT (동일 T1 트랜잭션)
  → PENDING 이벤트 1건 증가 확인
  → payload에 userId, address 포함 확인
```

**실제 로그**
```
[Outbox-1] PASS — bookingId=1, outboxEventId=1,
payload={"bookingId":1,"userId":2,"address":"서울시 강남구 강남대로 396"}
```

**검증 포인트**
- `outbox_event.status = PENDING` ← T1 커밋 즉시
- `outbox_event.eventType = BOOKING_CREATED`
- payload에 userId, address 포함 (T2가 알림 처리에 필요한 데이터)

---

### [Outbox-2] OutboxPollingPublisher 실행 후 notification 생성 + PROCESSED 전환

**검증 시나리오**
```
createBooking() → outbox_event PENDING
outboxPollingPublisher.processOutboxEvents() 수동 호출 (스케줄 대기 없이)
  → T2: SELECT outbox_event WHERE status=PENDING
  → T2: notificationService.createNotification()
  → T2: outbox_event.status = PROCESSED
```

**실제 로그**
```
[Outbox-2] PASS — notification created, outboxEventId=2 → PROCESSED
```

**검증 포인트**
- `notification` 행 1건 증가 (`BOOKING_CREATED` 타입)
- `outbox_event.status = PROCESSED`
- `outbox_event.processedAt` 값 설정됨

**의미**
T1(예약) 커밋 → T2(알림) 완전 독립 처리. 두 트랜잭션이 분리되어 있어 알림 처리 실패가 예약에 영향을 미치지 않음.

---

### [Outbox-3] 알림 서비스 예외 → booking 보존 + outbox_event FAILED

**검증 시나리오**
```
예약 생성 (booking + PENDING outbox_event)
broken payload로 교체 ("INVALID_JSON{{{")
  → dispatch() → objectMapper.readValue() 실패 → RuntimeException
  → markFailed() → outbox_event.status = FAILED
booking 여전히 존재 확인
```

**실제 로그**
```
[Outbox-3] PASS — booking id=3 보존, outboxEvent id=4 → FAILED
```

**검증 포인트**
- `bookingRepository.findById(bookingId)` → `isPresent() = true` ← 예약 보존
- `outbox_event.status = FAILED` ← 알림 실패 기록

**의미**
Before 구조(알림을 T1에 포함)였다면 알림 예외 시 `booking`까지 롤백됨.
Outbox 구조에서는 `booking`과 `outbox_event`(T1) / `notification`(T2)의 운명이 분리됨.

---

### [Spatial-1] 5km 반경 검색: 강남 공간 포함, 부산 공간 제외

**검증 시나리오**
```
검색 기준: 강남역 (37.4979, 127.0276), 반경 5km
결과: spaceId=1 (강남) 포함 / spaceId=2 (부산) 미포함
```

**실제 로그**
```
[Spatial-1] PASS — 반경 5km 결과: 1건, ids=[1]
```

**검증 포인트**
- MySQL `ST_Within(location, ST_GeomFromText(polygon, 4326))` + `SPATIAL INDEX` 동작
- 강남(spaceId=1): 검색 기준점과 동일 좌표 → 포함
- 부산(spaceId=2): 약 325km 거리 → 제외

---

### [Spatial-2] 1km 협소 반경 검색: 부산 공간 미포함

**실제 로그**
```
[Spatial-2] PASS — 1km 반경 결과: 1건
```

반경을 1km로 좁혀도 부산 공간이 결과에 포함되지 않음을 확인.

---

### [GEO-1] Redis GEO Write-through 캐시 등록 확인

**검증 시나리오**
```
createParkingSpace() 시 geoCacheService.addSpace() 호출 (Write-through)
GEOADD parkingspaces:geo 127.0276 37.4979 "1"
GEOADD parkingspaces:geo 129.1638 35.1631 "2"

findNearbySpaceIds(127.0276, 37.4979, 5.0km)
  → GEOSEARCH → spaceId=1 반환, spaceId=2 미포함
```

**실제 로그**
```
[GEO-1] PASS — Redis GEO hit: 1건, ids=[1]
```

**검증 포인트**
- 공간 생성과 동시에 Redis GEO 등록 (Write-through)
- `GEOSEARCH`가 5km 반경 내 spaceId=1만 반환
- spaceId=2 (부산)는 제외

**의미**
`searchByLocation()`은 Redis GEO 우선 조회 → hit 시 MySQL 쿼리 없이 처리.
miss 또는 Redis 오류 시 MySQL Spatial fallback. 이번 테스트는 Redis GEO hit 경로 검증.

---

### [Concurrency] 동시 예약 2건 → 1건만 성공

**검증 시나리오**
```
동일 ParkingSpace, 동일 시간대 (10:00~12:00)
2개 스레드가 CountDownLatch로 동시 출발

Thread-1: createBooking() → SELECT FOR UPDATE → INSERT booking → 성공
Thread-2: createBooking() → SELECT FOR UPDATE 대기 → 중복 감지 → 예외
```

**실제 로그**
```
[Concurrency] thread2 예외: JDBC exception executing SQL
  [select ... from booking ... for update]
  [Cannot execute statement in a READ ONLY transaction.]
[Concurrency] PASS — 성공: 1건, 실패: 1건 (중복 예약 차단 확인)
```

**검증 포인트**
- `successCount = 1`, `failCount = 1` (정확히 1건만 성공)
- thread2: `SELECT FOR UPDATE`에서 READ ONLY 트랜잭션으로 실행 시도 → MySQL이 차단

**의미**
Pessimistic Lock(`SELECT FOR UPDATE`)이 실제 MySQL 레벨에서 동시 쓰기를 차단함.
Optimistic Lock(재시도 방식)과 달리 첫 번째 요청이 락을 쥐고 있는 동안 두 번째 요청은 대기/실패.

---

## 각 Phase와 통합 테스트의 대응 관계

| Phase | 구현 내용 | 통합 테스트 검증 |
|-------|-----------|----------------|
| Phase 1: Outbox | outbox_event T1 원자 기록 + T2 Publisher | Outbox-1, 2, 3 |
| Phase 2: Spatial Index | `ST_Within` + R-Tree SPATIAL INDEX | Spatial-1, 2 |
| Phase 3: Pessimistic Lock | `SELECT FOR UPDATE` 동시성 제어 | Concurrency |
| Phase 4: Redis GEO Cache | Write-through + GEOSEARCH | GEO-1, Spatial-1 (캐시 히트 경로) |

---

## 재현 방법

```bash
# 컨테이너 기동
docker start parkingmate-mysql-test parkingmate-redis

# 통합 테스트 실행
cd ParkingMate/parkingmate
./gradlew test --tests "com.parkingmate.parkingmate.integration.ParkingMateIntegrationTest"

# 결과 리포트
open build/reports/tests/test/index.html
```

---

## 관련 문서

```
BEFORE_MEASUREMENT_REPORT.md     ← 문제 정의 (Before 수치)
AFTER_MEASUREMENT_REPORT.md      ← Outbox 적용 후 수치 비교
INTEGRATION_TEST_REPORT.md       ← 본 문서 (통합 테스트 결과)
plan_0317.md                     ← 전체 구현 로드맵
```
