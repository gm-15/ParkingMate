# 🅿️ ParkingMate — P2P 주차 공간 예약 백엔드

> Spring Boot 3.5 / Java 21 기반의 P2P 주차 예약 백엔드.
> 기존 주차 공유 앱이 풀지 못한 **세 가지 백엔드 본질 문제** — 이중 예약 방지 · 예약-알림 원자성 · 지리공간 검색 효율 — 을 Kafka·EKS 없이 RDBMS layer 깊이로 해결한 개인 프로젝트.

🇬🇧 영문 버전: [README.en.md](README.en.md) · 📜 v1 시점 보존본: [README_v1_legacy.ko.md](README_v1_legacy.ko.md)

---

## 🚀 30초 요약 (면접관용)

| 항목 | 내용 |
|---|---|
| 한 줄 | P2P 주차 예약 백엔드, 백엔드 본질 3대 문제 RDBMS layer 해결 |
| 박건우 역할 | **단독 풀스택** (백엔드 · 프론트엔드 · 인프라 · DevOps) |
| 핵심 기술 | Spring Boot 3.5 · MySQL 8 R-Tree Spatial Index · Redis GEO · Resilience4j · Transactional Outbox |
| 검증된 자산 | Outbox 패턴 락 범위 축소 (Hikari 표준편차 5.1ms → 2.1ms) · MySQL Spatial Index (EXPLAIN 9,880 → 378) · 8/8 통합 테스트 PASS |
| 의도적 제외 | Kafka · EKS — 다른 포트폴리오에서 검증됨. 이 프로젝트는 **RDBMS 깊이 들어가는 piece** |
| 리팩토링 | DDD 47파일 패키지 재구성 (2026-03-24, `BUILD SUCCESSFUL` 유지) |

---

## 🏙 어떤 문제를 풀고 있나? (배경)

도심 주차난은 만성적 문제입니다. 해외 연구에 따르면 도시 교통량의 약 30%가 주차 공간을 찾는 차량입니다. 한국의 대표 서비스 "모두의 주차장"은 상업 주차장 디렉토리에 가까워, **방치된 개인 공간을 실시간 거래 가능한 자산으로 만드는 영역**은 여전히 빈 시장입니다.

ParkingMate는 그 빈 시장을 — 표면적 리디자인이 아니라 **세 가지 엔지니어링 gap을 직접 닫는 방식**으로 — 풀려고 합니다:

| 문제 | 기존 앱의 한계 | ParkingMate의 해결 |
|---|---|---|
| **이중 예약 방지** | 두 사용자가 동시에 같은 시간대를 예약 시 락이 없으면 둘 다 성공 | **비관적 락(`SELECT FOR UPDATE`)** + 트랜잭션 외부 Redis 분산 락 |
| **예약-알림 원자성** | 예약은 성공했는데 알림이 사라지는 Dual-Write 문제 (사용자는 게이트에서 발견) | **Transactional Outbox 패턴** — `booking` + `outbox_event` 같은 트랜잭션 커밋, `@Scheduled` publisher가 at-least-once로 발송 |
| **지리공간 검색 효율** | "주변 주차 찾기"가 풀 테이블 스캔 + Java Haversine 거리 계산 → 카탈로그 수천 개 넘으면 급격히 악화 | **MySQL Spatial Index** (POINT SRID 4326 + R-Tree) + **Redis GEO** write-through 캐시 |
| **Redis 단일 장애** | 분산 락 실패 시 예약 전체 차단 | **Resilience4j Circuit Breaker** → DB-only 비관적 락 자동 폴백 |

전체 의사결정 흐름: [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md)

---

## 📏 검증된 측정 결과

