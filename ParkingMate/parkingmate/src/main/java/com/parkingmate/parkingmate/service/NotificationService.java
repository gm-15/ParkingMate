package com.parkingmate.parkingmate.service;

import com.parkingmate.parkingmate.domain.*;
import com.parkingmate.parkingmate.dto.NotificationDto;
import com.parkingmate.parkingmate.repository.NotificationRepository;
import com.parkingmate.parkingmate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    /**
     * 알림 생성
     */
    @Transactional
    public void createNotification(Long userId, String title, String message, NotificationType type) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다. id=" + userId));

        Notification notification = new Notification(user, title, message, type);
        notificationRepository.save(notification);
        
        log.info("Notification created: userId={}, type={}, title={}", userId, type, title);
        
        // 실제 운영 환경에서는 WebSocket이나 SSE를 통해 실시간 알림 전송
        // sendRealTimeNotification(userId, notification);
    }

    /**
     * 사용자의 알림 목록 조회
     */
    public List<NotificationDto> getUserNotifications(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return notificationRepository.findByUser_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(NotificationDto::new)
                .collect(Collectors.toList());
    }

    /**
     * 읽지 않은 알림 개수 조회
     */
    public long getUnreadCount(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        return notificationRepository.countByUser_IdAndIsReadFalse(user.getId());
    }

    /**
     * 알림을 읽음 처리
     */
    @Transactional
    public void markAsRead(Long notificationId, String userEmail) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("알림을 찾을 수 없습니다. id=" + notificationId));

        if (!notification.getUser().getEmail().equals(userEmail)) {
            throw new IllegalStateException("알림을 읽을 권한이 없습니다.");
        }

        notification.markAsRead();
    }

    /**
     * 모든 알림을 읽음 처리
     */
    @Transactional
    public void markAllAsRead(String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));

        notificationRepository.markAllAsReadByUserId(user.getId());
    }

    /**
     * 예약 생성 알림
     */
    @Transactional
    public void notifyBookingCreated(Long userId, String parkingSpaceAddress, LocalDateTime startTime) {
        String title = "예약이 완료되었습니다";
        String message = String.format("%s의 예약이 완료되었습니다. 시작 시간: %s", 
                parkingSpaceAddress, startTime.toString());
        createNotification(userId, title, message, NotificationType.BOOKING_CREATED);
    }

    /**
     * 예약 취소 알림
     */
    @Transactional
    public void notifyBookingCanceled(Long userId, String parkingSpaceAddress) {
        String title = "예약이 취소되었습니다";
        String message = String.format("%s의 예약이 취소되었습니다.", parkingSpaceAddress);
        createNotification(userId, title, message, NotificationType.BOOKING_CANCELED);
    }
}

