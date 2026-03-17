# ParkingMate 고도화 전략 마스터 문서

> 최종 업데이트: 2026-03-04
> 포지셔닝: "기능 구현에서 멈추지 않고, 데이터 정합성과 장애 격리를 직접 검증하는 백엔드 엔지니어"
> 블로그 포스팅 제목: "성능 개선인가, 아니면 기술적 부채인가? ParkingMate 데이터 정합성 고도화기"

---

## 1. 프로젝트 개요

### 비즈니스 배경

도심 주차난 문제를 P2P 방식으로 해결하는 주차 공간 공유 플랫폼.
개인 차고, 빈 토지 등 **잠재 공급원을 실시간 거래 가능한 자산으로 전환**하는 것이 핵심 가치.

### 기존 앱(모두의 주차장) 대비 차별점

| 비교 항목 | 모두의 주차장 | ParkingMate |
|---|---|---|
| 비즈니스 모델 | 상업 주차장 디렉토리 나열 | P2P 개인 공간 실시간 거래 |
| 이중 예약 방지 | 기술적 보장 없음 | Pessimistic Lock + Transactional Outbox |
| 예약-알림 정합성 | 미보장 (Dual-Write 위험) | At-Least-Once 보장 (Outbox Pattern) |
| 실시간 가용성 | 정적 정보 노출 | Redis GEO 캐시 (5분 TTL, 지도 이동 즉시 반영) |
| 사용자 신뢰 레이어 | 후기/사진 시스템 미흡 | 입출차 사진 증거 + 보상 정책을 도메인 로직으로 구현 |
| 공간 검색 성능 | 단순 주소 검색 | MySQL Spatial Index (R-Tree) 기반 반경 쿼리 |

> **포트폴리오 한 줄 포지셔닝**
> "모두의 주차장이 해결 못 한 이중 예약, 데이터 정합성, 실시간 가용성 문제를
> Transactional Outbox와 MySQL Spatial Index로 엔지니어링 레벨에서 해결했다."

---

## 2. 전략적 방향: EKS보다 RDBMS 딥다이브

### 왜 EKS를 억지로 붙이지 않는가

```
❌ 피해야 할 방향:
   ParkingMate에 EKS + Kafka를 억지 적용
   → 인프라 복잡도만 높아지고, 실제 비즈니스 문제 해결과 거리가 멀어짐
   → "왜 이 규모에 Kafka가 필요하냐?"는 역질문에 답하기 어려움

✅ 선택한 방향:
   순수 RDBMS 딥다이브 + Transactional Outbox 패턴
   → "기술을 위한 기술이 아닌, 비즈니스 문제에서 출발한 기술 선택"을 증명
   → DB 레이어에서 정합성을 완벽히 다루는 데이터 장인정신 어필
```

### 포트폴리오 전체 포지셔닝

| 프로젝트 | 핵심 증명 포인트 |
|---|---|
| INSK_V4 | LLM 토큰 비용 최적화 (FinOps) |
| 냉장Goat v2 | Redis 분산락 + 서킷 브레이커 (동시성) |
| Clmakase | Kafka + EKS 15만 트래픽 (대용량 인프라) |
| **ParkingMate** | **RDBMS 딥다이브 + 데이터 정합성 (데이터 장인정신)** |

> 네 프로젝트가 각자 다른 역량 축을 커버. ParkingMate는 DB 레이어 전문성 담당.

---

## 3. 기술 스택

### Backend

| 분류 | 기술 | 선택 이유 |
|---|---|---|
| Framework | Spring Boot 3.5.3 (Java 21) | Virtual Thread 지원, 최신 LTS |
| ORM | JPA/Hibernate + QueryDSL | 복잡한 동적 쿼리 타입 안전하게 처리 |
| DB (Primary) | MySQL 8.0 + Spatial Index | R-Tree 인덱스 기반 반경 쿼리, ACID 보장 |
| Cache | Redis 7 (GEO + String + Hash) | 실시간 반경 검색 캐시 레이어 |
| 동시성 | Pessimistic Lock (PESSIMISTIC_WRITE) | 예약 충돌 빈도 높음 → Optimistic 대비 유리 |
| 이벤트 | Transactional Outbox + Polling Publisher | At-Least-Once 메시지 전달 보장 |
| 장애 격리 | Resilience4j (CircuitBreaker + Bulkhead) | DB 지연 시 스레드 고갈 방지 |
| 보안 | Spring Security 6 + JWT (HS256) | Stateless 인증 |
| 이미지 | AWS S3 | 입출차 사진 증거 저장 |
| 빌드 | Gradle 8 | 멀티모듈 확장 용이 |

