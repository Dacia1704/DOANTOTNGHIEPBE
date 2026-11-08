package com.tamdaptit.rentalmanagement.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tamdaptit.rentalmanagement.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static com.tamdaptit.rentalmanagement.notification.config.NotificationRabbitConfig.QUEUE;

@Service
@RequiredArgsConstructor
public class NotificationConsumer {
    private final JdbcTemplate jdbc;
    private final NotificationRealtime realtime;
    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @RabbitListener(queues=QUEUE)
    @Transactional
    public void receive(Message message) throws java.io.IOException {
        var event = mapper.readValue(message.getBody(), NotificationEvent.class);
        if (event.id()==null || event.userId()==null || event.type()==null || event.title()==null
                || event.body()==null || event.createdAt()==null || event.id().length()>36
                || event.title().length()>255 || event.userId().length()>36
                || (event.referenceType()!=null && event.referenceType().length()>50)
                || (event.referenceId()!=null && event.referenceId().length()>36))
            throw new IllegalArgumentException("Invalid notification event");
        // A redelivered event retains its ID. INSERT IGNORE returns zero for an existing primary key.
        // The SELECT also makes events for deleted accounts safe to acknowledge.
        int inserted = jdbc.update("""
            INSERT IGNORE INTO notifications(id,user_id,type,title,body,reference_type,reference_id,is_read,created_at)
            SELECT ?,id,?,?,?,?,?,false,? FROM users WHERE id=?
            """,event.id(),event.type().name(),event.title(),event.body(),event.referenceType(),
                event.referenceId(),event.createdAt(),event.userId());
        if (inserted>0) realtime.changedAfterCommit(event.userId());
    }
}
