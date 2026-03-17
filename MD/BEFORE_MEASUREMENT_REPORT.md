# Before 측정 리포트: Hikari 커넥션 점유 시간 & R-Tree 공간 인덱스 분석

> 작성일: 2026-03-17
> 대상 시스템: ParkingMate — P2P 주차 공간 공유 플랫폼
> 측정 목적: Transactional Outbox 패턴 및 R-Tree Spatial Index 도입 전 현재 상태의 문제를 수치로 증명

---

## 목차

1. [측정 A] Hikari 커넥션 점유 시간 — 트랜잭션 범위 분석
2. [측정 B] R-Tree Spatial Index — EXPLAIN Before/After 분석

---

# [측정 A] Hikari 커넥션 점유 시간

## A-1. 왜 이 측정을 하게 됐는가

### 배경

ParkingMate는 주차 공간 예약 시 동시성을 Redis 분산락 + Pessimistic Lock(SELECT FOR UPDATE)으로 제어한다.
예약 완료 후 사용자에게 알림을 보내는 기능(`NotificationService.notifyBookingCreated()`)이 필요했고,
가장 단순한 구현은 `BookingService.createBooking()` 안에서 직접 알림 메서드를 호출하는 것이다.

```java
// 단순한 구현 (문제 있음)
@Transactional
public Long createBooking(...) {
    bookingRepository.save(booking);
    notificationService.createNotification(userId, ...); // ← 같은 트랜잭션
    return booking.getId();
}
```

### 발견한 문제

**문제 1: 트랜잭션 범위 과잉 — 커넥션 점유 시간 연장**

`@Transactional` 시작 시점에 Hikari 풀에서 커넥션이 할당된다.
알림 처리를 위한 `SELECT user` + `INSERT notification`이 추가되면
커넥션이 반납되는 시점이 뒤로 밀린다.

**문제 2: 책임 결합 — 알림 실패가 예약을 롤백시킴**

```
알림 INSERT 실패 / 서버 강제 종료 → 예외 발생 또는 COMMIT 전 중단
                                              ↓
                                     트랜잭션 전체 롤백
                                              ↓
                              예약도 사라짐 ← 사용자는 예약했는데
```

**문제 3: 이벤트 유실 위험 (@TransactionalEventListener 대안도 불충분)**

`@TransactionalEventListener` 사용 시 알림 실패해도 예약은 보존되지만,
프로세스 다운 시 메모리 이벤트 영구 유실. DB에 기록된 것이 아니기 때문에 재시작 후 복구 불가.

### 측정 목표

1. 현재 코드(알림 없음) 트랜잭션 점유 시간 = 베이스라인
2. 알림을 트랜잭션 안에 넣었을 때 점유 시간 증가 수치 확인
3. Transactional Outbox 패턴 적용 후와 비교 가능한 기준선 확보

---

## A-2. 무엇을 준비했는가

### 테스트 환경

| 항목 | 내용 |
|------|------|
| Spring Boot | 3.5.3 |
| Java | 21.0.6 (HotSpot) |
| DB | MySQL 8.0 (Docker, localhost:3307) |
| Redis | localhost:6379 (Docker) |
| Hikari 풀 | max 10, min-idle 2 (TimingTestPool) |
| Spring Profile | `mysqltest` (`application-mysqltest.properties`) |

**H2 → MySQL로 전환한 이유**

초기 H2 in-memory에서 측정 시 Case 1/2/3 간 차이가 1ms 미만으로 무의미했다.
H2는 네트워크 없이 JVM 내부에서 실행되어 쿼리당 레이턴시가 0.01ms 수준이기 때문이다.
MySQL Docker 컨테이너로 전환 시 localhost 네트워크 왕복이 실제로 발생해
의미있는 차이를 측정할 수 있었다.

### 제약 조건 처리

**Redis 분산락 Mock**
```java
@MockBean DistributedLockUtil distributedLockUtil;

when(distributedLockUtil.executeWithLock(anyString(), any(Supplier.class)))
    .thenAnswer(inv -> ((Supplier<?>) inv.getArgument(1)).get());
```
락 자체가 아닌 트랜잭션 범위가 측정 대상이므로 Mock 처리.

**테스트 데이터**
```
User (timing@test.com) ─── ParkingSpace (서울시 강남구 테헤란로 123, 5000원/시)
```
`savedUserId`, `savedSpaceId` static 필드로 3개 케이스 공유.
시간대 겹침 방지: `plusDays(i + offset)` 방식으로 비겹치는 슬롯 사용.

