package com.parkingmate.parkingmate.domain;

public enum NotificationType {
    BOOKING_CREATED,      // 예약 생성됨
    BOOKING_CANCELED,     // 예약 취소됨
    BOOKING_REMINDER,     // 예약 알림
    NEW_SPACE_NEARBY,     // 근처 새 주차 공간
    SYSTEM                // 시스템 알림
}

