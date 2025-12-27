package com.parkingmate.parkingmate.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParkingSpaceCreateRequestDto {
    @NotBlank(message = "주소는 필수입니다.")
    private String address;

    private Double latitude;  // 위도 (선택사항)
    private Double longitude; // 경도 (선택사항)

    @NotNull(message = "시간당 가격은 필수입니다.")
    @Min(value = 0, message = "시간당 가격은 0원 이상이어야 합니다.")
    private Integer pricePerHour;

    private String description;
    
    private String imageUrls; // 이미지 URL들 (쉼표로 구분)
}