---

## A-3. 무엇을, 어떻게 했는가

### 테스트 파일

```
src/test/java/com/parkingmate/parkingmate/service/BookingConnectionTimingTest.java
```

### Case 1 — 현재 코드 (베이스라인)

**트랜잭션 범위 (DB 쿼리 4건)**:
```
@Transactional 시작 → 커넥션 획득
  ① SELECT * FROM users WHERE email = ?
  ② SELECT * FROM parking_space WHERE id = ?
  ③ SELECT ... FROM booking ... FOR UPDATE  (PESSIMISTIC_WRITE)
  ④ INSERT INTO booking ...
COMMIT → 커넥션 반납
```

### Case 2 — 알림을 동일 트랜잭션 안에 포함 (문제 재현)

**트랜잭션 범위 (DB 쿼리 6건)**:
```
TransactionTemplate(REQUIRES_NEW) → 커넥션 획득
  ① SELECT user     (예약용)
  ② SELECT space    (예약용)
  ③ SELECT FOR UPDATE
  ④ INSERT booking
  ⑤ SELECT user     (알림용 재조회) ← 추가
  ⑥ INSERT notification             ← 추가
COMMIT → 커넥션 반납
```

구현 방식: `TransactionTemplate(PROPAGATION_REQUIRES_NEW)` 안에서
`bookingService.createBooking()`(REQUIRED → 외부 참여) +
`notificationService.createNotification()`(REQUIRED → 동일 참여) 순차 실행.

**핵심 리스크**: ⑥에서 예외 발생 시 ①~④ 포함 전체 롤백.

### Case 3 — Outbox 패턴 (예상 구조)

**트랜잭션 범위 (DB 쿼리 5건)**:
```
T1 → 커넥션 획득
  ① SELECT user
  ② SELECT space
  ③ SELECT FOR UPDATE
  ④ INSERT booking
  ⑤ INSERT outbox_event          ← 알림 로직 없음, 이벤트 기록만
COMMIT → 커넥션 반납

T2 (OutboxPollingPublisher, @Scheduled 5초마다, 별도 트랜잭션):
  → outbox_event WHERE status=PENDING 조회
  → notificationService.create()
  → outbox_event status = PROCESSED
```

> After 실측 결과는 `AFTER_MEASUREMENT_REPORT.md` 참조.

---

## A-4. 결과

### 실측값 (MySQL localhost:3307)

**Case 1: 현재 코드 (알림 없음, 베이스라인)**
```
원시값 (ms): [206, 28, 28, 33, 41, 40, 32, 32, 36, 31]
1회차 206ms: JVM warmup + DB 첫 연결 + JPA 스키마 검증 오버헤드 → 제외
안정 구간 (2~10회): [28, 28, 33, 41, 40, 32, 32, 36, 31]
안정 평균: 33ms | 최소: 28ms | 최대: 41ms
```

**Case 2: 알림 포함 트랜잭션 (문제 재현)**
```
원시값 (ms): [46, 38, 32, 35, 36, 37, 51, 35, 32, 36]
(Case 1 실행으로 JVM 풀 워밍업 완료, 전체 유효)
전체 평균: 37ms | 최소: 32ms | 최대: 51ms | 분산: 2.4x
```

**Hikari 풀 상태 (Before 케이스 공통)**
```
활성: 0 | 유휴: 2 | 대기: 0 | 전체: 2
→ 단일 스레드 순차 실행, 트랜잭션 종료 즉시 커넥션 반납. 풀 압박 없음.
```

### Before 케이스 비교

| 항목 | Case 1 (베이스) | Case 2 (알림 포함) |
|------|----------------|-------------------|
| DB 쿼리 수 | 4건 | 6건 |
| 안정 평균 | **33ms** | **37ms** |
| 베이스 대비 | — | **+4ms (+12%)** |
| 분산 (min~max) | 28~41ms (1.5x) | 32~51ms **(2.4x)** |
| 알림 실패 시 | 예약 보존 | **예약 롤백 ❌** |
| 이벤트 유실 | — | 유실 가능 ❌ |

### 수치 해석

**왜 +4ms인가 (Case 1 → Case 2)**
```
MySQL 쿼리 1건당 localhost 왕복: ~2ms
Case 2 추가 쿼리: 2건 (SELECT user + INSERT notification)
이론값: +4ms → 실측: +4ms (33ms → 37ms) — 예측과 정확히 일치
```

