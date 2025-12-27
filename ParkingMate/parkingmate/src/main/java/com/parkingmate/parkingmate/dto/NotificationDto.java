package com.parkingmate.parkingmate.dto;

import com.parkingmate.parkingmate.domain.Notification;
import com.parkingmate.parkingmate.domain.NotificationType;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class NotificationDto {
    private Long id;
    private String title;
    private String message;
    private NotificationType type;
    private boolean isRead;
    private LocalDateTime createdAt;

    public NotificationDto(Notification notification) {
        this.id = notification.getId();
        this.title = notification.getTitle();
        this.message = notification.getMessage();
        this.type = notification.getType();
        this.isRead = notification.isRead();
        this.createdAt = notification.getCreatedAt();
    }
}

