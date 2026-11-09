package com.tamdaptit.rentalmanagement.rental.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;
import com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher;
import com.tamdaptit.rentalmanagement.notification.entity.enums.NotificationType;

/** Audit/outbox is committed atomically with the transition; notifications are delivered in a later transaction. */
@Service @RequiredArgsConstructor
public class RentalEvents {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final NotificationPublisher notifications;
    @Transactional
    public void emit(String actor,String recipient,String id,String type,String transition,String hash) {
        String eventId=UUID.randomUUID().toString();
        var now=LocalDateTime.now(clock).withNano(0);
        jdbc.update("INSERT INTO rental_events(id,actor_id,recipient_id,reference_id,reference_type,transition,pdf_sha256,created_at,delivered_at) VALUES(?,?,?,?,?,?,?,?,?)",
            eventId,actor,recipient,id,type,transition,hash,now,now);
        notifications.enqueue(eventId,recipient,NotificationType.valueOf(type),"Cập nhật thuê phòng",
            label(transition),type,id,now);
    }
    @Transactional
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${app.notifications.relay-delay-ms:2000}")
    public void deliver() {
        var events=jdbc.queryForList("SELECT * FROM rental_events WHERE delivered_at IS NULL ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED");
        for(var e:events) {
            notifications.enqueue(e.get("id").toString(),e.get("recipient_id").toString(),
                NotificationType.valueOf(e.get("reference_type").toString()),"Cập nhật thuê phòng",
                label(e.get("transition").toString()),e.get("reference_type").toString(),
                e.get("reference_id").toString(),e.get("created_at") instanceof LocalDateTime date ? date
                    : ((java.sql.Timestamp)e.get("created_at")).toLocalDateTime());
            jdbc.update("UPDATE rental_events SET delivered_at=? WHERE id=?",LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),e.get("id"));
        }
    }
    private String label(String event) {
        return switch(event) {
            case "REQUEST_CREATED" -> "Có yêu cầu thuê phòng mới đang chờ xử lý.";
            case "REQUEST_ACCEPTED" -> "Chủ trọ đã chấp nhận. Phòng đang được giữ và hợp đồng đang tạo PDF.";
            case "PDF_READY" -> "Hợp đồng PDF đã sẵn sàng. Vui lòng xem và xác nhận trước hạn.";
            case "PDF_FAILED" -> "Chưa thể tạo PDF hợp đồng; hệ thống sẽ thử lại.";
            case "AGREEMENT_ACTIVE" -> "Khách thuê đã xác nhận hợp đồng trong ứng dụng.";
            case "CHECKOUT" -> "Chủ trọ đã xác nhận hoàn tất trả phòng.";
            case "REQUEST_REJECTED" -> "Chủ trọ đã từ chối yêu cầu thuê phòng của bạn.";
            case "REQUEST_CANCELLED" -> "Khách thuê đã hủy yêu cầu thuê phòng.";
            case "REQUEST_EXPIRED" -> "Yêu cầu thuê phòng đã hết hạn.";
            case "CONFIRMATION_TIMEOUT" -> "Hợp đồng đã hết hạn xác nhận. Phòng đã được giải phóng.";
            case "AGREEMENT_DECLINED" -> "Khách thuê đã từ chối hợp đồng.";
            default -> "Trạng thái thuê phòng đã cập nhật: "+event;
        };
    }
}
