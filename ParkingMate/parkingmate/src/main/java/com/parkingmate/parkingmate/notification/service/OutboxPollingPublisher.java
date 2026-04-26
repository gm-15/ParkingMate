package com.parkingmate.parkingmate.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.parkingmate.parkingmate.notification.domain.NotificationType;
import com.parkingmate.parkingmate.notification.domain.OutboxEvent;
import com.parkingmate.parkingmate.notification.domain.OutboxEventStatus;
import com.parkingmate.parkingmate.notification.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

/**
 * Transactional Outbox Pattern — Polling Publisher
 *
 * 역할: outbox_event 테이블에서 PENDING 이벤트를 주기적으로 읽어
 *       NotificationService를 통해 알림을 발송.
 *
 * 트랜잭션 전략:
 *   - 이벤트 목록 조회: 읽기 전용 트랜잭션
 *   - 각 이벤트 처리: REQUIRES_NEW (독립 트랜잭션)
 *     → 한 이벤트 실패가 다른 이벤트에 영향 없음
 *
 * 내결함성:
 *   - 처리 실패 시 status = FAILED (예약은 유지)
 *   - 다음 주기에 FAILED → 재처리 로직 추가 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxPollingPublisher {

    private final OutboxRepository outboxRepository;
    private final NotificationService notificationService;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 5000) // 5초마다 실행
    public void processOutboxEvents() {
        // 읽기 전용 트랜잭션으로 PENDING 이벤트 조회
        TransactionTemplate readTx = new TransactionTemplate(transactionManager);
        readTx.setReadOnly(true);

        List<OutboxEvent> pendingEvents = readTx.execute(status ->
                outboxRepository.findTop10ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING)
        );

        if (pendingEvents == null || pendingEvents.isEmpty()) return;

        log.debug("Outbox polling: {} pending events found", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            processInNewTransaction(event);
        }
    }

    private void processInNewTransaction(OutboxEvent event) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        tx.execute(status -> {
            try {
                dispatch(event);
                event.markProcessed();
                log.info("Outbox event processed: id={}, type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.error("Outbox event failed: id={}, type={}, error={}", event.getId(), event.getEventType(), e.getMessage());
                event.markFailed();
            }
            outboxRepository.save(event);
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    private void dispatch(OutboxEvent event) throws Exception {
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
        Long userId = ((Number) payload.get("userId")).longValue();
        String address = (String) payload.get("address");

        switch (event.getEventType()) {
            case "BOOKING_CREATED" -> notificationService.createNotification(
                    userId,
                    "예약이 완료되었습니다",
                    address + " 예약이 완료되었습니다.",
                    NotificationType.BOOKING_CREATED
            );
            case "BOOKING_CANCELED" -> notificationService.createNotification(
                    userId,
                    "예약이 취소되었습니다",
                    address + " 예약이 취소되었습니다.",
                    NotificationType.BOOKING_CANCELED
            );
            default -> log.warn("Unknown outbox event type: {}", event.getEventType());
        }
    }
}
