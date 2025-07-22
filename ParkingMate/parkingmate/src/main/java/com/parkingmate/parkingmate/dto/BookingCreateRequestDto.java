package com.parkingmate.parkingmate.dto;

import lombok.Getter;
import java.time.LocalDateTime;

@Getter
public class BookingCreateRequestDto {
    private Long parkingSpaceId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}