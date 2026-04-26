# DDD 패키지 재구성 기록

> 작성일: 2026-03-24
> 대상 시스템: ParkingMate — P2P 주차 공간 공유 플랫폼

---

## 1. DDD가 뭔가요?

DDD(Domain-Driven Design, 도메인 주도 설계)는 한 마디로 **"비즈니스 개념 단위로 코드를 묶는다"**는 아이디어입니다.

### 비유: 회사 조직도로 이해하기

**기존 방식 (레이어드 아키텍처)**은 직무 기준으로 팀을 만드는 것과 같습니다.

```
영업팀    → 영업사원 A (자동차), 영업사원 B (전자제품), 영업사원 C (식품)
마케팅팀  → 마케터 A (자동차), 마케터 B (전자제품), 마케터 C (식품)
기획팀    → 기획자 A (자동차), 기획자 B (전자제품), 기획자 C (식품)
```

자동차 관련 업무를 하려면 영업팀, 마케팅팀, 기획팀을 모두 돌아다녀야 합니다.

**DDD 방식**은 사업 단위로 팀을 만드는 것과 같습니다.

```
자동차 사업부   → 자동차 영업 + 자동차 마케터 + 자동차 기획자
전자제품 사업부 → 전자제품 영업 + 전자제품 마케터 + 전자제품 기획자
식품 사업부     → 식품 영업 + 식품 마케터 + 식품 기획자
```

자동차 관련 업무는 자동차 사업부 안에서 모두 해결됩니다.

---

## 2. 코드에서 어떻게 다른가요?

### Before: 레이어드 패키지 구조

```
com.parkingmate.parkingmate/
├── controller/        ← 모든 컨트롤러
│   ├── BookingController.java        (예약)
│   ├── ParkingSpaceController.java   (공간)
│   ├── NotificationController.java   (알림)
│   └── UserController.java           (사용자)
├── service/           ← 모든 서비스
│   ├── BookingService.java
│   ├── ParkingSpaceService.java
│   ├── NotificationService.java
│   └── UserService.java
├── domain/            ← 모든 엔티티
│   ├── Booking.java
│   ├── ParkingSpace.java
│   ├── Notification.java
│   └── User.java
├── repository/        ← 모든 레포지토리
│   ├── BookingRepository.java
│   └── ...
└── dto/               ← 모든 DTO
    ├── BookingCreateRequestDto.java
    └── ...
```

**문제점**: "예약" 기능 하나를 수정하려면 controller/, service/, domain/, repository/, dto/ 폴더를 모두 열어야 합니다.

---

### After: DDD 패키지 구조

```
com.parkingmate.parkingmate/
├── reservation/       ← "예약" 도메인 — 이 안에 예약에 관한 모든 것
│   ├── controller/   BookingController
│   ├── service/      BookingService
│   ├── domain/       Booking, BookingStatus
│   ├── repository/   BookingRepository
│   └── dto/          BookingCreateRequestDto, BookingResponseDto, AvailableTimeSlotDto
│
├── space/             ← "공간" 도메인 — 주차 공간에 관한 모든 것
│   ├── controller/   ParkingSpaceController, ImageController
│   ├── service/      ParkingSpaceService, GeoCacheService
│   ├── domain/       ParkingSpace
│   ├── repository/   ParkingSpaceRepository
│   └── dto/          ParkingSpace*Dto, LocationSearchRequest, ImageUploadResponse
│
├── notification/      ← "알림" 도메인 — 알림과 Outbox에 관한 모든 것
│   ├── controller/   NotificationController
│   ├── service/      NotificationService, OutboxPollingPublisher
│   ├── domain/       Notification, NotificationType, OutboxEvent, OutboxEventStatus
│   ├── repository/   NotificationRepository, OutboxRepository
│   └── dto/          NotificationDto
│
├── user/              ← "사용자" 도메인 — 회원에 관한 모든 것
│   ├── controller/   UserController
│   ├── service/      UserService
│   ├── domain/       User
│   ├── repository/   UserRepository
│   └── dto/          UserLoginRequestDto, UserLoginResponseDto, UserProfileResponseDto, UserSignUpRequestDto
│
└── common/            ← 도메인 공통 — 여러 도메인이 함께 쓰는 것
    ├── config/       RedisConfig, SecurityConfig, WebConfig
    ├── filter/       JwtAuthenticationFilter
    ├── exception/    GlobalExceptionHandler
    ├── util/         JwtUtil, DistributedLockUtil
    ├── dto/          ApiResponse, PageResponse
    └── service/      S3Service
```

