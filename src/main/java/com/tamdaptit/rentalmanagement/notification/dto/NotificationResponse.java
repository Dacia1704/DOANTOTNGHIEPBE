package com.tamdaptit.rentalmanagement.notification.dto;

import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;

public record NotificationResponse(
    String id,
    NotificationType type,
    String title,
    String body,
    String referenceType,
    String referenceId,
    boolean read,
    LocalDateTime createdAt
) {}
