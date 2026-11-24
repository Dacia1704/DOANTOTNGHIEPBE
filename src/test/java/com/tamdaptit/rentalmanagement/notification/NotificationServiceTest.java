package com.tamdaptit.rentalmanagement.notification;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationResponse;
import com.tamdaptit.rentalmanagement.notification.service.NotificationRealtime;
import com.tamdaptit.rentalmanagement.notification.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phần 2: Trao đổi, thông báo & Quản trị bổ trợ - NotificationService")
class NotificationServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private NotificationRealtime realtime;

    private NotificationServiceImpl notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationServiceImpl(jdbc, realtime);
    }

    @Test
    @DisplayName("Lấy số lượng thông báo chưa đọc (unreadCount) của người dùng")
    void testUnreadCount() {
        String userId = "tenant-1";
        when(jdbc.queryForObject(contains("COUNT(*) FROM notifications"), eq(Long.class), eq(userId)))
                .thenReturn(7L);

        long count = notificationService.unreadCount(userId);
        assertThat(count).isEqualTo(7L);
    }

    @Test
    @DisplayName("Lấy unreadCount khi người dùng không có thông báo chưa đọc trả về 0")
    void testUnreadCountZero() {
        String userId = "tenant-zero";
        when(jdbc.queryForObject(contains("COUNT(*) FROM notifications"), eq(Long.class), eq(userId)))
                .thenReturn(0L);

        long count = notificationService.unreadCount(userId);
        assertThat(count).isZero();
    }

    @Test
    @DisplayName("Phân trang thông báo với tham số page âm hoặc size không hợp lệ bị ném INVALID_REQUEST")
    void testInvalidPaginationParameters() {
        assertThatThrownBy(() -> notificationService.list("user-1", false, -1, 10))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> notificationService.list("user-1", false, 0, 0))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> notificationService.list("user-1", false, 0, 101))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> notificationService.list("user-1", false, 1_000_001, 20))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Lấy danh sách thông báo thành công và tính toán PageResponse")
    void testListNotificationsSuccess() {
        String userId = "tenant-1";
        when(jdbc.queryForObject(contains("COUNT(*) FROM notifications"), eq(Long.class), eq(userId)))
                .thenReturn(15L);
        when(jdbc.query(contains("SELECT * FROM notifications"), any(RowMapper.class), eq(userId), eq(10), eq(0L)))
                .thenReturn(Collections.emptyList());

        PageResponse<NotificationResponse> result = notificationService.list(userId, false, 0, 10);

        assertThat(result.getCurrentPage()).isZero();
        assertThat(result.getPageSize()).isEqualTo(10);
        assertThat(result.getTotalElements()).isEqualTo(15L);
        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("Lọc danh sách thông báo chỉ lấy chưa đọc (unreadOnly=true)")
    void testListNotificationsUnreadOnly() {
        String userId = "tenant-1";
        when(jdbc.queryForObject(contains("AND is_read=false"), eq(Long.class), eq(userId)))
                .thenReturn(3L);
        when(jdbc.query(contains("AND is_read=false"), any(RowMapper.class), eq(userId), eq(10), eq(0L)))
                .thenReturn(Collections.emptyList());

        PageResponse<NotificationResponse> result = notificationService.list(userId, true, 0, 10);

        assertThat(result.getTotalElements()).isEqualTo(3L);
        assertThat(result.getTotalPages()).isEqualTo(1);
    }

    @Test
    @DisplayName("Đánh dấu đọc thông báo không tồn tại ném lỗi NOT_FOUND")
    void testMarkReadNotFound() {
        String userId = "tenant-1";
        String notificationId = "notif-999";
        when(jdbc.queryForObject(contains("COUNT(*) FROM notifications WHERE id=?"), eq(Long.class), eq(notificationId), eq(userId)))
                .thenReturn(0L);

        assertThatThrownBy(() -> notificationService.markRead(userId, notificationId))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    @Test
    @DisplayName("Đánh dấu đọc một thông báo cập nhật is_read và gửi tín hiệu realtime")
    void testMarkReadSuccess() {
        String userId = "tenant-1";
        String notificationId = "notif-1";
        when(jdbc.queryForObject(contains("COUNT(*) FROM notifications WHERE id=?"), eq(Long.class), eq(notificationId), eq(userId)))
                .thenReturn(1L);

        notificationService.markRead(userId, notificationId);

        verify(jdbc, times(1)).update(
                contains("UPDATE notifications SET is_read=true WHERE id=? AND user_id=?"),
                eq(notificationId), eq(userId)
        );
        verify(realtime, times(1)).changedAfterCommit(userId);
    }

    @Test
    @DisplayName("Đánh dấu đọc tất cả thông báo của người dùng")
    void testMarkAllReadSuccess() {
        String userId = "tenant-1";

        notificationService.markAllRead(userId);

        verify(jdbc, times(1)).update(
                contains("UPDATE notifications SET is_read=true WHERE user_id=? AND is_read=false"),
                eq(userId)
        );
        verify(realtime, times(1)).changedAfterCommit(userId);
    }
}
