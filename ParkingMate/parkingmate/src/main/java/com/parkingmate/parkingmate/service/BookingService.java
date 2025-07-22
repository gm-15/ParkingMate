package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.Booking;
import com.parkingmate.parkingmate.domain.ParkingSpace;
import com.parkingmate.parkingmate.domain.User;
import com.parkingmate.parkingmate.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.repository.BookingRepository;
import com.parkingmate.parkingmate.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import com.parkingmate.parkingmate.dto.BookingResponseDto;
import java.util.stream.Collectors;
import com.parkingmate.parkingmate.domain.BookingStatus;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;

    /**
     * 주차 공간 예약
     */
    @Transactional
    public Long createBooking(BookingCreateRequestDto requestDto, String userEmail) {
        // 1. 사용자 및 주차 공간 엔티티 조회
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        ParkingSpace parkingSpace = parkingSpaceRepository.findById(requestDto.getParkingSpaceId())
                .orElseThrow(() -> new IllegalArgumentException("주차 공간을 찾을 수 없습니다."));

        // 2. 중복 예약 확인
        validateNoOverlappingBookings(requestDto.getParkingSpaceId(), requestDto.getStartTime(), requestDto.getEndTime());

        // 3. 총 예약 금액 계산
        int totalPrice = calculatePrice(requestDto.getStartTime(), requestDto.getEndTime(), parkingSpace.getPricePerHour());

        // 4. 예약 엔티티 생성 및 저장
        Booking booking = new Booking(
                user,
                parkingSpace,
                requestDto.getStartTime(),
                requestDto.getEndTime(),
                totalPrice
        );
        bookingRepository.save(booking);

        return booking.getId();
    }

    // 중복 예약 검증 로직 수정
    private void validateNoOverlappingBookings(Long parkingSpaceId, LocalDateTime startTime, LocalDateTime endTime) {
        // 상태가 'RESERVED'인 예약들 중에서만 중복을 확인
        List<Booking> overlappingBookings = bookingRepository
                .findByParkingSpaceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
                        parkingSpaceId, BookingStatus.RESERVED, endTime, startTime);
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
}