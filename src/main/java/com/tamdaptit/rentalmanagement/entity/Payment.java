package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.PaymentMethod;
import com.tamdaptit.rentalmanagement.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.entity.enums.PaymentType;
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

/**
 * Thanh toan cua landlord voi he thong.
 * reference_id la POLYMORPHIC (khong co FK vat ly).
 * - type = SERVICE_PACKAGE  -> reference_id = service_package_subscription.id
 * - type = BOOST_PACKAGE    -> reference_id = boost_service_package_subscription.id
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
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    User landlord;

    /** SERVICE_PACKAGE / BOOST_PACKAGE */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    PaymentType type;

    /** ID doi tuong duoc thanh toan (polymorphic) */
    @Column(name = "reference_id", nullable = false, columnDefinition = "CHAR(36)")
    String referenceId;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal amount;

    /** VNPAY / MOMO / BANK_TRANSFER */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    PaymentMethod paymentMethod;

    /** PENDING / SUCCESS / FAILED / REFUNDED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "provider_txn_id", length = 255)
    String providerTxnId;

    @Column(name = "paid_at")
    LocalDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = PaymentStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}