package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 * Tenant danh gia phong sau khi thue.
 * rental_agreement_id UNIQUE: mot hop dong chi danh gia phong mot lan.
 */
@Entity
@Table(
    name = "room_reviews",
    uniqueConstraints = @UniqueConstraint(
        name = "UX_rr_agreement",
        columnNames = "rental_agreement_id"
    )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomReview {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_agreement_id", nullable = false)
    RentalAgreement rentalAgreement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    Room room;

    /** Diem tu 1 den 5 */
    @Column(nullable = false)
    Integer rating;

    @Column(columnDefinition = "TEXT")
    String comment;

    @Column(name = "landlord_reply", columnDefinition = "TEXT")
    String landlordReply;

    @Column(name = "is_hidden", nullable = false)
    @Builder.Default
    Boolean isHidden = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hidden_by")
    User hiddenBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isHidden == null) isHidden = false;
    }
}