package com.tamdaptit.rentalmanagement.notification.dto;

import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationEvent(String id, String userId, NotificationType type, String title,
        String body, String referenceType, String referenceId, LocalDateTime createdAt) {}
