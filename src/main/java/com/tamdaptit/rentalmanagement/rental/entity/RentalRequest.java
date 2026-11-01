package com.tamdaptit.rentalmanagement.rental.entity;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Yeu cau thue phong. landlord_id la SNAPSHOT cua chu tro tai thoi diem tao.
 */
@Entity
@Table(name = "rental_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RentalRequest {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private com.tamdaptit.rentalmanagement.room.entity.Room room;
    @Column(name = "tenant_full_name", length = 100)
    private String tenantFullName;
    @Column(name = "tenant_phone", length = 20)
    private String tenantPhone;
    @Column(name = "tenant_email", length = 255)
    private String tenantEmail;
    @Column(name = "tenant_citizen_id", length = 20)
    private String tenantCitizenId;
    @Column(name = "tenant_permanent_address", length = 500)
    private String tenantPermanentAddress;
    @Column(name = "occupant_count")
    private Integer occupantCount;
    @Column(name = "duration_months")
    private Integer durationMonths;
    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private User tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    private User landlord;

    @Column(columnDefinition = "TEXT")
    private String message;

    @Column(name = "proposed_move_in_date")
    private LocalDate proposedMoveInDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    RentalRequestStatus status = RentalRequestStatus.PENDING;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @Column(name = "pending_key", length = 255)
    private String pendingKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