모든 측정은 `BookingConnectionTimingTest`와 `ParkingMateIntegrationTest`로 실 MySQL 8.0 (Docker `localhost:3307`) + Redis 7 환경에서 수행. 재현 방법은 [§ 로컬 개발](#-로컬-개발) 참조.

### Hikari 커넥션 점유 시간 (10회 반복 평균)

| Case | 설명 | TX 내 DB 쿼리 | 평균 | 범위 | 분산 | 알림 실패 시 예약? |
|---|---|---|---|---|---|---|
| Case 1 | **Baseline** (알림 없음) | 4 | 33 ms | 28–41 ms | 1.5× | 보존 |
| Case 2 | **문제 재현** — 알림이 같은 TX 내부 | 6 | 37 ms | 32–51 ms | 1.6× | ❌ 롤백 |
| **Case 3 (After)** | **Outbox** — 알림 별도 publisher | **5** | **36 ms** | **33–40 ms** | **1.2×** | ✅ **보존** |

> **헤드라인 수치는 분산, 평균이 아닙니다.** 로컬에서 절대 시간은 37→36ms로 거의 안 움직였지만 **min-max 비율이 1.6×에서 1.2×로 collapse**됐습니다 — 알림 서브시스템 부하가 더 이상 예약 latency에 새지 않습니다. RDS-style 10ms/쿼리 네트워크로 추정하면 같은 변경이 **60ms → 50ms (–17%)** 의 커넥션 점유 시간 감소, 100 동시 요청을 10-커넥션 Hikari 풀로 서빙할 때 **+25% throughput** 효과.

### EXPLAIN — 지리공간 검색 (10,000행 데이터셋)

| | Before (`lat/lng` BETWEEN) | **After (R-Tree `ST_Within`)** |
|---|---|---|
| `EXPLAIN.type` | `ALL` (풀 테이블 스캔) | **`range` (인덱스 범위 스캔)** |
| `EXPLAIN.key` | `NULL` | **`idx_location`** |
| Rows scanned | 9,880 | **378** |
| `filtered` | 1.23% | **100%** |
| 실행 시간 | 3.30 ms | 2.42 ms |

> **96.2% 스캔 행 감소.** 10K 행에서는 시간 차이가 작지만(InnoDB 버퍼풀에 다 들어감), 1M 행이라면 같은 구조 변경이 **38,000 행 스캔 vs 1M 행 + 디스크 I/O** — 디스크 바운드 구간에서 orders of magnitude 차이.

### 통합 테스트 (`ParkingMateIntegrationTest`, 8/8 PASS)

| # | Case | 검증 내용 |
|---|---|---|
| 0 | Setup | 사용자 2명, ParkingSpace 2개 (서울 강남 + 부산 해운대) |
| 1 | Outbox-1 | 예약 생성 시 `outbox_event` 1행이 같은 T1 안에 `PENDING`으로 기록 |
| 2 | Outbox-2 | publisher 실행 후 `notification` 행 생성 + outbox 행 `PROCESSED`로 전이 |
| 3 | Outbox-3 | 손상된 payload가 dispatch에서 throw → outbox `FAILED`, **예약은 보존** |
| 4 | Spatial-1 | 강남역 5km 반경: 강남 space 포함, 부산 space 제외 |
| 5 | Spatial-2 | 1km 반경: 부산 여전히 제외 |
| 6 | GEO-1 | Redis `GEOADD` write-through · `GEOSEARCH`가 정확한 space만 반환 |
| 7 | Concurrency | CountDownLatch 2-스레드 경합 → 정확히 1개 `SELECT FOR UPDATE` 성공 |

---

## 🏛️ 시스템 아키텍처

```
                              ┌──────────────────────────────┐
                              │  React 19 + Vite (frontend)  │
                              └──────────────┬───────────────┘
                                             │ JWT-bearing REST
                                             ▼
        ┌────────────────────────────────────────────────────────────┐
        │   Spring Boot 3.5.3 (Java 21)                              │
        │   DDD-organized packages:                                  │
        │     reservation/  space/  notification/  user/  common/    │
        │                                                            │
        │   ┌─────────── BookingService.createBooking() ──────────┐  │
        │   │  @CircuitBreaker(name="redis-lock",                 │  │
        │   │     fallbackMethod="createBookingWithoutLock")      │  │
        │   │  @Bulkhead(name="booking", max-concurrent=20)       │  │
        │   │                                                     │  │
        │   │  T1 (REQUIRED, REPEATABLE_READ)                     │  │
        │   │   ① SELECT user                                     │  │
        │   │   ② SELECT parking_space                            │  │
        │   │   ③ SELECT FOR UPDATE booking (overlap check)       │  │
        │   │   ④ INSERT booking                                  │  │
        │   │   ⑤ INSERT outbox_event   ← ④와 원자적 커밋          │  │
        │   │   COMMIT — 커넥션 즉시 해제                          │  │
        │   │                                                     │  │
        │   │  T2 (REQUIRES_NEW per event, 5초 주기)               │  │
        │   │   OutboxPollingPublisher                            │  │
        │   │     SELECT outbox WHERE status='PENDING'            │  │
        │   │     → notificationService.create()                  │  │
        │   │     → status = PROCESSED (또는 FAILED)               │  │
        │   └─────────────────────────────────────────────────────┘  │
        └──────────────┬──────────────────────────────────┬──────────┘
                       │                                  │
                       ▼                                  ▼
        ┌────────────────────────────────┐   ┌──────────────────────────┐
        │  MySQL 8.0 (Source of Truth)   │   │  Redis 7                 │
        │  + POINT/SRID 4326             │   │  + GEO write-through 캐시 │
        │  + R-Tree Spatial Index        │   │  + 분산 락                │
        └────────────────────────────────┘   └──────────────────────────┘
```

---

## 🔧 의도적으로 안 한 것

- ❌ **Kafka** — 다른 포트폴리오(Clmakase)에서 3-Broker StatefulSet로 검증됨. 여기서 다시 도입하면 over-engineering
- ❌ **EKS / Kubernetes** — 위와 동일 이유
- ❌ **마이크로서비스 분리** — 단일 모듈 안에서 DDD 패키지 격리로 충분
- ❌ **운영 메트릭(TPS · P99)** — 측정 스크립트 없음. 위 Hikari·EXPLAIN 측정만이 검증 자산

이 프로젝트는 **RDBMS layer 깊이 들어가는 piece**입니다. 다른 인프라 깊이는 다른 프로젝트에서 보여드립니다.

---

## 🛠 기술 스택

| 영역 | 기술 |
|---|---|
| 백엔드 | Java 21, Spring Boot 3.5.3, Spring Data JPA |
| DB · 캐시 | MySQL 8.0 (POINT SRID 4326 + R-Tree), Redis 7 (GEO + 분산락) |
| 신뢰성 | Transactional Outbox, Pessimistic Lock, Resilience4j (CircuitBreaker + Bulkhead) |
| 테스트 | JUnit 5, Testcontainers, CountDownLatch |
| 프론트엔드 | React 19, Vite 7, TypeScript |
| 인프라 (Terraform) | VPC · ALB · ECR · S3 (v2 ECS 배포 예정) |
| CI/CD | GitHub Actions (backend · frontend test → build → ECR push) |

---

## 👤 박건우 역할 (단독)

| 영역 | 담당 사항 |
|---|---|
| 백엔드 (Java 21 / Spring Boot 3.5.3) | 도메인 모델링 · 리포지토리 설계 · 트랜잭션 경계 |
| 동시성 설계 | Pessimistic Lock + Outbox + Resilience4j 합성 |
| DB 엔지니어링 | MySQL Spatial Index 설계 · EXPLAIN 기반 최적화 · JPA / JTS 통합 |
| 캐싱 | Redis GEO write-through, 분산 락 primitives |
| 프론트엔드 (React 19 / Vite 7) | 주차 흐름의 최소 CRUD UI |
| 인프라 (Terraform) | VPC / ALB / ECR / S3 모듈 |
| CI/CD (GitHub Actions) | backend · frontend test → build → ECR push (v2에서 deploy stage 추가 예정) |
| 문서화 | ADR · before/after 측정 리포트 · 통합 테스트 리포트 |

---

## 🗺 로드맵

### v2 (다음 단계)

- [ ] **CI/CD deploy stage** — ECS Fargate 자동 배포 (인프라 Terraform은 v1에서 작성 완료)
- [ ] **사진 증거 시스템** — 출차 시 차량 위치 사진 업로드 (S3 + presigned URL)
- [ ] **운영 메트릭** — Prometheus + Grafana (TPS · P99 · 락 점유 시간)
- [ ] **알림 채널 확장** — 현재 in-app만, SMS · 푸시 추가

### 장기

- [ ] **머신러닝 추천** — 사용자 예약 패턴 기반 주차장 추천
- [ ] **다중 region** — Redis GEO 분산 정합성

---

## 📚 저장소 안 참고 문서

- [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md) — 전략 문서 (의사결정 흐름)
- [MD/INTEGRATION_TEST_REPORT.md](MD/INTEGRATION_TEST_REPORT.md) — 8/8 통합 테스트 풀 리포트
- [README.en.md](README.en.md) — 영문 버전
- [README_v1_legacy.ko.md](README_v1_legacy.ko.md) — v1 시점 보존본

---

## 🔗 연락처

**박건우 ｜ Backend Engineer**

- 이메일: Gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