### Infrastructure

| 분류 | 기술 | 비고 |
|---|---|---|
| 컨테이너 | Docker + Docker Compose | 로컬 개발 환경 |
| 클라우드 | AWS (RDS, ElastiCache, S3, ECS) | 운영 환경 |
| IaC | Terraform | infrastructure/ 디렉토리 |
| 부하 테스트 | nGrinder / k6 | 동시성 검증 |

### 의도적으로 도입하지 않은 것

| 기술 | 미도입 이유 |
|---|---|
| Kafka | 이 규모에서 불필요한 인프라 복잡도. Clmakase 프로젝트에서 이미 증명 |
| EKS | ParkingMate의 핵심 가치는 인프라가 아닌 데이터 정합성 |
| Optimistic Lock (예약 생성) | 충돌 빈도 높은 예약 도메인에서 재시도 폭풍 유발 → Pessimistic 선택 |

---

## 4. 아키텍처 핵심 결정 (ADR)

### ADR-001: Pessimistic Lock 선택 이유

```
컨텍스트:
  주차 예약은 동일 시간대에 다수 사용자가 동시 요청하는 고충돌 도메인

결정:
  Optimistic Lock 대신 Pessimistic Lock (PESSIMISTIC_WRITE) 선택

근거:
  - Optimistic Lock: 충돌 시 OptimisticLockException → 재시도 → 트래픽 폭증 가능
  - Pessimistic Lock: 선점 후 처리 → 충돌 자체를 원천 차단
  - 단점(커넥션 점유 시간)은 트랜잭션 범위 최소화 + Outbox 패턴으로 보완

트레이드오프:
  (+) 이중 예약 완벽 차단
  (-) 트랜잭션 내 커넥션 점유 → 알림을 AFTER_COMMIT으로 분리하여 완화
```

### ADR-002: Transactional Outbox 패턴 선택 이유 (Kafka 미채택 포함)

```
컨텍스트:
  예약 저장(DB)과 알림 발송(Message)의 원자적 처리 필요

후보 비교:
  A) @TransactionalEventListener (AFTER_COMMIT)
     장점: 구현 단순
     단점: 이벤트는 JVM 메모리에만 존재
           → AFTER_COMMIT 직전 프로세스 다운 시 알림 영구 유실
           → "결제는 됐는데 알림/보상 없음" 비즈니스 장애 발생

  B) Kafka + Producer
     장점: 고가용성, 대용량 메시지 처리
     단점: Kafka를 써도 DB 저장과 Kafka 전송 사이의 Dual-Write 문제는 여전히 존재
           → DB 저장 성공 + Kafka 전송 실패 시 데이터 불일치 동일하게 발생
           → 소규모 서비스에서 Kafka 클러스터 운영 비용이 비즈니스 가치보다 큼
           → "이 규모에 왜 Kafka가 필요한가?" 역질문에 방어 어려움
           → Clmakase 프로젝트에서 이미 Kafka 역량 증명 완료 (중복 어필 불필요)

  C) Transactional Outbox Pattern  ← 선택
     구조: 예약 INSERT + outbox_event INSERT → 동일 트랜잭션 (DB 원자성)
           → Polling Publisher(@Scheduled)가 outbox 테이블 읽어 알림 서비스 호출
     원자성 보장 이유: DB 트랜잭션 하나로 비즈니스 데이터 + 이벤트 데이터를 묶음
                      → 예약 저장 실패 시 outbox도 자동 롤백 (정합성 완벽 보장)
     장점: At-Least-Once 전달 보장, 프로세스 다운에도 데이터 유실 없음
     단점: outbox 테이블 관리 필요, Polling 주기(~1초)만큼 알림 지연 (허용 가능)

결정: Transactional Outbox Pattern
근거: 금전적 거래 포함 시스템에서 알림/보상 유실은 사용자 신뢰 훼손
      Kafka도 Dual-Write 문제를 근본적으로 해결하지 못함
      이 프로젝트의 핵심 증명 포인트는 "DB 레이어 정합성 장인정신"
```