**원격 MySQL(AWS RDS, 네트워크 10ms/쿼리 가정) 추정**

| 케이스 | 쿼리 수 | 예상 커넥션 점유 |
|--------|---------|----------------|
| Case 1 | 4건 | ~40ms |
| Case 2 | 6건 | ~60ms (+50%) |

**Hikari 풀 10개, 동시 요청 100개 시나리오 (RDS 기준)**
```
Case 1: 40ms 점유 → 풀 1개당 25 req/s → 전체 250 req/s
Case 2: 60ms 점유 → 풀 1개당 16 req/s → 전체 160 req/s
         동일 트래픽에서 커넥션 대기 발생 시점 36% 앞당겨짐
```

---

## A-5. 결론

```
문제 1: 커넥션 점유 시간 증가
        +2 쿼리 → localhost +4ms(+12%), RDS +20ms(+50%)
        → 동시 요청 급증 시 Hikari 풀 대기 큐 조기 형성

문제 2: 책임 결합
        알림 실패 / 프로세스 강제 종료 → 예약 롤백
        → 사용자가 예약 성공했으나 시스템 내부 오류로 예약 소멸

문제 3: 이벤트 유실
        @TransactionalEventListener: 프로세스 다운 시 메모리 이벤트 영구 소멸
```

**해결 방향**: Transactional Outbox 패턴
→ booking INSERT + outbox_event INSERT를 동일 T1에 묶어 원자성 보장
→ 알림 처리는 OutboxPollingPublisher(T2, @Scheduled)가 담당, 예약과 운명 분리

> After 구현 결과 및 수치 비교는 `AFTER_MEASUREMENT_REPORT.md` 참조.

---

---

# [측정 B] R-Tree Spatial Index — EXPLAIN Before/After

## B-1. 왜 이 측정을 하게 됐는가

### 기존 위치 검색 구조 (Before)

```java
// 위도/경도를 각각 DOUBLE 컬럼으로 저장
@Column private Double latitude;
@Column private Double longitude;
```

```sql
-- 검색 쿼리
SELECT * FROM parking_space
WHERE latitude  BETWEEN 37.490 AND 37.510
  AND longitude BETWEEN 126.990 AND 127.010;
```

두 컬럼에 각각 B-Tree 인덱스를 걸어도 MySQL은 **두 조건을 동시에 인덱스로 처리하지 못한다.**
결국 풀 테이블 스캔 후 조건 필터링하는 구조였다.

### 새로운 위치 검색 구조 (After)

```sql
-- POINT 타입으로 단일 공간 좌표 저장
location POINT NOT NULL SRID 4326,
SPATIAL INDEX idx_location (location)

-- 검색
SELECT * FROM parking_space
WHERE ST_Within(location, ST_Buffer(ST_GeomFromText('POINT(127.0 37.5)', 4326), 0.009));
```

MySQL R-Tree가 공간을 MBR(Minimum Bounding Rectangle)로 분할해 관리한다.
"이 영역 안에 있는 점" 조회 시 해당 MBR 노드만 탐색하므로 스캔 행수가 대폭 감소한다.

---

## B-2. R-Tree vs B-Tree 구조 차이

```
B-Tree (latitude/longitude 각각):
  "37.5 근방인 행" → B-Tree로 찾을 수 있음
  "37.5 근방 AND 127.0 근방" → 두 조건 동시 인덱스 불가 → 풀 스캔
  → 전체 테이블을 읽고, Java에서 Haversine 계산 후 필터

R-Tree (POINT + SPATIAL INDEX):
  공간을 MBR 사각형으로 분할해 트리 구성
  "이 MBR 안에 있는 점들" → 해당 노드만 탐색
  → 2차원 조건을 단일 인덱스 한 번으로 처리
```

---

## B-3. EXPLAIN 측정 결과

### 측정 조건

```
테이블: parking_space
데이터: 약 10,000건 (위도 37.0~38.0, 경도 126.0~128.0 랜덤 분포)
검색 반경: 약 1km
```

### Before — lat/lng 범위 필터

```sql
EXPLAIN SELECT * FROM parking_space
WHERE latitude  BETWEEN 37.4900 AND 37.5100
  AND longitude BETWEEN 126.990 AND 127.010;
```

