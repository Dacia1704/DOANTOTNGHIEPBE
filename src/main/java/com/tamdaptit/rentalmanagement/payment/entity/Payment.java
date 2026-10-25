package com.tamdaptit.rentalmanagement.payment.entity;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentMethod;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentType;
import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionOrder;
import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackageSubscription;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Thanh toan cua landlord voi he thong.
 * Moi lan thu thanh toan tham chieu dung mot don goi dich vu hoac dang ky Boost.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Payment {


    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    PaymentType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_subscription_order_id")
    private ServiceSubscriptionOrder serviceSubscriptionOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boost_subscription_id")
    private BoostServicePackageSubscription boostSubscription;

    @Column(name = "merchant_order_id", nullable = false, unique = true, length = 100)
    private String merchantOrderId;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "vnp_create_date")
    private LocalDateTime vnPayCreateDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 30)
    PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider_txn_id", length = 255)
    private String providerTxnId;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    @Column(name = "requires_review", nullable = false)
    @Builder.Default
    private Boolean requiresReview = false;

    @Column(name = "review_reason", length = 255)
    private String reviewReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
