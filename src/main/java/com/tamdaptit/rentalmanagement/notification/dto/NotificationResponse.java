package com.tamdaptit.rentalmanagement.notification.dto;

import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import java.time.LocalDateTime;

public record NotificationResponse(String id, NotificationType type, String title, String body,
        String referenceType, String referenceId, boolean read, LocalDateTime createdAt) {}
