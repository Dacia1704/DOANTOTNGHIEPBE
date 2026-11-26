package com.tamdaptit.rentalmanagement.notification.controller;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationResponse;
import com.tamdaptit.rentalmanagement.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Thông báo cá nhân qua RabbitMQ")
public class NotificationController {
    private final NotificationService service;

    @GetMapping
    @Operation(summary = "Danh sách thông báo của tài khoản đang đăng nhập, mới nhất trước")
    public ApiResponse<PageResponse<NotificationResponse>> list(
        Authentication auth,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ApiResponse.success(service.list(auth.getName(), unreadOnly, page, size));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> unreadCount(Authentication auth) {
        return ApiResponse.success(service.unreadCount(auth.getName()));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<Void> read(Authentication auth, @PathVariable String id) {
        service.markRead(auth.getName(), id);
        return ApiResponse.success(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAll(Authentication auth) {
        service.markAllRead(auth.getName());
        return ApiResponse.success(null);
    }
}
