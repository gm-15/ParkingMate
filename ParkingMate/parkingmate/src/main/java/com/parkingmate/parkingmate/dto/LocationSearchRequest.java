package com.parkingmate.parkingmate.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LocationSearchRequest {
    private Double latitude;      // 중심 위도
    private Double longitude;     // 중심 경도
    private Double radiusKm;      // 검색 반경 (킬로미터), 기본값: 5km
    private String sortBy;        // 정렬 기준: price_asc, price_desc, latest, distance
    private Integer page = 0;
    private Integer size = 10;
}

