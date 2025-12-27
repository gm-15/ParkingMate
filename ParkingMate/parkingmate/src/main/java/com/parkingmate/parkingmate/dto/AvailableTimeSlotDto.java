package com.parkingmate.parkingmate.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class AvailableTimeSlotDto {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int totalPrice;
}

