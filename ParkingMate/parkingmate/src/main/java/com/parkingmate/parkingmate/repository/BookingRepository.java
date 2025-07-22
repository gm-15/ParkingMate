package com.parkingmate.parkingmate.repository;

import com.parkingmate.parkingmate.domain.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;
import com.parkingmate.parkingmate.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    // 특정 주차 공간에 대해, '특정 상태'의 예약 중 주어진 시간 범위와 겹치는 예약이 있는지 확인
    List<Booking> findByParkingSpaceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
            Long parkingSpaceId, BookingStatus status, LocalDateTime endTime, LocalDateTime startTime);

    // 특정 사용자의 모든 예약을 최신순으로 정렬하여 조회
    List<Booking> findByUser_IdOrderByStartTimeDesc(Long userId);
}