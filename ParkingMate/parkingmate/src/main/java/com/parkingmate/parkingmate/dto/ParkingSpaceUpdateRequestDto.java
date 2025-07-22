package com.parkingmate.parkingmate.dto;

import lombok.Getter;

@Getter
public class ParkingSpaceUpdateRequestDto {
    private String address;
    private int pricePerHour;
    private String description;
}