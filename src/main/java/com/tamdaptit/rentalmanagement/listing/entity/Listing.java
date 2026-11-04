package com.tamdaptit.rentalmanagement.listing.entity;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionPeriod;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

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
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(unique = true, length = 255)
    private String slug;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ListingStatus status = ListingStatus.PENDING;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "pending_expires_at")
    private LocalDateTime pendingExpiresAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "initial_review", nullable = false)
    @Builder.Default
    private Boolean initialReview = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_subscription_period_id")
    private ServiceSubscriptionPeriod serviceSubscriptionPeriod;

    @Column(name = "package_priority_level", nullable = false)
    @Builder.Default
    private Integer packagePriorityLevel = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_quota_period_id")
    private ServiceSubscriptionPeriod postingQuotaPeriod;

    @Column(name = "posting_quota_consumed", nullable = false)
    @Builder.Default
    private Boolean postingQuotaConsumed = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "priority_quota_period_id")
    private ServiceSubscriptionPeriod priorityQuotaPeriod;

    // Bit 1 = low priority consumed; bit 2 = high priority consumed, even after deselection.
    @Column(name = "consumed_priority_levels", nullable = false)
    @Builder.Default
    private Integer consumedPriorityLevels = 0;

    @Column(name = "priority_level")
    @Builder.Default
    private Integer priorityLevel = 0;

    @Column(name = "view_count")
    @Builder.Default
    private Integer viewCount = 0;

    @Column(name = "contact_count")
    @Builder.Default
    private Integer contactCount = 0;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
