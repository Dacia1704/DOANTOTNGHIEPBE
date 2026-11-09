package com.tamdaptit.rentalmanagement.notification.service;

import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationPublisher {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Transactional
    public void enqueue(String userId, NotificationType type, String title, String body,
            String referenceType, String referenceId) {
        enqueue(UUID.randomUUID().toString(), userId, type, title, body, referenceType, referenceId,
                LocalDateTime.now(clock).withNano(0));
    }

    @Transactional
    public void enqueue(String id, String userId, NotificationType type, String title, String body,
            String referenceType, String referenceId, LocalDateTime createdAt) {
        jdbc.update("""
            INSERT INTO notification_outbox
                (id,user_id,type,title,body,reference_type,reference_id,created_at,next_attempt_at)
            VALUES(?,?,?,?,?,?,?,?,?)
            ON DUPLICATE KEY UPDATE id=id
            """, id,userId,type.name(),title,body,referenceType,referenceId,createdAt,createdAt);
    }
}
