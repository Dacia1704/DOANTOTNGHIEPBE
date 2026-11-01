package com.tamdaptit.rentalmanagement.rental.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.UUID;

/** Audit/outbox is committed atomically with the transition; notifications are delivered in a later transaction. */
@Service @RequiredArgsConstructor
public class RentalEvents {
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public void emit(String actor,String recipient,String id,String type,String transition,String hash) {
        jdbc.update("INSERT INTO rental_events(id,actor_id,recipient_id,reference_id,reference_type,transition,pdf_sha256,created_at) VALUES(?,?,?,?,?,?,?,?)",
            UUID.randomUUID().toString(),actor,recipient,id,type,transition,hash,LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    }
    @Transactional
    public void deliver() {
        var events=jdbc.queryForList("SELECT * FROM rental_events WHERE delivered_at IS NULL ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED");
        for(var e:events) {
            // SELECT protects against deleted legacy users without a foreign-key rollback loop.
            jdbc.update("""
                INSERT INTO notifications(id,user_id,type,title,body,reference_type,reference_id,is_read)
                SELECT ?,id,?,?,?,?,?,false FROM users WHERE id=?
                """,e.get("id"),e.get("reference_type"),"Cập nhật thuê phòng",label(e.get("transition").toString()),
                e.get("reference_type"),e.get("reference_id"),e.get("recipient_id"));
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
            default -> "Trạng thái thuê phòng đã cập nhật: "+event;
        };
    }
}
