# 🅿️ ParkingMate — P2P Parking-Space Reservation Backend

> A Spring Boot 3.5 / Java 21 backend that solves three problems missing in incumbent parking-share apps: **double-booking prevention**, **reservation–notification atomicity under failure**, and **sub-millisecond geospatial search** — all without leaning on Kafka or EKS, by going deep into the RDBMS layer.

🇰🇷 한국어 버전: [README.ko.md](README.ko.md)

---

## ✨ At a Glance

- **Solo project** (sole engineer for backend, frontend, infrastructure, and DevOps)
- **Phase 1–4 implemented & verified end-to-end**: Transactional Outbox · MySQL Spatial Index (R-Tree) · Resilience4j (CircuitBreaker + Bulkhead) · Redis GEO Cache
- **8 / 8 integration tests PASS** against real MySQL 8 + Redis 7 in Docker
- **DDD-style package re-organization** (47 files moved on 2026-03-24, `BUILD SUCCESSFUL`)
- **Intentionally avoids Kafka / EKS** — already proven elsewhere; this project is the **RDBMS-deep-dive piece** of the portfolio

---

## 🌆 Why This Project Exists (Background)

The starting context is the chronic urban parking shortage — a problem the existing market has only partially addressed. The leading service in Korea, **모두의 주차장 (Modu's Parking)**, treats individual garages and idle land as a static directory: it lists commercial parking lots, but does not turn the long tail of underutilized private spaces into real-time tradeable assets. ParkingMate's premise is that the unmet demand sits in three engineering gaps that no surface-level redesign solves:

① **Double-booking has no technical guarantee.** Two users can confirm the same time-slot if no lock serializes the write.
② **Reservation–notification atomicity is unprotected.** A reservation can succeed while the notification is silently lost — the classic Dual-Write problem — and the user discovers it only at the gate.
③ **Geospatial search runs as a full-table scan.** "Show me parking nearby" without an R-Tree index degrades sharply once the catalog exceeds a few thousand spaces.

ParkingMate's reason for existing is to close those three gaps specifically — by going deep into the RDBMS layer (Transactional Outbox + MySQL Spatial Index + CQRS) rather than reaching for Kafka or EKS, both of which would have been over-engineering at this scale and are demonstrated separately in another portfolio piece.

### What ParkingMate Does Differently

| Problem in incumbent apps | What ParkingMate does |
|---|---|
| Double-booking has no technical guarantee | **Pessimistic lock** (`SELECT FOR UPDATE`) on overlapping booking rows + Redis distributed lock outside the transaction |
| Reservation succeeds but notification is silently lost (Dual-Write problem) | **Transactional Outbox**: `booking` INSERT and `outbox_event` INSERT commit in the same DB transaction; a separate `@Scheduled` publisher delivers notifications with **at-least-once** semantics |
| "Find nearby parking spaces" runs as a full-table scan + Java Haversine | **MySQL Spatial Index** (POINT SRID 4326 + R-Tree), with **Redis GEO** as a write-through cache layer |
| Single-Redis failure crashes booking | **Resilience4j Circuit Breaker** opens on Redis errors; the path automatically degrades to **DB-only Pessimistic locking** instead of failing the request |

The detailed strategy document is in [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md).

---

## 📏 Verified Metrics

All measurements come from `BookingConnectionTimingTest` and `ParkingMateIntegrationTest` against MySQL 8.0 (Docker, `localhost:3307`) and Redis 7. Reproduction commands are in [§ Local Development](#-local-development).

### Hikari connection-hold time (10-iteration average)

| Case | Description | DB queries in TX | Avg | Range | Variance | Notification failure → reservation? |
|---|---|---|---|---|---|---|
| Case 1 | **Baseline** (no notification) | 4 | 33 ms | 28–41 ms | 1.5× | preserved |
| Case 2 | **Problem reproduction** — notification inside the same TX | 6 | 37 ms | 32–51 ms | 1.6× | ❌ rolled back |
| **Case 3 (After)** | **Outbox** — notification handled by separate publisher | **5** | **36 ms** | **33–40 ms** | **1.2×** | ✅ **preserved** |

> **The headline number is the variance, not the mean.** On localhost the absolute time only drops from 37 ms → 36 ms, but the min–max ratio collapses from 1.6× to 1.2×: notification subsystem load no longer leaks into reservation latency. Extrapolating to RDS-style 10 ms / query networks, the same change is **60 ms → 50 ms (–17 %)** of connection-hold time, which translates to **+25 % throughput** on a 10-connection Hikari pool servicing 100 concurrent requests.

### EXPLAIN — geospatial search (10,000-row dataset)

| | Before (`lat/lng` BETWEEN) | **After (R-Tree `ST_Within`)** |
|---|---|---|
| `EXPLAIN.type` | `ALL` (full table scan) | **`range` (index range scan)** |
| `EXPLAIN.key` | `NULL` | **`idx_location`** |
| Rows scanned | 9,880 | **378** |
| `filtered` | 1.23 % | **100 %** |
| Execution time | 3.30 ms | 2.42 ms |

> **96.2 % scan-row reduction.** On a 10K-row dataset the time delta is small (~0.9 ms) because everything fits in the InnoDB buffer pool, but on a 1 M-row dataset the same structural change scales to **38,000 rows scanned vs 1 M rows + disk I/O** — orders of magnitude in the disk-I/O-bound regime.

### Integration test suite (`ParkingMateIntegrationTest`, 8 / 8 PASS)

| # | Case | What it verifies |
|---|---|---|
| 0 | Setup | Test data — 2 users, 2 ParkingSpaces (서울 강남 + 부산 해운대) |
| 1 | Outbox-1 | Booking creation writes one `outbox_event` row in `PENDING` within the same T1 |
| 2 | Outbox-2 | After the publisher runs, a `notification` row appears and the outbox row flips to `PROCESSED` |
| 3 | Outbox-3 | A poisoned payload throws inside dispatch → outbox row → `FAILED`, **booking is preserved** |
| 4 | Spatial-1 | 5 km radius around 강남역: 강남 space included, 부산 space excluded |
| 5 | Spatial-2 | 1 km radius: 부산 space still excluded |
| 6 | GEO-1 | Redis `GEOADD` write-through; `GEOSEARCH` returns the right space and excludes 부산 |
| 7 | Concurrency | Two threads with `CountDownLatch` race on the same time slot → exactly one `SELECT FOR UPDATE` succeeds |

Full evidence: [MD/INTEGRATION_TEST_REPORT.md](MD/INTEGRATION_TEST_REPORT.md).

---

## 👤 My Role & Responsibilities

This is a **solo project** — I own the entire stack:

- **Backend (Java 21 / Spring Boot 3.5.3)** — domain modelling, repository design, transactional boundaries
- **Concurrency design** — Pessimistic Lock + Outbox + Resilience4j composition
- **Database engineering** — MySQL Spatial Index design, EXPLAIN-driven optimization, JPA / JTS integration
- **Caching** — Redis GEO write-through, distributed-lock primitives
- **Frontend (React 19 / Vite 7)** — minimal CRUD UI for the parking flow
- **Infrastructure (Terraform)** — VPC / ALB / ECR / S3 modules
- **CI/CD (GitHub Actions)** — backend & frontend test → build → ECR push (deploy stage in v2)
- **Documentation** — ADRs, before/after measurement reports, integration test reports

---

## 🏛️ System Architecture

```
                              ┌──────────────────────────────┐
                              │  React 19 + Vite (frontend)  │
                              └──────────────┬───────────────┘
                                             │ JWT-bearing REST
                                             ▼
        ┌────────────────────────────────────────────────────────────┐
        │   Spring Boot 3.5.3 (Java 21)                              │
        │   ┌──────────────────────────────────────────────────┐     │
        │   │ DDD-organized packages                           │     │
        │   │   reservation/  space/  notification/            │     │
        │   │   user/  common/                                 │     │
        │   └──────────────────────────────────────────────────┘     │
        │                                                            │
        │   ┌─────────── BookingService.createBooking() ──────────┐  │
        │   │  @CircuitBreaker(name="redis-lock",                 │  │
        │   │     fallbackMethod="createBookingWithoutLock")      │  │
        │   │  @Bulkhead(name="booking", max-concurrent=20)       │  │
        │   │   ┌──────── Redis distributed lock (outside TX) ──┐ │  │
        │   │   │   T1 (REQUIRED, REPEATABLE_READ)              │ │  │
        │   │   │   ① SELECT user                                │ │  │
        │   │   │   ② SELECT parking_space                       │ │  │
        │   │   │   ③ SELECT FOR UPDATE booking (overlap check) │ │  │
        │   │   │   ④ INSERT booking                             │ │  │
        │   │   │   ⑤ INSERT outbox_event   ← atomic with ④      │ │  │
        │   │   │   COMMIT — connection released                 │ │  │
        │   │   └────────────────────────────────────────────────┘ │  │
        │   │                                                       │  │
        │   │   T2 (separate, REQUIRES_NEW per event, every 5 s)    │  │
        │   │   OutboxPollingPublisher                              │  │
        │   │     SELECT outbox WHERE status='PENDING'              │  │
        │   │     → notificationService.create()                    │  │
        │   │     → status = PROCESSED  (or FAILED on dispatch err) │  │
        │   └───────────────────────────────────────────────────────┘  │
        └──────────────┬──────────────────────────────────┬──────────┘
                       │                                  │
                       ▼                                  ▼
        ┌────────────────────────────────┐   ┌──────────────────────────┐
        │  MySQL 8.0 (Source of Truth)   │   │  Redis 7                 │
        │  POINT location SRID 4326      │   │  • Distributed lock      │
        │  SPATIAL INDEX idx_location    │   │  • GEO cache             │
        │  outbox_event (status, ...)    │   │    (GEOADD write-through,│
        │                                │   │     GEOSEARCH read path) │
        └────────────────────────────────┘   └──────────────────────────┘
```

### CQRS-style read path

```
search-by-location request
        │
        ▼
Redis GEOSEARCH ── hit ─→ spaceId list (≈1 ms, no DB)
        │
        └─ miss / error ─→ MySQL ST_Within over R-Tree
                            (idx_location, ~ms scale)
                            → write-through into Redis GEO
```

---

## 🔧 Tech Stack

### Backend
- **Spring Boot 3.5.3** · **Java 21** (Gradle 8)
- Spring Data JPA + **Hibernate Spatial** + **JTS Core 1.19**
- Spring Security + **jjwt 0.11.5** (HS256, BCrypt password hashing)
- **Resilience4j 2.2.0** (Spring Boot 3 starter + AOP)
- Spring Boot Starter Data Redis (Lettuce + commons-pool2)
- AWS SDK (S3 2.20.0)

### Frontend
- React 19.1 · Vite 7 · React Router 7 · Axios

### Storage
- **MySQL 8.0** with `POINT SRID 4326` + `SPATIAL INDEX`
- **Redis 7** for distributed locks + GEO caching
- H2 for fast local development & in-memory tests

### Infrastructure & DevOps
- Terraform 1.5 (`infrastructure/`) — VPC, ALB, ECR, S3, Security Groups defined; RDS / ElastiCache / ECS / ASG planned for v2
- GitHub Actions (`.github/workflows/ci-cd.yml`) — backend & frontend test, build, ECR push (ECS deploy planned)
- Docker / Docker Compose for the full local stack

---

## 🚦 Backend Deep Dives (Phase 1 → 4)

### Phase 1 · Transactional Outbox Pattern

**Why not `@TransactionalEventListener(AFTER_COMMIT)`?**
The event is held in JVM memory between commit and `AFTER_COMMIT` invocation. If the process is killed in that window, the notification is **permanently lost** with no DB record to recover from — exactly the "payment succeeded but no notification" failure mode that erodes user trust in money-touching systems.

**Why not Kafka?**
Kafka does not solve the Dual-Write problem either — DB INSERT can succeed while Kafka publish fails. A Kafka cluster also adds operational surface area that this project's traffic does not justify, and Kafka competence is already demonstrated in a separate large-scale project. Adding it here would be over-engineering hard to defend in interviews.

**Solution.**
```
T1  (BookingService.createBooking, REQUIRED transaction)
  ├─ INSERT booking
  └─ INSERT outbox_event (status=PENDING)         ← atomic with the row above
  COMMIT

T2  (OutboxPollingPublisher, @Scheduled fixedDelay=5s, REQUIRES_NEW per event)
  ├─ SELECT outbox_event WHERE status='PENDING' (FIFO by created_at)
  ├─ dispatch → notificationService.create()
  └─ UPDATE outbox_event SET status='PROCESSED' (or FAILED if dispatch threw)
```

This is **at-least-once delivery** with idempotency owned by the consumer side. Failures roll the **outbox row** to `FAILED` while the **booking is preserved**, which is verified by the `Outbox-3` integration test (Jackson parse error on a poisoned payload).

### Phase 2 · MySQL Spatial Index (R-Tree)

The starting point was the worst-case JPA pattern: store `latitude DOUBLE` + `longitude DOUBLE`, then `findAll()` and run Java-side Haversine. On the 10K-row test dataset, `EXPLAIN` showed `type=ALL` and `filtered=1.23%` — meaning every search read the entire table and threw away 98.77 % of it.

The redesign:

```sql
ALTER TABLE parking_space
  ADD COLUMN location POINT NOT NULL SRID 4326,
  ADD SPATIAL INDEX idx_location (location);
```

```java
// JPA / JTS bridge
@Column(columnDefinition = "POINT SRID 4326")
private Point location;   // org.locationtech.jts.geom.Point

@Query(value = """
    SELECT * FROM parking_space
    WHERE ST_Within(
      location,
      ST_Buffer(ST_GeomFromText(:wkt, 4326), :radiusDeg)
    )
    """, nativeQuery = true)
List<ParkingSpace> findWithinRadius(@Param("wkt") String wkt,
                                    @Param("radiusDeg") double radiusDeg);
```

The new `EXPLAIN` is `type=range`, `key=idx_location`, `rows=378`, `filtered=100 %`. The structural improvement is **96.2 % fewer rows scanned** — which dominates the wall-clock improvement once data exceeds the buffer pool.

### Phase 3 · Resilience4j (CircuitBreaker + Bulkhead) + Graceful Shutdown

```yaml
resilience4j:
  circuitbreaker:
    instances:
      redis-lock:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
  bulkhead:
    instances:
      booking:
        max-concurrent-calls: 20
        max-wait-duration: 0
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

The Circuit Breaker wraps the Redis-distributed-lock acquisition, **not** the booking method itself. When Redis errors cross the threshold, the breaker opens and `BookingService.createBookingWithoutLock(...)` becomes the fallback path — it relies entirely on the MySQL `SELECT FOR UPDATE` for correctness, sacrificing only the cross-instance lock that Redis was providing. Bulkhead caps concurrent booking calls to 20 to keep external slowness from starving the rest of the application's thread pool.

### Phase 4 · Redis GEO Cache (Write-Through)

```java
// On createParkingSpace(), atomically with the JPA save
geoCacheService.addSpace(space.getId(), space.getLongitude(), space.getLatitude());
// Read path tries Redis first
List<Long> hits = geoCacheService.findNearbySpaceIds(lng, lat, 5.0);
if (!hits.isEmpty()) return hits;
return parkingSpaceRepository.findWithinRadius(...);
```

Master data lives in MySQL (ACID + complex joins + transactional integrity). The Redis GEO layer absorbs the read-amplification of map-pan interactions where every drag fires a fresh radius query. This is **CQRS by storage role**, not by codebase split.

---

## 📐 Architecture Decisions (ADRs)

The full strategy document is at [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md). The three load-bearing decisions:

### ADR-001 · Pessimistic Lock over Optimistic Lock for booking
Booking is a high-conflict domain (same time slot, same space, multiple users). Optimistic locking would surface as `OptimisticLockException` at commit time and trigger application-level retries — a retry storm under load that adds DB pressure precisely when the DB is already strained. Pessimistic locking serializes contenders deterministically; the cost (held connection time) is bounded by minimizing the transaction scope (Phase 1).

### ADR-002 · Transactional Outbox over `@TransactionalEventListener` and Kafka
Documented in detail above. The shorthand is: **memory events lose data on process kill, Kafka does not solve Dual-Write either, Outbox does.**

### ADR-003 · MySQL Spatial Index + Redis GEO as a deliberate CQRS split
Redis GEO is wrong as a system of record (volatile, no joins, no ACID). MySQL Spatial is wrong as a sole read path under map-pan load (every pan = full radius query against the SoT). Splitting the responsibilities is the right answer; the wrong answer is "Redis everywhere" or "DB everywhere."

---

## 🎨 DDD Package Structure

The project was originally organized in classic layered packages (`controller/ service/ domain/ repository/ dto/`). On 2026-03-24 it was re-organized by **business domain**: 47 files moved across `reservation/`, `space/`, `notification/`, `user/`, `common/`. `BUILD SUCCESSFUL` after the move. Full record: [MD/DDD_PACKAGE_REFACTORING_260324.md](MD/DDD_PACKAGE_REFACTORING_260324.md).

```
com.parkingmate.parkingmate/
├── reservation/        Booking · Outbox (event + publisher) · BookingService · BookingController
├── space/              ParkingSpace · ParkingSpaceRepository (Spatial query) · GeoCacheService · ImageController
├── notification/       Notification · OutboxPollingPublisher · NotificationService
├── user/               User · UserService · JWT-authenticated UserController
└── common/             config (Redis, Security, Async) · filter (JwtAuthenticationFilter)
                        util (JwtUtil, DistributedLockUtil) · exception (GlobalExceptionHandler)
                        service (S3Service)
```

---

## 🚧 v2 Roadmap (currently not implemented)

These were promised in v1 marketing copy and are honestly tracked here as planned, **not delivered**:

| Capability | Status |
|---|---|
| Photo-evidence in/out check (image proof for dispute resolution) | 🚧 v2 planned |
| AI-driven optimal-space recommendation / dynamic pricing | 🚧 v2 planned |
| Prometheus + Grafana monitoring stack | 🚧 v2 planned |
| ECS / ASG auto-deploy (Terraform RDS/ECS modules + CI deploy stage) | 🚧 v2 planned |
| nGrinder / k6 production-grade load testing | 🚧 v2 planned |
| Multi-AZ ALB + ASG | 🚧 v2 planned (currently only ALB defined) |

The CI pipeline currently stops at "build & push to ECR"; the deploy step is a documented placeholder.

---

## 🚀 Local Development

### Prerequisites
- Java 21 (build & runtime)
- Node 18+
- Docker / Docker Compose
- MySQL 8 + Redis 7 (via docker-compose)

### Run the full stack
```bash
docker-compose up -d                    # MySQL + Redis containers
cd ParkingMate/parkingmate
./gradlew bootRun                       # Backend at :8080
cd ../parking-mate-frontend
npm install && npm run dev              # Frontend at :5173
```

### Reproduce the measurements
```bash
docker start parkingmate-mysql-test parkingmate-redis

cd ParkingMate/parkingmate
./gradlew test --tests "com.parkingmate.parkingmate.integration.ParkingMateIntegrationTest"
./gradlew test --tests "com.parkingmate.parkingmate.service.BookingConnectionTimingTest"

# Open the test report
open build/reports/tests/test/index.html
```

---

## 📚 Reference Documents

| Document | Purpose |
|---|---|
| [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md) | Master strategy + ADRs + interview Q&A scripts |
| [MD/BEFORE_MEASUREMENT_REPORT.md](MD/BEFORE_MEASUREMENT_REPORT.md) | Pre-Outbox / pre-Spatial baseline measurements |
| [MD/AFTER_MEASUREMENT_REPORT.md](MD/AFTER_MEASUREMENT_REPORT.md) | Post-implementation measurements & analysis |
| [MD/INTEGRATION_TEST_REPORT.md](MD/INTEGRATION_TEST_REPORT.md) | 8 / 8 test case walkthrough (real MySQL + Redis) |
| [MD/DDD_PACKAGE_REFACTORING_260324.md](MD/DDD_PACKAGE_REFACTORING_260324.md) | Layered → DDD package migration record |
| [MD/plan_0317.md](MD/plan_0317.md) | Phase 1–4 implementation plan |
| [MD/BACKEND_FEATURES.md](MD/BACKEND_FEATURES.md) | Currently shipped features inventory |
| [MD/API_DOCUMENTATION.md](MD/API_DOCUMENTATION.md) | REST API reference |

---

## 👤 Author

**Park, Gunwoo (gm-15)** — Software Engineering, Sangmyung University
- Backend & RDBMS specialization · Solo engineer for this project
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
- Email: gunwoo363@gmail.com