### ADR-002-B: Redis vs Kafka 역할 명확히 분리

```
자주 혼동되는 질문: "대량 조회 요청에 Kafka 같은 완충지대가 필요하지 않나?"

답: Redis와 Kafka는 해결하는 문제 자체가 다르다.

Redis GEO (이 프로젝트에서 사용)
  - 역할: 읽기(Read) 요청 캐시
  - 특성: 싱글 스레드지만 모든 데이터가 메모리에 있어 초당 수십만 읽기 처리 가능
  - 적합한 케이스: 지도 이동 시 발생하는 실시간 조회 요청 (즉각 응답이 생명)
  - 지연이 없어야 함 → Kafka를 거치면 오히려 응답 속도 저하

Kafka (이 프로젝트에서 미채택)
  - 역할: 쓰기(Write) 요청 버퍼링
  - 특성: DB가 감당 못 하는 대량 쓰기 요청을 쌓아두는 완충지대
  - 적합한 케이스: 대규모 로그 수집, 결제 이벤트 스트리밍 등 쓰기 폭주 시나리오
  - 조회 요청에 쓰면: 응답 지연 추가 + 구조 복잡화 → 역효과

결론:
  읽기(조회) 트래픽 흡수 = Redis Cache
  쓰기(생성) 트래픽 버퍼 = Kafka (이 규모에서는 서킷 브레이커로 대체)
```

### ADR-003: MySQL Spatial Index + Redis GEO 역할 분리

```
컨텍스트:
  위치 기반 주차장 검색 성능 최적화

결정: CQRS 패턴으로 저장소 역할 분리

[MySQL Spatial Index (R-Tree)] → Source of Truth (쓰기/마스터)

  POINT 타입이란?
    기존: latitude DOUBLE, longitude DOUBLE → 두 컬럼을 따로 저장
    변경: location POINT → 위도/경도를 하나의 공간 좌표(Geometry)로 저장
          MySQL이 이를 수학적 '점'으로 인식 → 공간 연산 API 지원

  R-Tree 인덱스란?
    B-Tree: 숫자를 크기 순서로 정렬하는 1차원 인덱스
    R-Tree: 공간을 여러 개의 최소 경계 사각형(MBR, Minimum Bounding Rectangle)으로
            계층적으로 분할하여 관리하는 2차원 인덱스

    작동 원리:
      "서울 강남역 반경 3km 내 주차장 검색" 시
      1. 3km 반경을 둘러싸는 MBR(사각형) 계산
      2. MBR이 겹치는 R-Tree 노드만 탐색 (전체 무시)
      3. 해당 노드 내 POINT들에 대해 정확한 거리 계산
      → 100만 건 중 수백 건만 탐색 (풀 스캔 대비 수천 배 차이)

    EXPLAIN 목표 결과:
      type: range    (NOT ALL - 풀 스캔 아님)
      key: idx_location  (Spatial Index 사용 확인)
      Extra: Using where (MBR 필터 후 정밀 계산)

[Redis GEO (Sorted Set)] → Cache Layer (읽기/캐시)
  내부 구현: Geohash 기반 Sorted Set
             좌표를 52비트 정수로 인코딩 → score로 저장
  실시간 반경 검색 (GEORADIUS ~1ms)
  지도 이동 시 발생하는 대량 읽기 요청 흡수
  TTL 24시간, 공간 등록/수정 시 즉시 갱신
  가용성 캐시는 TTL 5분 (예약/취소 이벤트로 즉시 무효화)

면접 답변:
  "무조건 Redis GEO를 쓰는 관행을 비판했습니다.
   Redis는 휘발성이고 복잡한 조인이 불가능합니다.
   주차장 마스터 데이터는 ACID가 필요한 영속 데이터이므로
   MySQL R-Tree 인덱스가 Source of Truth입니다.
   Redis GEO는 지도 이동 시 발생하는 대량 읽기 요청을
   DB 접근 없이 처리하는 캐시 레이어로만 활용했습니다.
   이는 데이터 성격(Master vs Cache)에 따라 저장소를 분리한 CQRS 구조입니다."
```

