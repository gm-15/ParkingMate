# ParkingMate 백엔드 기능 명세서

## 📋 현재 구현된 기능

### 1. 사용자 관리 (User Management)

#### API 엔드포인트
- `POST /api/users/signup` - 회원가입
- `POST /api/users/login` - 로그인 (JWT 토큰 발급)
- `GET /api/users/me` - 내 프로필 조회

#### 주요 기능
- ✅ 이메일 기반 회원가입
- ✅ BCrypt를 이용한 비밀번호 암호화
- ✅ JWT 기반 인증/인가
- ✅ 사용자 프로필 조회

#### 도메인 모델
```java
User {
  - id: Long
  - email: String (unique)
  - password: String (encrypted)
  - name: String
}
```

---

### 2. 주차 공간 관리 (Parking Space Management)

#### API 엔드포인트
- `POST /api/spaces` - 주차 공간 등록 (인증 필요)
- `GET /api/spaces` - 주차 공간 목록 조회 (주소 검색 가능)
- `GET /api/spaces/{id}` - 주차 공간 상세 조회
- `PUT /api/spaces/{id}` - 주차 공간 정보 수정 (소유자만)
- `DELETE /api/spaces/{id}` - 주차 공간 삭제 (소유자만)

#### 주요 기능
- ✅ 주차 공간 등록 (주소, 시간당 가격, 상세 설명)
- ✅ 주차 공간 목록 조회
- ✅ 주소 기반 검색
- ✅ 주차 공간 상세 조회
- ✅ 소유자 권한 기반 수정/삭제

#### 도메인 모델
```java
ParkingSpace {
  - id: Long
  - user: User (소유자)
  - address: String
  - pricePerHour: int
  - description: String
}
```

---

### 3. 예약 관리 (Booking Management)

#### API 엔드포인트
- `POST /api/bookings` - 예약 생성 (인증 필요)
- `GET /api/bookings/my` - 내 예약 목록 조회
- `DELETE /api/bookings/{id}` - 예약 취소

#### 주요 기능
- ✅ 주차 공간 예약 생성
- ✅ 예약 시간 겹침 검증
- ✅ 요금 자동 계산 (시간당 가격 기반)
- ✅ 내 예약 목록 조회
- ✅ 예약 취소 (예약자만)

#### 동시성 제어
- ✅ Redis 분산 락 (분산 환경 동시성 제어)
- ✅ Pessimistic Lock (DB 레벨 배타적 락)
- ✅ Optimistic Lock (@Version 기반 낙관적 락)

#### 도메인 모델
```java
Booking {
  - id: Long
  - user: User (예약자)
  - parkingSpace: ParkingSpace
  - startTime: LocalDateTime
  - endTime: LocalDateTime
  - totalPrice: int
  - status: BookingStatus (RESERVED, CANCELED)
  - version: Long (Optimistic Lock)
}
```

---

## 🔧 기술 스택

### Framework & Library
- Java 21
- Spring Boot 3.x
- Spring Data JPA
- Spring Security
- JWT (jjwt)
- Redis (Lettuce)
- Lombok

### Database
- MySQL (프로덕션)
- H2 (로컬 개발/테스트)
- Redis (캐시 및 분산 락)

### 보안
- Spring Security
- JWT 인증
- BCrypt 비밀번호 암호화
- CORS 설정

---

## ⚠️ 부족한 기능 및 개선 사항

### 1. 예외 처리 및 에러 응답 표준화
- ❌ GlobalExceptionHandler 미구현
- ❌ 표준화된 에러 응답 형식 없음
- ❌ Validation 에러 처리 부족

### 2. 입력 검증 (Validation)
- ❌ DTO 필드 검증 어노테이션 미적용
- ❌ 비즈니스 로직 검증 부족

### 3. 추가 기능
- ❌ 내가 등록한 주차 공간 조회 API
- ❌ 특정 주차 공간의 예약 가능 시간대 조회
- ❌ 페이징 처리 (목록 조회)
- ❌ 정렬 기능 (가격순, 최신순 등)

### 4. 응답 표준화
- ❌ API 응답 형식 통일 필요
- ❌ 에러 코드 체계 부재

### 5. 로깅 및 모니터링
- ❌ 구조화된 로깅 부족
- ❌ 성능 모니터링 미구현

---

## 📝 다음 단계 개발 계획

1. **예외 처리 및 Validation 추가** (우선순위: 높음)
2. **API 응답 표준화**
3. **내가 등록한 주차 공간 조회 기능**
4. **예약 가능 시간대 조회 기능**
5. **페이징 및 정렬 기능**

