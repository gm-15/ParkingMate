# 🅿️ ParkingMate ｜ P2P Parking Space Reservation Backend

> Spring Boot 3.5 / Java 21 P2P parking reservation backend.
> Solves three backend essentials (no double-booking, reservation/notification atomicity, geospatial search efficiency) that existing parking apps don't close, using RDBMS depth instead of Kafka or EKS.

🇰🇷 한글: [README.md](README.md) ｜ 📜 v1 legacy: [README_v1_legacy.ko.md](README_v1_legacy.ko.md)

---

## 📋 Project Summary

![ParkingMate data flow summary](docs/images/summary.png)

> Above: The reservation transaction (synchronous, `@Transactional`) atomically commits `booking` and `outbox_event`. Notification publishing runs asynchronously in a `@Scheduled` publisher with a 5 second tick. Even if notification fails, reservation data is preserved and failures stay traceable through the `FAILED` status.

| Item | Content |
|---|---|
| One-liner | P2P parking reservation backend, three core problems solved at the RDBMS layer |
| Role | **Solo full-stack** (backend, frontend, infra, DevOps) |
| Period | Dec 2025 ~ Mar 2026 |
| Core stack | Spring Boot 3.5.3 · MySQL 8 R-Tree Spatial Index · Redis GEO · Spring Data Redis on Lettuce · Resilience4j · Transactional Outbox |
| Verified assets | Outbox transaction separation (Hikari connection-hold variance ratio 1.6× → 1.2×) · MySQL Spatial Index (EXPLAIN 9,880 → 378, 96.2% scan reduction) · Integration tests 8/8 PASS |
| Intentionally excluded | Kafka, EKS (proven in other portfolios, Clmakase and INSK; this project is the RDBMS-depth piece) |
| Refactor | DDD 47-file package restructuring (commit `1f74025`, BUILD SUCCESSFUL preserved) |

---

## 🏙 What problem is this solving? (Background, AS IS / 5 Why)

### Surface symptom

Urban drivers spend time looking for parking while individual idle spaces never become tradable assets. About 30% of city traffic consists of vehicles searching for parking. Korea's leading service "Moduui Jucha-jang" functions more as a commercial-lot directory; the niche of turning private idle spaces into real-time tradable assets remains an open market.

### Drilling down with 5 Why

| Question | Answer |
|---|---|
| Why hasn't P2P parking sharing taken root in Korea? | Matching UX alone doesn't solve the trust problem. |
| Why does the trust problem leak from the backend? | Concurrency, external-module failure, and geospatial inefficiency accumulate invisibly below the surface. |
| Why didn't existing apps close those gaps? | They assumed infrastructure costs (message broker, microservices, observability) had to be paid first. |
| Is that assumption correct? | At ParkingMate's traffic and domain scale, a single RDBMS with proper depth can guarantee the same level of integrity. |

Based on this analysis, ParkingMate closes the three engineering gaps directly:

| Problem | Existing limitation | ParkingMate's solution |
|---|---|---|
| **No double-booking** | Without locks, two users booking the same slot at the same time can both succeed | Redis distributed lock (Lettuce SET NX EX, owner-verified DEL) inside the transaction plus DB Pessimistic Lock (`SELECT FOR UPDATE`) as layered defense |
| **Reservation/notification atomicity** | Dual-write problem: booking commits but notification disappears, user finds out at the gate | Transactional Outbox pattern: `booking` and `outbox_event` committed in the same transaction, `@Scheduled` publisher delivers at-least-once |
| **Geospatial search efficiency** | "Find nearby" does a full table scan plus Java Haversine. Degrades sharply past a few thousand entries | MySQL Spatial Index (POINT SRID 4326 plus R-Tree) and Redis GEO write-through cache |
| **Redis single-point failure** | Lock failure blocks all reservations | Resilience4j Circuit Breaker auto falls back to the DB-only Pessimistic Lock path |

Full decision flow: [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md)

---

## 📏 Verified measurements