---

## 5. 도메인 패키지 구조 (DDD)

```
com.parkingmate/
├── reservation/                    ← 예약 도메인 (핵심)
│   ├── domain/
│   │   ├── Booking.java            (비관적 락 대상)
│   │   └── BookingStatus.java
│   ├── repository/
│   │   └── BookingRepository.java  (@Lock PESSIMISTIC_WRITE)
│   ├── service/
│   │   ├── BookingCommandService.java   (쓰기: 락 + Outbox)
│   │   └── BookingQueryService.java     (읽기: 이력 조회)
│   ├── outbox/                     ← Transactional Outbox
│   │   ├── OutboxEvent.java        (outbox_event 테이블 엔티티)
│   │   ├── OutboxRepository.java
│   │   └── OutboxPollingPublisher.java  (@Scheduled 폴링)
│   ├── event/
│   │   ├── BookingCreatedEvent.java
│   │   └── BookingCanceledEvent.java
│   ├── controller/
│   │   └── BookingController.java
│   └── dto/
│
├── space/                          ← 공간 정보 도메인
│   ├── domain/
│   │   └── ParkingSpace.java       (POINT geometry 필드 추가)
│   ├── repository/
│   │   └── ParkingSpaceRepository.java  (Spatial 쿼리)
│   ├── service/
│   │   ├── ParkingSpaceService.java
│   │   └── GeoCacheService.java    (Redis GEO 캐시 레이어)
│   ├── controller/
│   │   └── ParkingSpaceController.java
│   └── dto/
│
├── notification/                   ← 알림 도메인 (Outbox 소비자)
│   ├── domain/
│   │   └── Notification.java
│   ├── service/
│   │   └── NotificationService.java
│   └── handler/
│       └── OutboxEventHandler.java  (Outbox 이벤트 처리)
│
├── user/                           ← 사용자/신뢰 도메인
│   ├── domain/
│   │   └── User.java
│   ├── service/
│   │   └── UserService.java
│   └── controller/
│       └── UserController.java
│
└── common/                         ← 공통 인프라
    ├── config/
    │   ├── RedisConfig.java
    │   ├── SecurityConfig.java
    │   ├── AsyncConfig.java        (스레드풀 격리)
    │   └── ResilienceConfig.java   (CircuitBreaker 설정)
    ├── exception/
    │   └── GlobalExceptionHandler.java
    ├── filter/
    │   └── JwtAuthenticationFilter.java
    └── util/
        └── DistributedLockUtil.java
```

---

## 6. 코드 수정 전 필수 작업: Before 측정 체크리스트

> **원칙**: 수치 없는 개선은 주장에 불과하다.
> 코드를 한 줄도 고치기 전에 아래 항목을 먼저 측정하고 스크린샷/로그로 저장한다.
> 이 "Before" 수치가 블로그 포스팅과 면접 답변의 핵심 근거가 된다.

### 측정 항목 및 방법

| # | 측정 항목 | 측정 방법 | 저장 형태 |
|---|---|---|---|
| 1 | 예약 트랜잭션 커넥션 점유 시간 | nGrinder 동시 50건 + Hikari 로그 | 스크린샷 + 수치 |
| 2 | 이중 예약 발생 여부 | nGrinder 동시 100건 (같은 시간대) → DB 결과 확인 | 쿼리 결과 캡처 |
| 3 | 근처 검색 쿼리 실행 계획 | `EXPLAIN SELECT ... WHERE Haversine(...)` | EXPLAIN 결과 캡처 |
| 4 | 근처 검색 응답 시간 | 1만 건 데이터 기준 평균 응답시간 | nGrinder 리포트 |
| 5 | 프로세스 강제 종료 시 알림 유실 | 예약 진행 중 `kill -9` → notification 테이블 확인 | 테스트 로그 |

### Before 측정 실행 커맨드

