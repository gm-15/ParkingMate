# 🅿️ ParkingMate

> 🇬🇧 **English version**: see [README.md](README.md)
> 📌 **이 파일은 v1 시점 한글 README의 보존본입니다.** 일부 항목(사진 증거 시스템·AI 추천·Prometheus/Grafana·ECS/ASG 자동 배포)은 v2 로드맵 항목으로, 현재 코드에 미구현 상태입니다.

신뢰 문제를 해결하여 시장을 혁신할 차세대 주차 공간 공유 플랫폼

## 📜 1. 프로젝트 핵심 요약

'ParkingMate'는 도심의 만성적인 주차난과 개인의 유휴 주차 공간이라는 두 가지 문제를 동시에 해결하는 P2P 주차 공간 공유 플랫폼입니다. 백엔드의 동시성 제어와 RDBMS 딥다이브를 통해 엔지니어링 역량을 증명하는 것이 목표.

핵심 차별점은 **'데이터 정합성과 장애 격리'를 직접 검증하는 RDBMS 딥다이브**입니다. 기존 모두의 주차장 등이 단순 중개에 그치는 것과 달리, 이중 예약·이벤트 정합성·실시간 가용성 문제를 Transactional Outbox + MySQL Spatial Index 조합으로 엔지니어링 레벨에서 해결.

## 🎯 2. 검증 완료 (Phase 1-4)

| Phase | 구현 | 검증 |
|---|---|---|
| Phase 1 | Transactional Outbox Pattern | Hikari 분산 1.6x → **1.2x**, 알림 유실 해결 |
| Phase 2 | MySQL Spatial Index (R-Tree) | EXPLAIN 스캔 행수 9,880 → **378 (96.2% 감소)** |
| Phase 3 | Resilience4j (CircuitBreaker + Bulkhead) | Redis 장애 시 DB Pessimistic Lock 자동 fallback |
| Phase 4 | Redis GEO Cache Layer | GEOSEARCH ~1ms, MySQL Spatial fallback |

**통합 테스트**: `ParkingMateIntegrationTest` 8/8 PASS (Outbox-1/2/3 · Spatial-1/2 · GEO-1 · Concurrency · Setup)

상세 측정 리포트: [MD/BEFORE_MEASUREMENT_REPORT.md](MD/BEFORE_MEASUREMENT_REPORT.md) · [MD/AFTER_MEASUREMENT_REPORT.md](MD/AFTER_MEASUREMENT_REPORT.md) · [MD/INTEGRATION_TEST_REPORT.md](MD/INTEGRATION_TEST_REPORT.md)

## ⚙️ 3. 핵심 기술 스택

### Backend
- Java 21 / Spring Boot 3.5.3 / Spring Data JPA + Hibernate Spatial / JTS 1.19
- Spring Security 6 + JWT (jjwt 0.11.5)
- Redis 7 (Lettuce) — 분산락 + GEO + 캐시
- Resilience4j 2.2.0 (CircuitBreaker + Bulkhead)

### Database
- MySQL 8.0 + Spatial Index (POINT SRID 4326, R-Tree)
- H2 (로컬 개발/테스트)

### Frontend
- React 19 / Vite 7 / Axios

### Infrastructure (v2 진행 중)
- Terraform — VPC / ALB / ECR / S3 정의 (RDS · ECS · ASG는 예정)
- GitHub Actions CI/CD (build·push까지 동작, ECS deploy는 예정)
- Docker / Docker Compose

## 🏗️ 4. 아키텍처 결정 (ADR)

자세한 ADR은 [MD/PARKINGMATE_STRATEGY_V2_0304.md](MD/PARKINGMATE_STRATEGY_V2_0304.md) 참조.

- **ADR-001**: Pessimistic Lock 선택 (낙관적 락의 재시도 폭풍 방지)
- **ADR-002**: Transactional Outbox 채택 (Kafka/`@TransactionalEventListener`의 Dual-Write·메모리 유실 한계 극복)
- **ADR-003**: MySQL Spatial(Source of Truth) + Redis GEO(Cache) CQRS 분리

## 🎨 5. DDD 패키지 구조 (2026-03-24 재구성, 47개 파일 이동)

```
com.parkingmate.parkingmate/
├── reservation/      예약 도메인 (Booking, Outbox 포함)
├── space/            공간 도메인 (ParkingSpace, GeoCacheService)
├── notification/     알림 도메인 (OutboxPollingPublisher 포함)
├── user/             사용자 도메인 (JWT 인증)
└── common/           공통 인프라 (config, filter, util, exception)
```

상세: [MD/DDD_PACKAGE_REFACTORING_260324.md](MD/DDD_PACKAGE_REFACTORING_260324.md)

## 🚧 6. v2 로드맵 (현재 미구현)

| 기능 | 상태 |
|------|------|
| 사진 증거 시스템 (입출차 사진 + 분쟁 해결) | 🚧 v2 예정 |
| AI 기반 최적 주차 공간 추천 / 동적 가격 제안 | 🚧 v2 예정 |
| Prometheus & Grafana 모니터링 | 🚧 v2 예정 |
| ECS / ASG 자동 배포 (Terraform 보강) | 🚧 v2 예정 |
| nGrinder / k6 본격 부하 테스트 | 🚧 v2 예정 |

## 🚀 7. 시작하기

### 백엔드
```bash
cd ParkingMate/parkingmate
./gradlew bootRun
```

### 프론트엔드
```bash
cd ParkingMate/parking-mate-frontend
npm install
npm run dev
```

### 통합·타이밍 테스트
```bash
docker start parkingmate-mysql-test parkingmate-redis
cd ParkingMate/parkingmate
./gradlew test --tests "com.parkingmate.parkingmate.integration.ParkingMateIntegrationTest"
./gradlew test --tests "com.parkingmate.parkingmate.service.BookingConnectionTimingTest"
```

## 👤 작성자

**박건우 (gm-15)** — 상명대학교 소프트웨어학과
- GitHub: [github.com/gm-15](https://github.com/gm-15)
- Blog: [velog.io/@gm-15](https://velog.io/@gm-15)