**개선점**: "예약" 기능을 수정할 때 `reservation/` 폴더 하나만 열면 됩니다.

---

## 3. ParkingMate에서 도메인을 어떻게 구분했나요?

비즈니스 용어(==도메인)를 기준으로 구분했습니다.

| 도메인 | 의미 | 포함된 것 |
|--------|------|---------|
| `reservation` | 예약 행위 | 예약 생성/취소/조회, 예약 상태, 시간 중복 체크 |
| `space` | 주차 공간 | 공간 등록/수정, 위치 검색, GEO 캐시, 사진 업로드 |
| `notification` | 알림 | 알림 생성/조회, Outbox 이벤트, Polling Publisher |
| `user` | 사용자 | 회원가입, 로그인, JWT 발급, 프로필 |
| `common` | 공통 인프라 | 인증 필터, 보안 설정, 예외 처리, S3, Redis 설정 |

---

## 4. 변경 과정

### 변경 전 상태

| 항목 | 내용 |
|------|------|
| 구조 | 레이어드 (controller / service / domain / repository / dto) |
| 파일 수 | 47개 Java 파일 |
| 문제 | 기능 하나 찾으려면 5개 폴더를 왔다 갔다 해야 함 |

### 변경 작업

1. 각 파일을 새 경로로 이동
2. 파일 맨 위 `package` 선언 수정
   ```java
   // Before
   package com.parkingmate.parkingmate.service;

   // After
   package com.parkingmate.parkingmate.reservation.service;
   ```
3. 파일 내 모든 `import` 경로 수정
   ```java
   // Before
   import com.parkingmate.parkingmate.domain.Booking;
   import com.parkingmate.parkingmate.repository.BookingRepository;

   // After
   import com.parkingmate.parkingmate.reservation.domain.Booking;
   import com.parkingmate.parkingmate.reservation.repository.BookingRepository;
   ```
4. 기존 레이어드 디렉토리 삭제
5. 컴파일 검증: **BUILD SUCCESSFUL (오류 0)**

### 변경 범위

| 항목 | 내용 |
|------|------|
| 이동된 파일 | 47개 |
| 삭제된 디렉토리 | config/, controller/, domain/, dto/, exception/, filter/, repository/, service/, util/ |
| 생성된 도메인 패키지 | reservation/, space/, notification/, user/, common/ |
| 컴파일 결과 | BUILD SUCCESSFUL |

---

## 5. 왜 DDD가 포트폴리오에 의미 있나요?

레이어드 구조도 동작은 합니다. 하지만 DDD 구조로 바꾼다는 것은 코드를 **단순히 돌아가는 수준에서 유지보수 가능한 수준으로 끌어올리는 것**입니다.

면접에서 이렇게 설명할 수 있습니다:

> "처음에는 레이어드 구조로 빠르게 구현했습니다. 그런데 기능이 늘어나면서 예약 관련 코드가 controller/, service/, domain/, repository/에 분산되어 파악이 어렵다는 걸 느꼈습니다. DDD 방식으로 도메인 기준으로 재구성하니 예약 관련 모든 코드가 reservation/ 안에 모여 응집도가 높아졌고, 새 기능을 추가할 때 어디에 코드를 넣어야 할지 명확해졌습니다."

---

## 관련 파일

```
MD/DDD_PACKAGE_REFACTORING_260324.md    ← 본 문서
ParkingMate/parkingmate/src/main/java/com/parkingmate/parkingmate/
  reservation/    예약 도메인
  space/          공간 도메인
  notification/   알림 도메인
  user/           사용자 도메인
  common/         공통 인프라
```
