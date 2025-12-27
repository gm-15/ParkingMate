package com.parkingmate.parkingmate.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class BookingCreateRequestDto {
    @NotNull(message = "주차 공간 ID는 필수입니다.")
    private Long parkingSpaceId;

    @NotNull(message = "시작 시간은 필수입니다.")
    @Future(message = "시작 시간은 현재 시간 이후여야 합니다.")
    private LocalDateTime startTime;

    @NotNull(message = "종료 시간은 필수입니다.")
    @Future(message = "종료 시간은 현재 시간 이후여야 합니다.")
    private LocalDateTime endTime;
}