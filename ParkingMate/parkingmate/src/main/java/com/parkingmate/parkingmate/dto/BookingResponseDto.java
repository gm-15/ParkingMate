package com.parkingmate.parkingmate.dto;

import com.parkingmate.parkingmate.domain.Booking;
import com.parkingmate.parkingmate.domain.BookingStatus;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class BookingResponseDto {
    private Long bookingId;
    private String parkingSpaceAddress;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int totalPrice;
    private BookingStatus status;

    public BookingResponseDto(Booking booking) {
        this.bookingId = booking.getId();
        this.parkingSpaceAddress = booking.getParkingSpace().getAddress();
        this.startTime = booking.getStartTime();
        this.endTime = booking.getEndTime();
        this.totalPrice = booking.getTotalPrice();
        this.status = booking.getStatus();
    }
}