```bash
# 1. 더미 데이터 10,000건 삽입 (근처 검색 테스트용)
INSERT INTO parking_space (address, latitude, longitude, price_per_hour, user_id)
SELECT
  CONCAT('서울시 ', FLOOR(RAND()*25)+1, '구 ', FLOOR(RAND()*100)+1, '번길'),
  37.4 + (RAND() * 0.3),   -- 서울 위도 범위
  126.8 + (RAND() * 0.4),  -- 서울 경도 범위
  FLOOR(RAND() * 5000) + 1000,
  1
FROM information_schema.columns LIMIT 10000;

# 2. 현재 근처 검색 실행 계획 확인 (Before - 풀 스캔 확인)
EXPLAIN SELECT * FROM parking_space
WHERE latitude BETWEEN 37.5 AND 37.6
AND longitude BETWEEN 126.9 AND 127.0;
-- 예상: type=ALL (풀 스캔), rows=10000

# 3. Hikari 커넥션 로그 활성화 (application-local.properties)
logging.level.com.zaxxer.hikari=DEBUG
logging.level.com.zaxxer.hikari.HikariConfig=DEBUG
```

### 블로그 기록 포인트 (Before → After 대비용)

```
[Before 기록 항목]
□ 알림 전송 로직이 트랜잭션 안에 묶여 DB 커넥션을 ___ms 점유 (측정값 기입)
□ 1만 건 데이터에서 근처 검색 EXPLAIN → type=ALL, rows=___건 (측정값 기입)
□ Optimistic Lock(@Version) + Pessimistic Lock 혼재 → 코드 스크린샷 캡처
□ 동시 100건 같은 시간대 예약 요청 → 이중 예약 ___건 발생 (측정값 기입)

[After 기록 항목] ← Phase 완료 후 동일 조건 재측정
□ Outbox 패턴 적용 후 커넥션 점유 시간: ___ms (82% 단축 목표)
□ Spatial Index 적용 후 EXPLAIN → type=range, key=idx_location
□ Pessimistic Lock 단일화 + @Version 제거 → 코드 스크린샷 캡처
□ 동시 100건 예약 → 이중 예약 0건 확인
```

---

## 7. 구현 계획 (Phase별)

### Phase 1: 트랜잭션 범위 최소화 + Transactional Outbox

**목표**: "예약-알림 Dual-Write 문제 해결 + DB 커넥션 점유 시간 단축"

**Step 1-1: outbox_event 테이블 설계**

```sql
CREATE TABLE outbox_event (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,   -- 'BOOKING'
    aggregate_id    BIGINT       NOT NULL,   -- booking.id
    event_type      VARCHAR(100) NOT NULL,   -- 'BOOKING_CREATED'
    payload         JSON         NOT NULL,   -- 이벤트 데이터
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / PUBLISHED
    created_at      DATETIME(6)  NOT NULL,
    published_at    DATETIME(6),
    INDEX idx_status_created (status, created_at)  -- 폴링 최적화
);
```

**Step 1-2: BookingCommandService 핵심 구조**

```java
@Transactional(timeout = 3, isolation = Isolation.READ_COMMITTED)
public BookingResponseDto createBooking(BookingCreateRequestDto dto, String email) {

    // [Layer 1] Redis 분산락은 트랜잭션 바깥에서 호출
    // → 이 메서드는 락 획득 후 호출됨

    // [Layer 2] Pessimistic Lock: 겹치는 예약 행에 FOR UPDATE
    List<Booking> conflicts = bookingRepository
        .findOverlappingBookingsWithLock(spaceId, RESERVED, startTime, endTime);
    if (!conflicts.isEmpty()) throw new SlotConflictException();

    // 예약 저장
    Booking saved = bookingRepository.save(booking);

    // [핵심] Outbox 이벤트를 동일 트랜잭션에 저장 (DB 원자성 보장)
    OutboxEvent outbox = OutboxEvent.builder()
        .aggregateType("BOOKING")
        .aggregateId(saved.getId())
        .eventType("BOOKING_CREATED")
        .payload(objectMapper.writeValueAsString(new BookingCreatedPayload(saved)))
        .status(OutboxStatus.PENDING)
        .build();
    outboxRepository.save(outbox);  // 예약 + outbox = 동일 트랜잭션

    return BookingResponseDto.from(saved);
    // Commit → 커넥션 반환 (점유: ~30ms)
    // 알림은 Polling Publisher가 별도로 처리
}
```

