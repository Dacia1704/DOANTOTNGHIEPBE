package com.tamdaptit.rentalmanagement.notification.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationResponse;

public interface NotificationService {
    PageResponse<NotificationResponse> list(String userId, boolean unreadOnly, int page, int size);
    long unreadCount(String userId);
    void markRead(String userId, String id);
    void markAllRead(String userId);
}
