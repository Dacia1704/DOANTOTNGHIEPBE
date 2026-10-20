package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
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
@Table(name = "service_packages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServicePackage {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @Column(nullable = false, length = 30, unique = true)
    String code;

    @Column(nullable = false, length = 100)
    String name;

    @Column(name = "max_listings", nullable = false)
    @Builder.Default
    Integer maxListings = 0;

    @Column(name = "high_priority_quota", nullable = false)
    @Builder.Default
    Integer highPriorityQuota = 0;

    @Column(name = "low_priority_quota", nullable = false)
    @Builder.Default
    Integer lowPriorityQuota = 0;

    @Column(name = "has_stats", nullable = false)
    @Builder.Default
    Boolean hasStats = false;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal price;

    @Column(name = "billing_cycle_days", nullable = false)
    @Builder.Default
    Integer billingCycleDays = 30;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    Boolean isActive = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (maxListings == null) maxListings = 0;
        if (highPriorityQuota == null) highPriorityQuota = 0;
        if (lowPriorityQuota == null) lowPriorityQuota = 0;
        if (hasStats == null) hasStats = false;
        if (billingCycleDays == null) billingCycleDays = 30;
        if (isActive == null) isActive = true;
    }
}