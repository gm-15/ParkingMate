package com.parkingmate.parkingmate.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ParkingSpaceSearchRequest {
    private String address;        // 주소 검색어
    private String sortBy;         // 정렬 기준: price_asc, price_desc, latest
    private Integer page = 0;      // 페이지 번호 (0부터 시작)
    private Integer size = 10;     // 페이지 크기
    
    public boolean hasAddress() {
        return address != null && !address.trim().isEmpty();
    }
}

