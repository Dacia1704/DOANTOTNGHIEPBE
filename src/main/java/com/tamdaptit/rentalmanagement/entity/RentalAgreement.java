package com.tamdaptit.rentalmanagement.entity;

import com.tamdaptit.rentalmanagement.entity.enums.CreatedByRole;
import com.tamdaptit.rentalmanagement.entity.enums.RentalAgreementStatus;
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
import java.time.LocalDate;
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
 * Hop dong thue. SNAPSHOT gia va dieu khan tai thoi diem ky.
 */
@Entity
@Table(name = "rental_agreements")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalAgreement {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @Column(name = "agreement_code", nullable = false, length = 30, unique = true)
    String agreementCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_request_id")
    RentalRequest rentalRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    User landlord;

    /** Snapshot gia thue */
    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal price;

    /** Snapshot tien coc */
    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    BigDecimal deposit = BigDecimal.ZERO;

    /** Snapshot gia dien */
    @Column(name = "electricity_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    BigDecimal electricityPrice = BigDecimal.ZERO;

    /** Snapshot gia nuoc */
    @Column(name = "water_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    BigDecimal waterPrice = BigDecimal.ZERO;

    /** Snapshot phi dich vu */
    @Column(name = "service_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    BigDecimal serviceFee = BigDecimal.ZERO;

    /** Chu ky thanh toan (thang) */
    @Column(name = "payment_cycle_months", nullable = false)
    @Builder.Default
    Integer paymentCycleMonths = 1;

    @Column(name = "start_date", nullable = false)
    LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    LocalDate endDate;

    @Column(name = "terms_text", columnDefinition = "TEXT")
    String termsText;

    @Column(name = "pdf_url", length = 500)
    String pdfUrl;

    @Column(name = "pdf_public_id", length = 255)
    String pdfPublicId;

    /** DRAFT / PENDING_CONFIRMATION / ACTIVE / EXPIRED / TERMINATED / CANCELLED */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    RentalAgreementStatus status = RentalAgreementStatus.DRAFT;

    /** LANDLORD / TENANT */
    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", length = 20)
    CreatedByRole createdByRole;

    @Column(name = "landlord_confirmed_at")
    LocalDateTime landlordConfirmedAt;

    @Column(name = "tenant_confirmed_at")
    LocalDateTime tenantConfirmedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = RentalAgreementStatus.DRAFT;
        if (paymentCycleMonths == null) paymentCycleMonths = 1;
        if (deposit == null) deposit = BigDecimal.ZERO;
        if (electricityPrice == null) electricityPrice = BigDecimal.ZERO;
        if (waterPrice == null) waterPrice = BigDecimal.ZERO;
        if (serviceFee == null) serviceFee = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}