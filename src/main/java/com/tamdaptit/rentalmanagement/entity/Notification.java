package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.NotificationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;

/**
 * Thong bao gui toi nguoi dung.
 * reference_type + reference_id la POLYMORPHIC (khong co FK vat ly).
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Notification {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    User user;

    /** SYSTEM / RENTAL_REQUEST / RENTAL_AGREEMENT / REPORT / CHAT */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    NotificationType type;

    @Column(nullable = false, length = 255)
    String title;

    @Column(columnDefinition = "TEXT")
    String body;

    /** Loai entity lien quan (VD: RENTAL_REQUEST, REPORT) */
    @Column(name = "reference_type", length = 50)
    String referenceType;

    /** ID entity lien quan (polymorphic) */
    @Column(name = "reference_id", columnDefinition = "CHAR(36)")
    String referenceId;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    Boolean isRead = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isRead == null) isRead = false;
    }
}