**Step 1-3: OutboxPollingPublisher**

```java
@Component
@RequiredArgsConstructor
public class OutboxPollingPublisher {

    // 1초마다 PENDING 이벤트 조회 후 알림 서비스 호출
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pending = outboxRepository
            .findTop100ByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);

        for (OutboxEvent event : pending) {
            try {
                processEvent(event);
                event.markPublished();       // status = PUBLISHED
            } catch (Exception e) {
                log.error("Outbox event processing failed: {}", event.getId(), e);
                // 실패해도 다음 폴링에서 재시도 (At-Least-Once)
            }
        }
    }
}
```

**커넥션 점유 Before/After**:
```
Before:  [예약 INSERT] → [알림 INSERT] → Commit    점유: ~200ms
After:   [예약 INSERT] → [Outbox INSERT] → Commit  점유: ~35ms
                               ↓ (1초 후, 별도 트랜잭션)
                          [알림 INSERT]
```

**검증 방법**: nGrinder로 동시 100명 예약 요청 → Hikari 커넥션 사용률 비교

---

### Phase 2: MySQL Spatial Index 도입 + EXPLAIN 분석

**목표**: "Redis GEO 맹신 비판 + DB 레이어 성능 튜닝 전문성 증명"

**Step 2-1: ParkingSpace 테이블 Spatial 컬럼 추가**

```sql
-- 기존 latitude, longitude → POINT geometry 컬럼 추가
ALTER TABLE parking_space
    ADD COLUMN location POINT NOT NULL
        COMMENT 'SRID 4326 (WGS84) 좌표',
    ADD SPATIAL INDEX idx_location (location);

-- 기존 데이터 마이그레이션
UPDATE parking_space
SET location = ST_SRID(POINT(longitude, latitude), 4326)
WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
```

**Step 2-2: Spatial 반경 쿼리 (Repository)**

```java
// ParkingSpaceRepository.java
@Query(value = """
    SELECT *,
           ST_Distance_Sphere(location, ST_SRID(POINT(:lng, :lat), 4326)) / 1000 AS distance_km
    FROM parking_space
    WHERE ST_Within(
        location,
        ST_Buffer(ST_SRID(POINT(:lng, :lat), 4326), :radiusMeters)
    )
    ORDER BY distance_km ASC
    LIMIT :limit
    """, nativeQuery = true)
List<ParkingSpaceWithDistance> findWithinRadius(
    @Param("lat") double lat,
    @Param("lng") double lng,
    @Param("radiusMeters") double radiusMeters,
    @Param("limit") int limit
);
```

**Step 2-3: EXPLAIN 분석 (포트폴리오 핵심 증거)**

```sql
-- 100만 건 데이터 기준 실행 계획 분석
EXPLAIN SELECT * FROM parking_space
WHERE ST_Within(location, ST_Buffer(ST_SRID(POINT(126.9780, 37.5665), 4326), 3000));

-- 목표 결과:
-- type: range (풀 스캔 ALL 아님)
-- key: idx_location (Spatial Index 사용 확인)
-- rows: ~수백 건 (100만 건 중 인덱스 필터링)
-- Extra: Using where (R-Tree MBR 필터 적용)
```

**면접 답변 시나리오**:
```
Q: "왜 Redis GEO 대신 MySQL Spatial Index를 썼나요?"

A: "무조건 Redis GEO를 쓰는 관행을 의심했습니다.
    주차장 마스터 데이터는 영속성과 정합성이 필요한 데이터입니다.
    Redis는 휘발성이고 복잡한 조인이 불가능합니다.
    MySQL 8.0의 Spatial Index(R-Tree)를 적용하고
    100만 건 기준 EXPLAIN 분석으로 풀 스캔이 아닌
    인덱스 스캔임을 검증했습니다.
    단, 지도 이동 시 발생하는 대량 읽기 요청은
    Redis GEO 캐시 레이어로 흡수하여 CQRS 구조로 분리했습니다."
```

