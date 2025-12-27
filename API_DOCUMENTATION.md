# ParkingMate API 문서

## 인증

대부분의 API는 JWT 토큰이 필요합니다. 헤더에 다음 형식으로 포함하세요:
```
Authorization: Bearer {token}
```

---

## 사용자 관리

### 회원가입
```
POST /api/users/signup
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123",
  "name": "홍길동"
}
```

### 로그인
```
POST /api/users/login
Content-Type: application/json

{
  "email": "user@example.com",
  "password": "password123"
}

Response:
{
  "success": true,
  "message": "성공",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
  }
}
```

### 내 프로필 조회
```
GET /api/users/me
Authorization: Bearer {token}
```

---

## 주차 공간 관리

### 주차 공간 목록 조회 (기본)
```
GET /api/spaces
GET /api/spaces?address=강남구
```

### 주차 공간 목록 조회 (페이징 및 정렬)
```
GET /api/spaces?page=0&size=10&sortBy=latest
GET /api/spaces?address=강남구&page=0&size=10&sortBy=price_asc

Query Parameters:
- page: 페이지 번호 (0부터 시작, 기본값: 0)
- size: 페이지 크기 (기본값: 10, 최대: 100)
- sortBy: 정렬 기준
  - price_asc: 가격 오름차순
  - price_desc: 가격 내림차순
  - latest: 최신순 (기본값)
- address: 주소 검색어 (선택사항)

Response:
{
  "success": true,
  "message": "성공",
  "data": {
    "content": [...],
    "page": 0,
    "size": 10,
    "totalElements": 50,
    "totalPages": 5,
    "first": true,
    "last": false
  }
}
```

### 위치 기반 검색
```
GET /api/spaces/nearby?latitude=37.5665&longitude=126.9780&radiusKm=5&sortBy=distance

Query Parameters:
- latitude: 중심 위도 (필수)
- longitude: 중심 경도 (필수)
- radiusKm: 검색 반경 (km, 기본값: 5)
- sortBy: 정렬 기준 (distance, price_asc, price_desc, latest)
- page: 페이지 번호
- size: 페이지 크기
```

### 주차 공간 상세 조회
```
GET /api/spaces/{id}
```

### 예약 가능 시간대 조회
```
GET /api/spaces/{id}/available-slots?startDate=2024-01-01T00:00:00&endDate=2024-01-02T00:00:00&slotDurationHours=1

Query Parameters:
- startDate: 조회 시작 날짜 (ISO 8601 형식, 필수)
- endDate: 조회 종료 날짜 (ISO 8601 형식, 필수)
- slotDurationHours: 시간대 단위 (시간, 기본값: 1)

Response:
{
  "success": true,
  "message": "성공",
  "data": [
    {
      "startTime": "2024-01-01T09:00:00",
      "endTime": "2024-01-01T10:00:00",
      "totalPrice": 2000
    }
  ]
}
```

### 주차 공간 등록
```
POST /api/spaces
Authorization: Bearer {token}
Content-Type: application/json

{
  "address": "서울시 강남구 테헤란로 123",
  "pricePerHour": 2000,
  "description": "지하 주차장",
  "latitude": 37.5665,
  "longitude": 126.9780
}
```

### 내가 등록한 주차 공간 조회
```
GET /api/spaces/my
Authorization: Bearer {token}
```

### 주차 공간 수정
```
PUT /api/spaces/{id}
Authorization: Bearer {token}
Content-Type: application/json

{
  "address": "서울시 강남구 테헤란로 456",
  "pricePerHour": 2500,
  "description": "수정된 설명"
}
```

### 주차 공간 삭제
```
DELETE /api/spaces/{id}
Authorization: Bearer {token}
```

---

## 예약 관리

### 예약 생성
```
POST /api/bookings
Authorization: Bearer {token}
Content-Type: application/json

{
  "parkingSpaceId": 1,
  "startTime": "2024-01-01T10:00:00",
  "endTime": "2024-01-01T14:00:00"
}
```

### 내 예약 목록 조회
```
GET /api/bookings/my
Authorization: Bearer {token}
```

### 예약 취소
```
DELETE /api/bookings/{id}
Authorization: Bearer {token}
```

---

## 이미지 업로드

### 주차 공간 이미지 업로드 (다중)
```
POST /api/images/parking-spaces
Authorization: Bearer {token}
Content-Type: multipart/form-data

files: [file1, file2, ...] (최대 5개)

Response:
{
  "success": true,
  "message": "성공",
  "data": {
    "imageUrls": [
      "https://bucket.s3.amazonaws.com/parking-spaces/uuid1.jpg",
      "https://bucket.s3.amazonaws.com/parking-spaces/uuid2.jpg"
    ],
    "uploadedCount": 2
  }
}
```

### 단일 이미지 업로드
```
POST /api/images/upload?folder=parking-spaces
Authorization: Bearer {token}
Content-Type: multipart/form-data

file: [file]
```

### 이미지 삭제
```
DELETE /api/images?url={imageUrl}
Authorization: Bearer {token}
```

---

## 알림 관리

### 알림 목록 조회
```
GET /api/notifications
Authorization: Bearer {token}
```

### 읽지 않은 알림 개수 조회
```
GET /api/notifications/unread-count
Authorization: Bearer {token}

Response:
{
  "success": true,
  "message": "성공",
  "data": 5
}
```

### 알림 읽음 처리
```
PUT /api/notifications/{id}/read
Authorization: Bearer {token}
```

### 모든 알림 읽음 처리
```
PUT /api/notifications/read-all
Authorization: Bearer {token}
```

---

## 에러 응답 형식

모든 에러는 다음 형식으로 반환됩니다:

```json
{
  "success": false,
  "message": "에러 메시지",
  "data": null
}
```

### HTTP 상태 코드
- 200: 성공
- 400: 잘못된 요청 (검증 실패 등)
- 401: 인증 실패
- 403: 권한 없음
- 404: 리소스를 찾을 수 없음
- 409: 충돌 (중복 예약 등)
- 500: 서버 오류

