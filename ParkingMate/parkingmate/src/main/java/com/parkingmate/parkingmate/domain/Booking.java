package com.parkingmate.parkingmate.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "booking_id")
    private Long id;

    // 예약한 사용자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    // 예약된 주차 공간
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parking_space_id")
    private ParkingSpace parkingSpace;

    @Column(nullable = false)
    private LocalDateTime startTime; // 예약 시작 시간

    @Column(nullable = false)
    private LocalDateTime endTime; // 예약 종료 시간

    @Column(nullable = false)
    private int totalPrice; // 총 예약 금액

    @Enumerated(EnumType.STRING) // Enum 타입을 DB에 문자열로 저장
    @Column(nullable = false)
    private BookingStatus status; // 예약 상태 [RESERVED, CANCELED]


    public Booking(User user, ParkingSpace parkingSpace, LocalDateTime startTime, LocalDateTime endTime, int totalPrice) {
        this.user = user;
        this.parkingSpace = parkingSpace;
        this.startTime = startTime;
        this.endTime = endTime;
        this.totalPrice = totalPrice;
        this.status = BookingStatus.RESERVED; // 예약 생성 시 기본 상태는 'RESERVED'
    }

    public void cancel() {
        this.status = BookingStatus.CANCELED;
    }
}