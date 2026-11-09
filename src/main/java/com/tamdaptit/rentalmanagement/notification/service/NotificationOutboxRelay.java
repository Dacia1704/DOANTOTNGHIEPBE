package com.tamdaptit.rentalmanagement.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationEvent;
import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.concurrent.TimeUnit;
import static com.tamdaptit.rentalmanagement.notification.config.NotificationRabbitConfig.*;

@Service
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name="app.notifications.relay-enabled", havingValue="true", matchIfMissing=true)
public class NotificationOutboxRelay {
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Scheduled(fixedDelayString="${app.notifications.relay-delay-ms:2000}")
    @Transactional
    public void publishPending() {
        var now = LocalDateTime.now(clock).withNano(0);
        var events = jdbc.queryForList("""
            SELECT * FROM notification_outbox WHERE published_at IS NULL AND next_attempt_at<=?
            ORDER BY created_at,id LIMIT 20 FOR UPDATE SKIP LOCKED
            """, now);
        for (var row : events) {
            String id = (String) row.get("id");
            try {
                var event = new NotificationEvent(id, (String)row.get("user_id"),
                        NotificationType.valueOf((String)row.get("type")), (String)row.get("title"),
                        (String)row.get("body"), (String)row.get("reference_type"), (String)row.get("reference_id"),
                        row.get("created_at") instanceof LocalDateTime date ? date
                            : ((java.sql.Timestamp)row.get("created_at")).toLocalDateTime());
                var message = MessageBuilder.withBody(mapper.writeValueAsBytes(event))
                        .setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(id)
                        .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
                var correlation = new CorrelationData(id);
                rabbit.send(EXCHANGE, ROUTING_KEY, message, correlation);
                var confirm = correlation.getFuture().get(3, TimeUnit.SECONDS);
                if (!confirm.ack() || correlation.getReturned()!=null) {
                    throw new IllegalStateException("Notification was not accepted by its queue");
                }
                jdbc.update("UPDATE notification_outbox SET published_at=? WHERE id=?",now,id);
            } catch (Exception ex) {
                int attempts = ((Number)row.get("attempts")).intValue()+1;
                jdbc.update("UPDATE notification_outbox SET attempts=?,next_attempt_at=? WHERE id=?",
                        attempts,now.plusSeconds(Math.min(300, 1L << Math.min(attempts,8))),id);
                log.warn("Notification {} will retry ({})",id,ex.getClass().getSimpleName());
                if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
                // Bound an outage to one connection/confirm timeout per scheduled pass.
                break;
            }
        }
    }

    @Scheduled(fixedDelayString="${app.notifications.cleanup-delay-ms:3600000}")
    public void cleanupPublished() {
        jdbc.update("DELETE FROM notification_outbox WHERE published_at<? LIMIT 1000",
                LocalDateTime.now(clock).minusDays(7));
    }
}
