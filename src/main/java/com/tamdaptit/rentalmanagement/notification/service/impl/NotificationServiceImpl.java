package com.tamdaptit.rentalmanagement.notification.service.impl;

import com.tamdaptit.rentalmanagement.notification.service.*;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationResponse;
import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class NotificationServiceImpl implements NotificationService {
    private final JdbcTemplate jdbc;
    private final NotificationRealtime realtime;

    @Override
    public PageResponse<NotificationResponse> list(String userId, boolean unreadOnly, int page, int size) {
        if (page<0 || size<1 || size>100 || page>1_000_000) throw new AppException(ErrorCode.INVALID_REQUEST);
        String filter = " WHERE user_id=?"+(unreadOnly ? " AND is_read=false" : "");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM notifications"+filter,Long.class,userId);
        var rows = jdbc.query("SELECT * FROM notifications"+filter+" ORDER BY created_at DESC,id DESC LIMIT ? OFFSET ?",
                (rs,n) -> new NotificationResponse(rs.getString("id"),NotificationType.valueOf(rs.getString("type")),
                        rs.getString("title"),rs.getString("body"),rs.getString("reference_type"),
                        rs.getString("reference_id"),rs.getBoolean("is_read"),rs.getTimestamp("created_at").toLocalDateTime()),
                userId,size,(long)page*size);
        return PageResponse.<NotificationResponse>builder().currentPage(page).pageSize(size)
                .totalElements(total).totalPages((int)((total+size-1)/size)).data(rows).build();
    }

    @Override
    public long unreadCount(String userId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND is_read=false",Long.class,userId);
    }

    @Transactional
    @Override
    public void markRead(String userId,String id) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE id=? AND user_id=?",Long.class,id,userId)==0)
            throw new AppException(ErrorCode.NOT_FOUND);
        jdbc.update("UPDATE notifications SET is_read=true WHERE id=? AND user_id=?",id,userId);
        realtime.changedAfterCommit(userId);
    }

    @Transactional
    @Override
    public void markAllRead(String userId) {
        jdbc.update("UPDATE notifications SET is_read=true WHERE user_id=? AND is_read=false",userId);
        realtime.changedAfterCommit(userId);
    }
}
