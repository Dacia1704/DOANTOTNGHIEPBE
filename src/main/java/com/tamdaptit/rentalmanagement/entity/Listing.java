package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.ListingStatus;
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
import jakarta.persistence.PreUpdate;
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

@Entity
@Table(name = "listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Listing {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    Room room;

    @Column(nullable = false, length = 255)
    String title;

    @Column(nullable = false, length = 255)
    String slug;

    @Column(columnDefinition = "TEXT")
    String description;

    /** PENDING / APPROVED / REJECTED / HIDDEN / EXPIRED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    ListingStatus status = ListingStatus.PENDING;

    @Column(name = "rejection_reason", length = 500)
    String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    User approvedBy;

    @Column(name = "approved_at")
    LocalDateTime approvedAt;

    @Column(name = "priority_level", nullable = false)
    @Builder.Default
    Integer priorityLevel = 0;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    Long viewCount = 0L;

    @Column(name = "contact_count", nullable = false)
    @Builder.Default
    Long contactCount = 0L;

    @Column(name = "expires_at")
    LocalDateTime expiresAt;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = ListingStatus.PENDING;
        if (viewCount == null) viewCount = 0L;
        if (contactCount == null) contactCount = 0L;
        if (priorityLevel == null) priorityLevel = 0;
        if (isDeleted == null) isDeleted = false;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}