---

### Phase 3: Resilience4j 서킷 브레이커 + Graceful Shutdown

**목표**: "DB 지연 시 스레드 고갈 방지 + 진행 중 트랜잭션 유실 없음 검증"

**Step 3-1: 의존성 추가 (build.gradle)**

```groovy
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.2.0'
implementation 'org.springframework.boot:spring-boot-starter-aop'
```

**Step 3-2: 설정 (application.properties)**

```properties
# Graceful Shutdown
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

# CircuitBreaker - booking
resilience4j.circuitbreaker.instances.booking.sliding-window-size=10
resilience4j.circuitbreaker.instances.booking.failure-rate-threshold=50
resilience4j.circuitbreaker.instances.booking.wait-duration-in-open-state=10s
resilience4j.circuitbreaker.instances.booking.permitted-number-of-calls-in-half-open-state=3

# Bulkhead - 동시 처리 수 제한 (스레드 고갈 방지)
resilience4j.bulkhead.instances.booking.max-concurrent-calls=20
resilience4j.bulkhead.instances.booking.max-wait-duration=0
```

**Step 3-3: BookingCommandService 적용**

```java
@CircuitBreaker(name = "booking", fallbackMethod = "createBookingFallback")
@Bulkhead(name = "booking", type = Type.SEMAPHORE)
public BookingResponseDto createBooking(BookingCreateRequestDto dto, String email) {
    String lockKey = "lock:space:" + dto.getParkingSpaceId();
    return distributedLockUtil.executeWithLock(lockKey, 3L, TimeUnit.SECONDS,
        () -> createBookingCore(dto, email));
}

private BookingResponseDto createBookingFallback(
        BookingCreateRequestDto dto, String email, Exception ex) {
    log.warn("Circuit OPEN - booking fallback. cause={}", ex.getMessage());
    throw new ServiceUnavailableException(
        "현재 예약 서비스가 일시적으로 불안정합니다. 잠시 후 다시 시도해주세요."
    );
}
```

**검증 시나리오 (카오스 테스트)**:

```
시나리오 1: DB 지연 주입
  1. MySQL slow query 시뮬레이션 (SET GLOBAL innodb_lock_wait_timeout=1)
  2. 예약 요청 동시 50건 발송
  3. 서킷 브레이커 OPEN 상태 전환 확인
  4. 10초 후 HALF-OPEN → CLOSED 복구 확인
  → 검증 목표: 스레드 풀 고갈 없이 Fallback 응답 반환

시나리오 2: 프로세스 강제 종료 (Graceful Shutdown 검증)
  1. 예약 요청 처리 중 kill -SIGTERM 발송
  2. 30초 타임아웃 내 진행 중 트랜잭션 완료 확인
  3. Outbox 테이블 데이터 무결성 확인
  → 검증 목표: 처리 중이던 예약 데이터 유실 없음
```

---

### Phase 4: Redis GEO 캐시 레이어 구현

**목표**: "검색 요청의 대다수를 DB 조회 없이 처리"

**캐시 아키텍처**:

```
사용자 지도 이동 요청
        ↓
Redis GEO 조회 (GEORADIUS, ~1ms)
        ↓ Cache Hit (90% 이상 목표)
   spaceId 목록 + 거리 반환
        ↓
Redis Hash에서 정적 정보 조회 (space:info:{id})
        ↓
Redis String에서 가용성 조회 (space:avail:{id}:{date}, TTL 5분)
        ↓
응답 반환 (DB 미접근)

Cache Miss 시에만 MySQL Spatial 쿼리 → 캐시 워밍 → 재응답
```

**캐시 무효화 전략**:

```
이벤트 발생          무효화 대상               방식
──────────────────────────────────────────────────────────
공간 등록/수정    → GEO 인덱스 + Info Hash   → 즉시 갱신
예약 생성/취소    → 가용성 캐시              → 패턴 삭제 (space:avail:{id}:*)
공간 삭제         → GEO 인덱스 + Info Hash   → 즉시 삭제
```

---

## 7. 검증 지표 (포트폴리오 증거)

