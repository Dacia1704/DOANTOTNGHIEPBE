package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.SubscriptionStatus;
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
@Table(name = "boost_service_package_subscriptions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class BoostServicePackageSubscription {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    User landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "listing_id", nullable = false)
    Listing listing;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boost_package_id", nullable = false)
    BoostServicePackage boostPackage;

    @Column(name = "start_at", nullable = false)
    LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    LocalDateTime endAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    SubscriptionStatus status = SubscriptionStatus.ACTIVE;

    /** Gia thuc te tai thoi diem mua */
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal price;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = SubscriptionStatus.ACTIVE;
    }
}