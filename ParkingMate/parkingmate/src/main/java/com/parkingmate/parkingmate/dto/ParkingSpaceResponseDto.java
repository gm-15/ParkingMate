package com.parkingmate.parkingmate.dto;

import com.parkingmate.parkingmate.domain.ParkingSpace;
import lombok.Getter;

@Getter
public class ParkingSpaceResponseDto {
    private Long id;
    private String address;
    private Double latitude;
    private Double longitude;
    private int pricePerHour;
    private String description;
    private String ownerName;
    private String imageUrls;

    public ParkingSpaceResponseDto(ParkingSpace parkingSpace) {
        this.id = parkingSpace.getId();
        this.address = parkingSpace.getAddress();
        this.latitude = parkingSpace.getLatitude();
        this.longitude = parkingSpace.getLongitude();
        this.pricePerHour = parkingSpace.getPricePerHour();
        this.description = parkingSpace.getDescription();
        this.ownerName = parkingSpace.getUser().getName();
        this.imageUrls = parkingSpace.getImageUrls();
    }
}