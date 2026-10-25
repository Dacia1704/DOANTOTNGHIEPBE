package com.tamdaptit.rentalmanagement.subscription.entity;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "service_subscription_orders")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceSubscriptionOrder {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "package_id", nullable = false)
    private ServicePackage packageEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_package_price_id", nullable = false)
    private ServicePackagePrice priceOption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subscription_id")
    private ServicePackageSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_subscription_id")
    private ServicePackageSubscription previousSubscription;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ServiceSubscriptionOrderType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private ServiceSubscriptionOrderStatus status = ServiceSubscriptionOrderStatus.PENDING_PAYMENT;

    @Column(name = "duration_months", nullable = false)
    private Integer durationMonths;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "immediate_upgrade", nullable = false)
    @Builder.Default
    private Boolean immediateUpgrade = false;

    @Column(name = "upgrade_value", precision = 12, scale = 2)
    private BigDecimal upgradeValue;

    @Column(name = "upgrade_months", precision = 20, scale = 12)
    private BigDecimal upgradeMonths;

    @Column(name = "package_code_snapshot", nullable = false, length = 50)
    private String packageCodeSnapshot;

    @Column(name = "package_name_snapshot", nullable = false, length = 100)
    private String packageNameSnapshot;

    @Column(name = "max_listings_snapshot")
    private Integer maxListingsSnapshot;

    @Column(name = "high_priority_slots_snapshot", nullable = false)
    private Integer highPrioritySlotsSnapshot;

    @Column(name = "low_priority_slots_snapshot", nullable = false)
    private Integer lowPrioritySlotsSnapshot;

    @Column(name = "default_priority_level_snapshot", nullable = false)
    private Integer defaultPriorityLevelSnapshot;

    @Column(name = "has_stats_snapshot", nullable = false)
    private Boolean hasStatsSnapshot;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "effective_at")
    private LocalDateTime effectiveAt;

    @Column(name = "end_at")
    private LocalDateTime endAt;

    @Column(name = "previous_end_at")
    private LocalDateTime previousEndAt;

    @Column(name = "applied_at")
    private LocalDateTime appliedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