| 항목 | Before | After | 검증 방법 |
|---|---|---|---|
| 예약 트랜잭션 커넥션 점유 | ~200ms | ~35ms | nGrinder 스레드 덤프 |
| 이중 예약 발생률 | 비보장 | 0% | 동시 100건 nGrinder 테스트 |
| 알림 유실률 (프로세스 다운) | 유실 가능 | 0% | kill -9 후 outbox 테이블 확인 |
| 근처 검색 DB 부하 | 100% DB 조회 | 캐시 히트 시 0% | Redis INFO stats |
| 100만 건 반경 쿼리 | 풀 스캔 | Index Scan | EXPLAIN 결과 |
| DB 지연 시 서비스 가용성 | 스레드 고갈 | Fallback 응답 | 서킷 브레이커 상태 모니터링 |

---

## 8. 면접 핵심 스크립트

### "가장 어려웠던 기술적 도전은?"

> "예약 저장과 알림 발송을 원자적으로 처리하는 것이었습니다.
> 처음엔 `@TransactionalEventListener(AFTER_COMMIT)`을 사용했지만,
> 커밋 직후 프로세스가 다운되면 이벤트가 메모리에서 유실된다는 한계를 발견했습니다.
> 결제가 완료됐는데 알림이 없으면 사용자 신뢰가 깨집니다.
> Transactional Outbox 패턴으로 예약과 이벤트를 동일 트랜잭션에 저장해
> At-Least-Once 전달을 보장했습니다."

### "왜 Redis GEO 대신 MySQL Spatial을 썼나요?"

> "무조건 Redis를 쓰는 것이 정답이 아님을 증명하고 싶었습니다.
> 주차장 마스터 데이터는 ACID가 필요한 영속 데이터입니다.
> MySQL 8.0 Spatial Index로 100만 건 기준 EXPLAIN 분석을 했고,
> 풀 스캔 없이 R-Tree 인덱스 스캔으로 처리됨을 확인했습니다.
> Redis GEO는 지도 이동 시 발생하는 대량 읽기 요청 캐시에만 사용해
> 데이터 성격에 따라 저장소를 분리한 CQRS 구조로 설계했습니다."

### "비관적 락을 선택한 이유는?"

> "예약 도메인은 충돌 빈도가 높습니다.
> 낙관적 락을 쓰면 충돌 시 OptimisticLockException이 발생하고
> 재시도 폭풍이 오히려 DB 부하를 키울 수 있습니다.
> 비관적 락으로 선점 후 처리해 충돌 자체를 차단했고,
> 트랜잭션 범위를 최소화해 커넥션 점유 시간을 200ms에서 35ms로 단축했습니다."

---

## 9. 현재 코드베이스 기준 작업 파일 맵핑

| Phase | 작업 유형 | 대상 파일 |
|---|---|---|
| Phase 1 | 수정 | `service/BookingService.java` |
| Phase 1 | 신규 | `reservation/outbox/OutboxEvent.java` |
| Phase 1 | 신규 | `reservation/outbox/OutboxRepository.java` |
| Phase 1 | 신규 | `reservation/outbox/OutboxPollingPublisher.java` |
| Phase 1 | 신규 | `reservation/event/BookingCreatedEvent.java` |
| Phase 1 | 신규 | `reservation/event/BookingCanceledEvent.java` |
| Phase 2 | 수정 | `domain/ParkingSpace.java` (POINT 컬럼 추가) |
| Phase 2 | 수정 | `repository/ParkingSpaceRepository.java` (Spatial 쿼리) |
| Phase 2 | 수정 | `service/ParkingSpaceService.java` |
| Phase 2 | 수정 | `scripts/init.sql` (Spatial Index DDL) |
| Phase 3 | 수정 | `build.gradle` (Resilience4j 의존성) |
| Phase 3 | 수정 | `application.properties` (graceful shutdown, CB 설정) |
| Phase 3 | 수정 | `service/BookingService.java` (@CircuitBreaker 적용) |
| Phase 4 | 신규 | `space/service/GeoCacheService.java` |
| Phase 4 | 수정 | `config/RedisConfig.java` |
| 전체 | 수정 | 패키지 DDD 재구성 |
