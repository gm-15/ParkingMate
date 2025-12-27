# 추가 개발 기능 상세 내역

## 1. 페이징 처리 (목록 조회) ✅

### 구현 내용
- Spring Data의 `Pageable`과 `Page`를 활용한 페이징 처리
- `PageResponse<T>` DTO 생성으로 페이징 정보 포함
- 기본 페이지 크기: 10개, 최대: 100개

### API 엔드포인트
```
GET /api/spaces?page=0&size=10&sortBy=latest
```

### 주요 파일
- `PageResponse.java`: 페이징 응답 DTO
- `ParkingSpaceSearchRequest.java`: 검색 요청 DTO
- `ParkingSpaceRepository.java`: 페이징 지원 쿼리 메서드 추가
- `ParkingSpaceService.java`: 페이징 로직 구현

---

## 2. 정렬 기능 (가격순, 최신순) ✅

### 구현 내용
- 정렬 옵션: `price_asc`, `price_desc`, `latest`
- 주소 검색과 함께 정렬 가능
- 기본값: `latest` (최신순)

### 정렬 옵션
- `price_asc`: 시간당 가격 오름차순
- `price_desc`: 시간당 가격 내림차순
- `latest`: 최신순 (ID 역순)

### API 사용 예시
```
GET /api/spaces?sortBy=price_asc
GET /api/spaces?address=강남구&sortBy=price_desc
```

---

## 3. 예약 가능 시간대 조회 API ✅

### 구현 내용
- 특정 주차 공간의 예약 가능한 시간대를 조회
- 시간대 단위로 슬롯 생성 (기본: 1시간)
- 예약된 시간대와 겹치지 않는 슬롯만 반환
- 각 슬롯의 예상 요금 계산

### API 엔드포인트
```
GET /api/spaces/{id}/available-slots?startDate=2024-01-01T00:00:00&endDate=2024-01-02T00:00:00&slotDurationHours=1
```

### 응답 예시
```json
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

### 주요 파일
- `AvailableTimeSlotDto.java`: 예약 가능 시간대 DTO
- `BookingService.java`: 시간대 조회 로직

---

## 4. 사진 업로드 기능 (S3 연동 코드) ✅

### 구현 내용
- AWS S3 SDK 연동 코드 작성
- 이미지 파일 검증 (jpg, jpeg, png, gif, webp)
- 다중 파일 업로드 지원
- 파일 삭제 기능
- S3 비활성화 시 플레이스홀더 URL 반환

### API 엔드포인트
```
POST /api/images/parking-spaces (다중 파일)
POST /api/images/upload (단일 파일)
DELETE /api/images?url={imageUrl}
```

### 설정
- `application.properties`에 S3 설정 추가 필요:
  ```properties
  aws.s3.bucket=parkingmate-images
  aws.s3.region=ap-northeast-2
  aws.s3.enabled=false
  ```

### 주요 파일
- `S3Service.java`: S3 업로드/삭제 서비스
- `ImageController.java`: 이미지 업로드 컨트롤러
- `ImageUploadResponse.java`: 업로드 응답 DTO
- `ParkingSpace.java`: `imageUrls` 필드 추가

---

## 5. 실시간 알림 시스템 ✅

### 구현 내용
- 알림 도메인 모델 및 Repository 구현
- 다양한 알림 타입 지원 (예약 생성, 취소, 리마인더 등)
- 읽음/읽지 않음 상태 관리
- 읽지 않은 알림 개수 조회

### 알림 타입
- `BOOKING_CREATED`: 예약 생성됨
- `BOOKING_CANCELED`: 예약 취소됨
- `BOOKING_REMINDER`: 예약 알림
- `NEW_SPACE_NEARBY`: 근처 새 주차 공간
- `SYSTEM`: 시스템 알림

### API 엔드포인트
```
GET /api/notifications - 알림 목록 조회
GET /api/notifications/unread-count - 읽지 않은 알림 개수
PUT /api/notifications/{id}/read - 알림 읽음 처리
PUT /api/notifications/read-all - 모든 알림 읽음 처리
```

### 주요 파일
- `Notification.java`: 알림 도메인 엔티티
- `NotificationType.java`: 알림 타입 enum
- `NotificationRepository.java`: 알림 Repository
- `NotificationService.java`: 알림 서비스
- `NotificationController.java`: 알림 컨트롤러
- `NotificationDto.java`: 알림 DTO

### 향후 개선 사항
- WebSocket 또는 Server-Sent Events (SSE)를 통한 실시간 알림 전송
- 푸시 알림 연동 (FCM, APNS)

---

## 6. 검색 고도화 (위치 기반 검색) ✅

### 구현 내용
- Haversine 공식을 사용한 거리 계산
- 위도/경도 기반 반경 검색
- 거리순 정렬 지원
- `ParkingSpace` 엔티티에 `latitude`, `longitude` 필드 추가

### API 엔드포인트
```
GET /api/spaces/nearby?latitude=37.5665&longitude=126.9780&radiusKm=5&sortBy=distance
```

### 파라미터
- `latitude`: 중심 위도 (필수)
- `longitude`: 중심 경도 (필수)
- `radiusKm`: 검색 반경 (km), 기본값: 5km
- `sortBy`: 정렬 기준 (distance, price_asc, price_desc, latest)
- `page`: 페이지 번호
- `size`: 페이지 크기

### 거리 계산
- Haversine 공식 사용
- 지구를 구로 가정하여 두 지점 간의 대원거리 계산
- 반경 내 주차 공간만 필터링

### 주요 파일
- `LocationSearchRequest.java`: 위치 검색 요청 DTO
- `ParkingSpace.java`: `latitude`, `longitude` 필드 추가
- `ParkingSpaceService.java`: 위치 기반 검색 로직

---

## 종합 요약

모든 추가 기능이 성공적으로 구현되었습니다:

1. ✅ 페이징 처리 - 대용량 데이터 효율적 조회
2. ✅ 정렬 기능 - 사용자 편의성 향상
3. ✅ 예약 가능 시간대 조회 - 예약 전 시간 확인
4. ✅ 사진 업로드 (S3) - 실제 이미지 관리 준비 완료
5. ✅ 실시간 알림 시스템 - 사용자 경험 개선
6. ✅ 위치 기반 검색 - 근거리 주차 공간 검색

모든 기능은 실제 AWS 리소스 없이도 코드 구조는 완성되었으며, 필요 시 설정만 추가하면 즉시 사용 가능합니다.

