package com.parkingmate.parkingmate.repository;

import com.parkingmate.parkingmate.domain.Booking;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import com.parkingmate.parkingmate.domain.BookingStatus;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    // 특정 주차 공간에 대해, '특정 상태'의 예약 중 주어진 시간 범위와 겹치는 예약이 있는지 확인
    List<Booking> findByParkingSpaceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
            Long parkingSpaceId, BookingStatus status, LocalDateTime endTime, LocalDateTime startTime);

    // 특정 사용자의 모든 예약을 최신순으로 정렬하여 조회
    List<Booking> findByUser_IdOrderByStartTimeDesc(Long userId);

    // Pessimistic Lock을 사용하여 예약 조회
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.id = :id")
    Optional<Booking> findByIdWithPessimisticLock(@Param("id") Long id);

    // 특정 주차 공간에 대해 Pessimistic Lock을 사용하여 겹치는 예약 확인
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booking b WHERE b.parkingSpace.id = :parkingSpaceId " +
           "AND b.status = :status " +
           "AND b.startTime < :endTime " +
           "AND b.endTime > :startTime")
    List<Booking> findOverlappingBookingsWithLock(
            @Param("parkingSpaceId") Long parkingSpaceId,
            @Param("status") BookingStatus status,
            @Param("startTime") LocalDateTime startTime,
            @Param("endTime") LocalDateTime endTime);
}