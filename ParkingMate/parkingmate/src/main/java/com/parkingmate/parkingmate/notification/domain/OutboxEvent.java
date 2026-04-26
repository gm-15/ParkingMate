package com.parkingmate.parkingmate.notification.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Transactional Outbox Pattern — 이벤트 기록 테이블
 *
 * 역할: 예약 트랜잭션(T1)과 동일한 커밋 안에 이벤트를 기록.
 *       OutboxPollingPublisher(T2)가 별도 트랜잭션으로 알림을 처리.
 *
 * 보장: booking INSERT와 outbox_event INSERT가 원자적으로 커밋됨.
 *       프로세스 다운 후 재시작 시에도 미처리 이벤트를 재처리 가능.
 */
@Entity
@Table(
    name = "outbox_event",
    indexes = @Index(name = "idx_outbox_status_created", columnList = "status, created_at")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String aggregateType; // 도메인 ("BOOKING")

    @Column(nullable = false)
    private Long aggregateId;     // booking ID

    @Column(nullable = false)
    private String eventType;     // "BOOKING_CREATED", "BOOKING_CANCELED"

    @Column(columnDefinition = "TEXT")
    private String payload;       // JSON {"userId": 1, "address": "..."}

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime processedAt;

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = OutboxEventStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    public void markProcessed() {
        this.status = OutboxEventStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = OutboxEventStatus.FAILED;
    }
}
