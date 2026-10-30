package com.tamdaptit.rentalmanagement.subscription.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.UuidGenerator;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "service_subscription_periods")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServiceSubscriptionPeriod {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "subscription_id", nullable = false)
    private ServicePackageSubscription subscription;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private ServiceSubscriptionOrder order;

    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(name = "entitlement_value", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal entitlementValue = BigDecimal.ZERO;

    @Column(name = "billing_months", nullable = false, precision = 20, scale = 12)
    @Builder.Default
    private BigDecimal billingMonths = BigDecimal.ONE;

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

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
