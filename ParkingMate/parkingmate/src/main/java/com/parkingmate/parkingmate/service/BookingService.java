package com.parkingmate.parkingmate.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingmate.parkingmate.domain.Booking;
import com.parkingmate.parkingmate.domain.BookingStatus;
import com.parkingmate.parkingmate.domain.OutboxEvent;
import com.parkingmate.parkingmate.domain.ParkingSpace;
import com.parkingmate.parkingmate.domain.User;
import com.parkingmate.parkingmate.dto.AvailableTimeSlotDto;
import com.parkingmate.parkingmate.dto.BookingCreateRequestDto;
import com.parkingmate.parkingmate.dto.BookingResponseDto;
import com.parkingmate.parkingmate.repository.BookingRepository;
import com.parkingmate.parkingmate.repository.OutboxRepository;
import com.parkingmate.parkingmate.repository.ParkingSpaceRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import com.parkingmate.parkingmate.util.DistributedLockUtil;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BookingService {

    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final ParkingSpaceRepository parkingSpaceRepository;
    private final OutboxRepository outboxRepository;
    private final DistributedLockUtil distributedLockUtil;
    private final ObjectMapper objectMapper;

    /**
     * 주차 공간 예약
     *
     * 동시성 제어:
     *   1. Redis 분산 락 (CircuitBreaker 보호 — Redis 장애 시 fallback)
     *   2. Pessimistic Lock (SELECT FOR UPDATE) — DB 레벨 최종 방어선
     *
     * Outbox 패턴:
     *   예약 저장과 outbox_event INSERT를 동일 트랜잭션(T1)에 묶어 원자적 보장.
     *   알림은 OutboxPollingPublisher(T2)가 별도 트랜잭션으로 처리.
     *
     * Bulkhead:
     *   동시 예약 요청 최대 20개로 제한.
     */
    @Transactional
    @Bulkhead(name = "booking")
    @CircuitBreaker(name = "redis-lock", fallbackMethod = "createBookingWithoutLock")
    public Long createBooking(BookingCreateRequestDto requestDto, String userEmail) {
        String lockKey = "parking-space-" + requestDto.getParkingSpaceId();

        return distributedLockUtil.executeWithLock(lockKey, () ->
                doCreateBooking(requestDto, userEmail)
        );
    }

    // CircuitBreaker fallback — Redis 장애 시 DB 락만으로 처리
    public Long createBookingWithoutLock(BookingCreateRequestDto requestDto, String userEmail, Throwable t) {
        log.warn("Redis lock unavailable, proceeding with DB lock only. cause={}", t.getMessage());
        return doCreateBooking(requestDto, userEmail);
    }

    private Long doCreateBooking(BookingCreateRequestDto requestDto, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        ParkingSpace parkingSpace = parkingSpaceRepository.findById(requestDto.getParkingSpaceId())
                .orElseThrow(() -> new IllegalArgumentException("주차 공간을 찾을 수 없습니다."));

        validateNoOverlappingBookings(requestDto.getParkingSpaceId(), requestDto.getStartTime(), requestDto.getEndTime());

        int totalPrice = calculatePrice(requestDto.getStartTime(), requestDto.getEndTime(), parkingSpace.getPricePerHour());

        Booking booking = new Booking(user, parkingSpace, requestDto.getStartTime(), requestDto.getEndTime(), totalPrice);
        bookingRepository.save(booking);

        // Outbox 이벤트 기록 (T1과 동일 트랜잭션 — 원자적 보장)
        saveOutboxEvent("BOOKING_CREATED", booking.getId(), user.getId(), parkingSpace.getAddress());

        log.info("Booking created: bookingId={}, parkingSpaceId={}, userEmail={}",
                booking.getId(), requestDto.getParkingSpaceId(), userEmail);

        return booking.getId();
    }

    private void validateNoOverlappingBookings(Long parkingSpaceId, LocalDateTime startTime, LocalDateTime endTime) {
        List<Booking> overlapping = bookingRepository.findOverlappingBookingsWithLock(
                parkingSpaceId, BookingStatus.RESERVED, startTime, endTime);
        if (!overlapping.isEmpty()) {
            throw new IllegalStateException("해당 시간대에 이미 예약된 내역이 존재합니다.");
        }
    }

    private void saveOutboxEvent(String eventType, Long bookingId, Long userId, String address) {
        try {
            String payload = objectMapper.writeValueAsString(
                    Map.of("bookingId", bookingId, "userId", userId, "address", address)
            );
            outboxRepository.save(new OutboxEvent("BOOKING", bookingId, eventType, payload));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize outbox payload for bookingId={}", bookingId, e);
            throw new RuntimeException("Outbox event 직렬화 실패", e);
        }
    }

    private int calculatePrice(LocalDateTime startTime, LocalDateTime endTime, int pricePerHour) {
        long hours = Duration.between(startTime, endTime).toHours();
        if (Duration.between(startTime, endTime).toMinutes() % 60 > 0) hours++;
        return (int) hours * pricePerHour;
    }

    public List<BookingResponseDto> findMyBookings(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return bookingRepository.findByUser_IdOrderByStartTimeDesc(user.getId()).stream()
                .map(BookingResponseDto::new)
                .collect(Collectors.toList());
    }

    @Transactional
    public void cancelBooking(Long bookingId, String userEmail) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new IllegalArgumentException("해당 예약을 찾을 수 없습니다. id=" + bookingId));
        if (!booking.getUser().getEmail().equals(userEmail)) {
            throw new IllegalStateException("예약을 취소할 권한이 없습니다.");
        }
        booking.cancel();

        // 취소 Outbox 이벤트
        saveOutboxEvent("BOOKING_CANCELED", bookingId, booking.getUser().getId(), booking.getParkingSpace().getAddress());
    }

    public List<AvailableTimeSlotDto> getAvailableTimeSlots(Long parkingSpaceId, LocalDateTime startDate, LocalDateTime endDate, int slotDurationHours) {
        parkingSpaceRepository.findById(parkingSpaceId)
                .orElseThrow(() -> new IllegalArgumentException("해당 주차 공간을 찾을 수 없습니다. id=" + parkingSpaceId));

        List<Booking> reservedBookings = bookingRepository
                .findByParkingSpaceIdAndStatusAndStartTimeBeforeAndEndTimeAfter(
                        parkingSpaceId, BookingStatus.RESERVED, endDate, startDate);

        List<TimeBlock> reservedBlocks = reservedBookings.stream()
                .map(b -> new TimeBlock(b.getStartTime(), b.getEndTime()))
                .collect(Collectors.toList());

        List<AvailableTimeSlotDto> availableSlots = new ArrayList<>();
        LocalDateTime current = startDate;

        while (!current.plusHours(slotDurationHours).isAfter(endDate)) {
            LocalDateTime slotEnd = current.plusHours(slotDurationHours);
            TimeBlock slot = new TimeBlock(current, slotEnd);
            boolean available = reservedBlocks.stream().noneMatch(r -> overlaps(r, slot));
            if (available) {
                availableSlots.add(new AvailableTimeSlotDto(current, slotEnd, slotDurationHours * parkingSpaceRepository.findById(parkingSpaceId).get().getPricePerHour()));
            }
            current = current.plusHours(slotDurationHours);
        }
        return availableSlots;
    }

    private boolean overlaps(TimeBlock a, TimeBlock b) {
        return a.startTime.isBefore(b.endTime) && a.endTime.isAfter(b.startTime);
    }

    private static class TimeBlock {
        final LocalDateTime startTime;
        final LocalDateTime endTime;
        TimeBlock(LocalDateTime s, LocalDateTime e) { this.startTime = s; this.endTime = e; }
    }
}
