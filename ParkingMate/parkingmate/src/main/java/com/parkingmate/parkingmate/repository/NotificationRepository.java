package com.parkingmate.parkingmate.repository;

import com.parkingmate.parkingmate.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    
    // 사용자의 알림 목록 조회 (최신순)
    List<Notification> findByUser_IdOrderByCreatedAtDesc(Long userId);
    
    // 읽지 않은 알림 개수 조회
    long countByUser_IdAndIsReadFalse(Long userId);
    
    // 사용자의 모든 알림을 읽음 처리
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true WHERE n.user.id = :userId")
    void markAllAsReadByUserId(@Param("userId") Long userId);
}

