package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.Booking;
import com.parkingmate.parkingmate.domain.ParkingSpace;
import com.parkingmate.parkingmate.domain.User;
import com.parkingmate.parkingmate.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.repository.BookingRepository;
import com.parkingmate.parkingmate.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import com.parkingmate.parkingmate.util.DistributedLockUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import com.parkingmate.parkingmate.dto.AvailableTimeSlotDto;
import com.parkingmate.parkingmate.dto.BookingResponseDto;
import java.util.stream.Collectors;
import com.parkingmate.parkingmate.domain.BookingStatus;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final DistributedLockUtil distributedLockUtil;

    /**
     * 주차 공간 예약 (동시성 제어 적용)
     * 
     * 동시성 제어 전략:
     * 1. Redis 분산 락: 분산 환경에서 동시 요청 차단
     * 2. Pessimistic Lock: DB 레벨에서 배타적 락 획득
     * 3. Optimistic Lock: 엔티티의 version 필드를 통한 낙관적 락 (자동 적용)
     */
    @Transactional
    public Long createBooking(BookingCreateRequestDto requestDto, String userEmail) {
        String lockKey = "parking-space-" + requestDto.getParkingSpaceId();
        
        // Redis 분산 락을 사용하여 동시 예약 요청 제어
        return distributedLockUtil.executeWithLock(lockKey, () -> {
            // 1. 사용자 및 주차 공간 엔티티 조회
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
            ParkingSpace parkingSpace = parkingSpaceRepository.findById(requestDto.getParkingSpaceId())
                    .orElseThrow(() -> new IllegalArgumentException("주차 공간을 찾을 수 없습니다."));

            // 2. Pessimistic Lock을 사용하여 중복 예약 확인 (DB 레벨 락)
            validateNoOverlappingBookingsWithLock(
                    requestDto.getParkingSpaceId(), 
                    requestDto.getStartTime(), 
                    requestDto.getEndTime()
            );

            // 3. 총 예약 금액 계산
            int totalPrice = calculatePrice(
                    requestDto.getStartTime(), 
                    requestDto.getEndTime(), 
                    parkingSpace.getPricePerHour()
            );

            // 4. 예약 엔티티 생성 및 저장 (Optimistic Lock 자동 적용)
            Booking booking = new Booking(
                    user,
                    parkingSpace,
                    requestDto.getStartTime(),
                    requestDto.getEndTime(),
                    totalPrice
            );
            bookingRepository.save(booking);

            log.info("Booking created: bookingId={}, parkingSpaceId={}, userEmail={}", 
                    booking.getId(), requestDto.getParkingSpaceId(), userEmail);
            
            return booking.getId();
        });
    }

    /**
     * Pessimistic Lock을 사용하여 중복 예약 검증
     * DB 레벨에서 배타적 락을 획득하여 동시성 문제 해결
     */
    @Transactional
    private void validateNoOverlappingBookingsWithLock(Long parkingSpaceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> overlappingBookings = bookingRepository
                .findOverlappingBookingsWithLock(
                        parkingSpaceId, 
                        BookingStatus.RESERVED, 
                        startTime, 
                        endTime
                );
        if (!overlappingBookings.isEmpty()) {
            throw new IllegalStateException("해당 시간대에 이미 예약된 내역이 존재합니다.");
        }
    }

    // 요금 계산 로직
    private int calculatePrice(LocalDateTime startTime, LocalDateTime endTime, int pricePerHour) {
        long hours = Duration.between(startTime, endTime).toHours();
        if (Duration.between(startTime, endTime).toMinutes() % 60 > 0) {
            hours++; // 1시간 미만은 1시간으로 계산
        }
        return (int) hours * pricePerHour;
    }

    /**
     * 나의 예약 내역 조회
     */
    public List<BookingResponseDto> findMyBookings(String userEmail) {
        // 1. 이메일로 사용자 엔티티를 찾습니다.
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        // 2. 해당 사용자의 ID로 모든 예약을 조회합니다.
        return bookingRepository.findByUser_IdOrderByStartTimeDesc(user.getId()).stream()
                .map(BookingResponseDto::new) // 각 Booking을 BookingResponseDto로 변환
                .collect(Collectors.toList()); // 리스트로 만들어 반환
    }

    /**
     * 예약 취소
     */
    @Transactional
    public void cancelBooking(Long bookingId, String userEmail) {
        // 1. 취소할 예약 엔티티를 찾습니다.
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약을 찾을 수 없습니다. id=" + bookingId));

        // 2. 요청을 보낸 사용자가 예약을 한 본인인지 확인합니다.
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalStateException("예약을 취소할 권한이 없습니다.");
        }

        // 3. (권한 확인 완료) 예약 상태를 'CANCELED'로 변경합니다.
        booking.cancel();
    }

    /**
     * 특정 주차 공간의 예약 가능한 시간대 조회
     * 
     * @param parkingSpaceId 주차 공간 ID
     * @param startDate 조회 시작 날짜
     * @param endDate 조회 종료 날짜
     * @param slotDurationHours 시간대 단위 (시간)
     * @return 예약 가능한 시간대 목록
     */
    public List<AvailableTimeSlotDto> getAvailableTimeSlots(Long parkingSpaceId, LocalDateTime startDate, LocalDateTime endDate, int slotDurationHours) {
        // 1. 주차 공간 존재 확인
        ParkingSpace parkingSpace = parkingSpaceRepository.findById(parkingSpaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + parkingSpaceId));

        // 2. 해당 기간의 예약된 시간대 조회
        List<Booking> reservedBookings = bookingRepository
                .findByParkingSpaceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
                        parkingSpaceId,
                        BookingStatus.RESERVED,
                        endDate,
                        startDate
                );

        // 3. 예약된 시간대를 블록으로 변환
        List<TimeBlock> reservedBlocks = reservedBookings.stream()
                .map(booking -> new TimeBlock(booking.getStartTime(), booking.getEndTime()))
                .collect(Collectors.toList());

        // 4. 예약 가능한 시간대 생성 (시간대 단위로 슬롯 생성)
        List<AvailableTimeSlotDto> availableSlots = new java.util.ArrayList<>();
        LocalDateTime current = startDate;
        int pricePerHour = parkingSpace.getPricePerHour();

        while (current.plusHours(slotDurationHours).isBefore(endDate) || current.plusHours(slotDurationHours).isEqual(endDate)) {
            LocalDateTime slotEnd = current.plusHours(slotDurationHours);
            TimeBlock slotBlock = new TimeBlock(current, slotEnd);

            // 예약된 시간대와 겹치지 않는지 확인
            boolean isAvailable = reservedBlocks.stream()
                    .noneMatch(reserved -> isOverlapping(reserved, slotBlock));

            if (isAvailable) {
                int totalPrice = slotDurationHours * pricePerHour;
                availableSlots.add(new AvailableTimeSlotDto(current, slotEnd, totalPrice));
            }

            current = current.plusHours(slotDurationHours);
        }

        return availableSlots;
    }

    /**
     * 시간 블록이 겹치는지 확인
     */
    private boolean isOverlapping(TimeBlock block1, TimeBlock block2) {
        return block1.startTime.isBefore(block2.endTime) && block1.endTime.isAfter(block2.startTime);
    }

    /**
     * 시간 블록 내부 클래스
     */
    private static class TimeBlock {
        final LocalDateTime startTime;
        final LocalDateTime endTime;

        TimeBlock(LocalDateTime startTime, LocalDateTime endTime) {
            this.startTime = startTime;
            this.endTime = endTime;
        }
    }
}