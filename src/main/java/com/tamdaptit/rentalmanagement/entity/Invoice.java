package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.InvoiceType;
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
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Invoice {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    Payment payment;

    /** SERVICE_PACKAGE / BOOST_PACKAGE */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    InvoiceType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_package_id")
    ServicePackage servicePackage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "boost_service_package_id")
    BoostServicePackage boostServicePackage;

    @Column(name = "invoice_no", nullable = false, length = 50, unique = true)
    String invoiceNo;

    @Column(name = "tax_amount", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    BigDecimal totalAmount;

    @Column(name = "buyer_name", nullable = false, length = 100)
    String buyerName;

    @Column(name = "buyer_tax_code", length = 20)
    String buyerTaxCode;

    @Column(name = "issued_at", nullable = false)
    LocalDateTime issuedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (taxAmount == null) taxAmount = BigDecimal.ZERO;
    }
}