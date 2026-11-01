package com.tamdaptit.rentalmanagement.rental.entity;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.rental.entity.enums.CreatedByRole;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import com.tamdaptit.rentalmanagement.room.entity.Room;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

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
public class RentalAgreement {

    @Column(name = "confirmation_expires_at")
    private LocalDateTime confirmationExpiresAt;
    @Enumerated(EnumType.STRING)
    @Column(name = "pdf_status", nullable = false)
    @Builder.Default
    private com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus pdfStatus = com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus.PENDING;
    @Column(name = "pdf_error", length = 500)
    private String pdfError;
    @Column(name = "pdf_generated_at")
    private LocalDateTime pdfGeneratedAt;
    @Column(name = "pdf_sha256", length = 64)
    private String pdfSha256;
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.JSON)
    @Column(name = "contract_snapshot", columnDefinition = "json")
    private String contractSnapshot;
    @Column(name = "pdf_attempt_count", nullable = false)
    private int pdfAttemptCount;
    @Column(name = "pdf_next_retry_at")
    private LocalDateTime pdfNextRetryAt;
    @Column(name = "pdf_lease_until")
    private LocalDateTime pdfLeaseUntil;
    @Column(name = "pdf_claim_token", length = 36)
    private String pdfClaimToken;
    @Column(name = "cancel_reason", length = 500)
    private String cancelReason;

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "agreement_code", nullable = false, unique = true, length = 50)
    private String agreementCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rental_request_id")
    private RentalRequest rentalRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(precision = 12, scale = 2)
    private BigDecimal deposit;

    @Column(name = "electricity_price", precision = 10, scale = 2)
    private BigDecimal electricityPrice;

    @Column(name = "water_price", precision = 10, scale = 2)
    private BigDecimal waterPrice;

    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "payment_cycle_months")
    @Builder.Default
    private Integer paymentCycleMonths = 1;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "terms_text", columnDefinition = "TEXT")
    private String termsText;

    @Column(name = "pdf_url", length = 255)
    private String pdfUrl;

    @Column(name = "pdf_public_id", length = 255)
    private String pdfPublicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private RentalAgreementStatus status = RentalAgreementStatus.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", nullable = false, length = 20)
    private CreatedByRole createdByRole;

    @Column(name = "landlord_confirmed_at")
    private LocalDateTime landlordConfirmedAt;

    @Column(name = "tenant_confirmed_at")
    private LocalDateTime tenantConfirmedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
