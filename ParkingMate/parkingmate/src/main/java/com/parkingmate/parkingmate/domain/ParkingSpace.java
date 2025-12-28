package com.parkingmate.parkingmate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ParkingSpace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "parking_space_id")
    private Long id;

    // 주차 공간을 등록한 소유자 (User와 연결)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String address; // 주소

    // 위치 정보 (위도, 경도)

    private Double latitude; // 위도


    private Double longitude; // 경도

    @Column(nullable = false)
    private int pricePerHour; // 시간당 가격

    private String description; // 상세 설명

    @Column(length = 1000)
    private String imageUrls; // 이미지 URL들 (쉼표로 구분)

    // 생성자
    public ParkingSpace(User user, String address, int pricePerHour, String description) {
        this.user = user;
        this.address = address;
        this.pricePerHour = pricePerHour;
        this.description = description;
    }
}