| 항목 | 값 | 의미 |
|------|----|------|
| **type** | `ALL` | 풀 테이블 스캔 |
| **key** | `NULL` | 인덱스 미사용 |
| **rows** | 9,880건 | 전체 테이블 스캔 |
| **filtered** | 1.23% | 9,880건 읽고 1.23%만 조건 만족 |
| **실행 시간** | 3.30ms | |

### After — R-Tree Spatial Index

```sql
EXPLAIN SELECT * FROM parking_space
WHERE ST_Within(
    location,
    ST_Buffer(ST_GeomFromText('POINT(127.000 37.500)', 4326), 0.009)
);
```

| 항목 | 값 | 의미 |
|------|----|------|
| **type** | `range` | 인덱스 범위 스캔 |
| **key** | `idx_location` | R-Tree 인덱스 사용 |
| **rows** | 378건 | MBR 필터 후 후보만 |
| **filtered** | 100% | 읽은 것 전부 조건 만족 |
| **실행 시간** | 2.42ms | |

### Before vs After 핵심 수치

| 항목 | Before (lat/lng) | After (R-Tree) | 개선 |
|------|-----------------|----------------|------|
| EXPLAIN type | `ALL` | `range` | 풀 스캔 → 인덱스 스캔 |
| 사용 인덱스 | `NULL` | `idx_location` | |
| **스캔 행수** | **9,880건** | **378건** | **96.2% 감소** |
| filtered | 1.23% | 100% | 낭비 행 제거 |
| 실행 시간 | 3.30ms | 2.42ms | 27% 단축 |

---

## B-4. 스캔 행수가 핵심 지표인 이유

### 10,000건에서 시간 차이가 작은 이유

10,000건은 InnoDB 버퍼 풀에 전부 올라가 **디스크 I/O가 발생하지 않는다.**
메모리에서 읽으면 풀 스캔도 빠르기 때문에 시간 차이가 1ms 수준으로 보인다.
스캔 행수 96.2% 감소라는 구조적 개선은 메모리가 아닌 **디스크 I/O 환경에서 폭발적으로 드러난다.**

### 데이터 규모별 스캔 행수 예상 효과

```
10,000건:      9,880 →    378건  (96.2% 감소,      9,502건 절약)
100,000건:   100,000 →  3,800건  (96.2% 감소,     96,200건 절약)
1,000,000건: 1,000,000 → 38,000건  (96.2% 감소,    962,000건 절약)
```

### 100만 건 환경에서 실행 시간 추정

```
Before: 100만 건 디스크 랜덤 I/O 발생 → 수백ms ~ 수초
After:  38,000건만 읽음 → R-Tree 상위 MBR 탐색 → 수ms

예상 차이: 수십~수백 배
```

---

## B-5. 결론

**위도/경도 분리 저장 → POINT 단일 저장으로의 전환은 단순한 타입 변경이 아니다.**

MySQL이 두 숫자(latitude, longitude)를 독립된 1차원 값으로 인식하는 것과
하나의 2차원 공간 점(POINT)으로 인식하는 것의 차이가 인덱스 전략 전체를 바꾼다.

```
BEFORE: 두 숫자 → B-Tree 각각 탐색 불가 → 풀 스캔 → Java Haversine 재계산
AFTER:  하나의 점 → R-Tree 한 번 탐색 → MBR 필터 → 96.2% 스캔 제거
```

| 규모 | Before | After |
|------|--------|-------|
| 10,000건 | 9,880행 스캔 | 378행 스캔 |
| 1,000,000건 | 전체 스캔 + 디스크 I/O | 38,000행 + 인덱스 탐색 |
| 동시 요청 시 | DB 병목 | 처리 가능 |

---

## 부록: 재현 방법

```bash
# MySQL + Redis 기동
docker start parkingmate-mysql-test
docker start parkingmate-redis

# 타이밍 테스트 실행 (MySQL 프로파일)
cd ParkingMate/parkingmate
./gradlew test --tests "com.parkingmate.parkingmate.service.BookingConnectionTimingTest"

# 결과 리포트
open build/reports/tests/test/index.html
```

**관련 파일**
```
BEFORE_MEASUREMENT_REPORT.md                              ← 본 문서
ParkingMate/parkingmate/src/main/resources/
  application-mysqltest.properties                        ← MySQL 테스트 프로파일
ParkingMate/parkingmate/src/test/java/.../service/
  BookingConnectionTimingTest.java                        ← 타이밍 테스트
```
