package com.tamdaptit.rentalmanagement.notification.dto;

import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;

public record NotificationEvent(
    String id,
    String userId,
    NotificationType type,
    String title,
    String body,
    String referenceType,
    String referenceId,
    LocalDateTime createdAt
) {}