All measurements come from `BookingConnectionTimingTest` and `ParkingMateIntegrationTest` against real MySQL 8.0 (Docker `localhost:3307`) plus Redis 7. See [Local execution](#-local-execution) for reproduction.

### Hikari connection-hold variance (10 iterations)

| Case | Description | TX queries | Avg | Range | Variance ratio | Notification failure to booking? |
|---|---|---|---|---|---|---|
| Case 1 | Baseline (no notification) | 4 | 33 ms | 28~41 ms | 1.5× | preserved |
| Case 2 | Problem reproduced (notification inside TX) | 6 | 37 ms | 32~51 ms | 1.6× | ❌ rolled back |
| **Case 3 (After)** | **Outbox (notification via separate publisher)** | **5** | **36 ms** | **33~40 ms** | **1.2×** | ✅ **preserved** |

> **The headline number is variance, not average.** On localhost the absolute time barely moves (37 → 36 ms, within noise). The real change is that the min-max ratio collapses from 1.6× to 1.2×. Notification-subsystem load no longer leaks into reservation latency.
>
> Extrapolation under RDS-style 10 ms/query networking: the same change implies a 60 ms → 50 ms connection-hold reduction. For 100 concurrent requests against a 10 connection Hikari pool this projects to roughly +17% throughput as **analytical extrapolation**. JMeter and k6 load testing were not performed.

### EXPLAIN ｜ Geospatial search (10,000 row sample)

| | Before (`lat/lng` BETWEEN) | **After (R-Tree `ST_Within`)** |
|---|---|---|
| `EXPLAIN.type` | `ALL` (full table scan) | **`range` (index range scan)** |
| `EXPLAIN.key` | `NULL` | **`idx_location`** |
| Rows scanned | 9,880 | **378** |
| `filtered` | 1.23% | **100%** |
| Execution time | 3.30 ms | 2.42 ms |

> **96.2% reduction in rows scanned.** At 10K rows the whole table fits in the InnoDB buffer pool, so timing differences are small. At 1M rows with disk I/O the same structural change becomes 38,000 rows scanned vs 1M rows full scan plus disk I/O, projected as an order-of-magnitude difference. Raw EXPLAIN screenshots were not preserved separately.

### Integration tests (`ParkingMateIntegrationTest`, 8/8 PASS)

| # | Case | What it verifies |
|---|---|---|
| 0 | Setup | 2 users, 2 ParkingSpaces (Gangnam plus Haeundae) |
| 1 | Outbox-1 | Booking creation writes one `outbox_event` row with `PENDING` in the same T1 |
| 2 | Outbox-2 | After publisher runs, a `notification` row exists and the outbox row transitions to `PROCESSED` |
| 3 | Outbox-3 | Corrupted payload throws in dispatch, outbox flips to `FAILED`, **booking is preserved** |
| 4 | Spatial-1 | Gangnam 5 km radius: Gangnam space included, Busan space excluded |
| 5 | Spatial-2 | 1 km radius: Busan still excluded |
| 6 | GEO-1 | Redis `GEOADD` write-through, `GEOSEARCH` returns exactly the matching space |
| 7 | Concurrency | CountDownLatch 2 thread race, exactly one `SELECT FOR UPDATE` succeeds |

JUnit XML preserved at `build/test-results/test/TEST-...IntegrationTest.xml` (Java 21.0.6, profile `mysqltest`, MySQL 8 plus Redis 7 Docker, host `DESKTOP-24C2CU9`).

---

## 🏛️ System architecture

### 1. Reservation transaction, happy path

![Reservation transaction happy path](docs/images/sequence-booking.png)

Inside the `@Transactional` method we hold both a Redis distributed lock (first line of defense) and a DB Pessimistic Lock (second line of defense). The `booking` INSERT and `outbox_event` INSERT commit atomically in the same transaction. The Redis lock is released inside the `finally` block (after owner verification) before COMMIT.

### 2. Redis-failure fallback

![Redis-failure fallback](docs/images/fallback.png)

When the Redis lock call raises an exception, `@CircuitBreaker(name="redis-lock", fallbackMethod="createBookingWithoutLock")` automatically invokes the fallback. The reservation proceeds with the DB Pessimistic Lock alone, preserving both availability and integrity.

Resilience4j config: sliding-window 10, failure-rate 50%, wait-duration 30s.

### 3. Outbox asynchronous notification processing

![Outbox asynchronous notification processing](docs/images/outbox.png)

A `@Scheduled(fixedDelay = 5000)` publisher reads PENDING events (up to 10) in a read-only transaction, then processes each event inside its own `REQUIRES_NEW` transaction. One event's failure does not propagate to the others. If notification processing throws (payload parse error, missing userId, DB exception), the outbox row is marked `FAILED` and the `booking` row remains intact.

### Visual asset status

- ✅ Reservation sequence (`sequence-booking.png`)
- ✅ Redis-failure fallback (`fallback.png`)
- ✅ Outbox async processing (`outbox.png`)
- ✅ Summary data flow (`summary.png`)
- 📌 Planned: ParkingMate UI main-page screenshot
- 📌 Planned: R-Tree Spatial Index search-area map visualization
- 📌 Planned: DDD 47-file package structure diagram

---

## ✅ Implemented

- Transactional Outbox pattern (`booking` plus `outbox_event` atomic commit, `@Scheduled(fixedDelay=5000)` publisher, `REQUIRES_NEW` per event)
- Pessimistic Lock (`@Lock(LockModeType.PESSIMISTIC_WRITE)` plus JPQL overlap query) with Lettuce-based Redis distributed lock as layered defense
- Resilience4j Circuit Breaker (sliding-window 10, failure-rate 50%, wait 30s) plus Bulkhead (max-concurrent 20)
- Automatic fallback to `createBookingWithoutLock` on Redis failure (DB Pessimistic Lock only)
- MySQL POINT/SRID 4326 plus R-Tree SPATIAL INDEX geospatial search (`ST_Within` then `ST_Distance_Sphere`, two-stage filter)
- Redis GEO write-through cache (`GEOADD` / `GEOSEARCH` / `ZREM`) with MySQL Spatial fallback
- DDD 47 file domain package refactor (`user` / `space` / `reservation` / `notification` / `common`, BUILD SUCCESSFUL preserved)
- Integration tests: 8 cases covering happy and failure paths (Outbox 3, Spatial 2, GEO 1, Concurrency 1, Setup 1)
- JWT auth plus Spring Security STATELESS plus BCrypt
- Minimal frontend CRUD (React 19 plus Vite plus axios `/api` proxy)
- Terraform: VPC, 2x Public/Private Subnet, NAT GW, SG (ALB·App·RDS·Redis·Bastion), ALB+TG+Listener, S3, ECR

## 🚧 Designed / In Progress

- 🚧 **Outbox idempotency key** : Consumer-side unique constraint (`notification.outbox_event_id`) is Designed, not yet implemented. Current guarantee is at-least-once
- 🚧 **Automatic FAILED retry worker** : Exponential backoff plus max-retry policy is Designed; current code preserves FAILED state only
- 🚧 **Automated chaos testing** : Verified manually via `docker stop` of the Redis container. Automation with ToxiProxy is the next step
- 🚧 **DDD aggregate boundary** : Domain-package cohesion applied. Explicit aggregate roots and Repository-per-aggregate are the next step
- 🚧 **CI/CD deploy stage** : Terraform skeleton in place. ECS Fargate auto-deploy stage is currently an echo placeholder

## 📋 Planned (Roadmap)

### v2
- [ ] CI/CD deploy stage (ECS Fargate auto-deploy)
- [ ] Photo-evidence system (S3 plus presigned URL for vehicle position at checkout)
- [ ] Operational metrics (Prometheus plus Grafana: TPS, P99, lock hold time)
- [ ] Notification channel expansion (currently in-app only; SMS, push planned)
- [ ] JMeter and k6 load testing

### Long term
- [ ] ML-based recommendation (user booking patterns)
- [ ] Multi-region (Redis GEO distributed consistency)

---

## 🔧 Intentionally not done

- ❌ **Kafka** ｜ Already validated in another portfolio (Clmakase) with a 3-broker StatefulSet. Bringing it here would be over-engineering
- ❌ **EKS / Kubernetes** ｜ Same reason as above
- ❌ **Microservice split** ｜ DDD package isolation inside a single module is sufficient at this scale
- ❌ **Redisson** ｜ Simplified to Spring Data Redis on Lettuce: `setIfAbsent` (SET NX EX) with owner-verified DEL. Intentional choice for the single-node Redis stage. Redisson clustering and watchdog auto-renewal come in when traffic demands it
- ❌ **Operational metrics (TPS, P99)** ｜ Only Hikari variance and EXPLAIN measurements are claimed as verified
- ❌ **JMeter and k6 load testing** ｜ Throughput extrapolation is analytical, not measured

This project is the RDBMS-depth piece. Other infrastructure depth is shown in other projects.

---

## 🛠 Tech stack

| Area | Stack |
|---|---|
| Backend | Java 21, Spring Boot 3.5.3, Spring Data JPA, Spring Security, jjwt 0.11.5 |
| DB · Cache | MySQL 8.0 (POINT SRID 4326 plus R-Tree), Redis 7 (GEO plus Lettuce-based distributed lock) |
| Spatial library | Hibernate Spatial, JTS Core 1.19.0 |
| Reliability | Transactional Outbox, Pessimistic Lock, Resilience4j 2.2.0 (CircuitBreaker plus Bulkhead) |
| Testing | JUnit 5, Spring Boot Test, CountDownLatch 2 thread race |
| Frontend | React 19, Vite 7, axios, react-router-dom |
| Infra (Terraform) | VPC, 2x Public/Private Subnet, NAT GW, ALB, ECR, S3 (ECS auto-deploy planned for v2) |
| CI/CD | GitHub Actions (backend·frontend test → build → ECR push) |

---

## 👤 Role and responsibilities (solo)

| Area | Responsibility |
|---|---|
| Backend (Java 21 / Spring Boot 3.5.3) | Domain modeling, repository design, transaction boundaries |
| Concurrency design | Pessimistic Lock plus Outbox plus Resilience4j composition, fallback method design |
| DB engineering | MySQL Spatial Index design, EXPLAIN-based optimization, JPA / JTS integration |
| Caching | Redis GEO write-through, Lettuce-based distributed lock primitives |
| Frontend (React 19 / Vite 7) | Minimal CRUD UI for the parking flow |
| Infra (Terraform) | VPC / ALB / ECR / S3 modules |
| CI/CD (GitHub Actions) | backend·frontend test → build → ECR push (deploy stage planned for v2) |
| Documentation | Strategy docs, before/after measurement reports, integration test report |

---

## 🏗 Local execution

### Prerequisites

- Java 21
- Docker, Docker Compose
- Node.js 18+ (for frontend)

### Backend

```bash
# Start DB and Redis containers
cd ParkingMate
docker-compose up -d mysql redis

# Run backend
cd ParkingMate/parkingmate
./gradlew bootRun
```

### Frontend

```bash
cd ParkingMate/parking-mate-frontend
npm install
npm run dev
# http://localhost:5173 (Vite proxy forwards /api to localhost:8080)
```

### Reproducing measurements and tests

```bash
# Start the measurement-only MySQL (port 3307) and Redis containers
docker start parkingmate-mysql-test parkingmate-redis

# Integration tests (8 cases)
cd ParkingMate/parkingmate
./gradlew test --tests "com.parkingmate.parkingmate.integration.ParkingMateIntegrationTest"

# Hikari variance measurement (Case 1/2/3, 10 iterations each)
./gradlew test --tests "*BookingConnectionTimingTest"

# Result report
# build/reports/tests/test/index.html
```

---

## 📝 Notes (current status)

- **Measurement scope** ｜ Localhost MySQL Docker, single thread, 10 iterations. The comparison is over variance ratio, not absolute values
- **EXPLAIN dataset** ｜ 10K-row sample. 1M-row behavior is explicitly an extrapolation
- **Load testing** ｜ JMeter and k6 not performed. The +17% throughput figure is an analytical extrapolation from connection-hold time
- **Chaos testing** ｜ No automation. Validated by `docker stop` on the Redis container
- **Outbox semantics** ｜ At-least-once delivery only. Consumer-side idempotency and FAILED auto-retry are Designed
- **DDD** ｜ Domain-package cohesion only. Explicit aggregate boundaries are the next step
- **Raw EXPLAIN output** ｜ Screenshots and logs not preserved separately. Re-capture planned for v2

---

## 📚 In-repo reference docs

- [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md) ｜ Strategy doc (decision flow)
- [MD/BEFORE_MEASUREMENT_REPORT.md](MD/BEFORE_MEASUREMENT_REPORT.md) ｜ Hikari variance and EXPLAIN, before state
- [MD/AFTER_MEASUREMENT_REPORT.md](MD/AFTER_MEASUREMENT_REPORT.md) ｜ Outbox and Spatial Index, after state
- [MD/INTEGRATION_TEST_REPORT.md](MD/INTEGRATION_TEST_REPORT.md) ｜ Full 8/8 integration test report
- [README.md](README.md) ｜ Korean master
- [README_v1_legacy.ko.md](README_v1_legacy.ko.md) ｜ v1 legacy snapshot

---

## 🔗 Contact

**Gunwoo Park ｜ Backend Engineer**

- Email: Gunwoo363@gmail.com
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
