package com.tamdaptit.rentalmanagement.entity;

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
import com.tamdaptit.rentalmanagement.entity.enums.RoomRentalStatus;

@Entity
@Table(name = "rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Room {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.AUTO)
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "landlord_id", nullable = false)
    User landlord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_type_id", nullable = false)
    RoomType roomType;

    @Column(nullable = false, precision = 12, scale = 2)
    BigDecimal price;

    @Column(name = "electricity_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    BigDecimal electricityPrice = BigDecimal.ZERO;

    @Column(name = "water_price", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    BigDecimal waterPrice = BigDecimal.ZERO;

    @Column(name = "service_fee", nullable = false, precision = 10, scale = 2)
    @Builder.Default
    BigDecimal serviceFee = BigDecimal.ZERO;

    @Column(name = "default_deposit", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    BigDecimal defaultDeposit = BigDecimal.ZERO;

    @Column(name = "area_m2", precision = 6, scale = 2)
    BigDecimal areaM2;

    @Column(name = "max_occupants")
    Integer maxOccupants;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "province_id", nullable = false)
    Province province;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "district_id", nullable = false)
    District district;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ward_id", nullable = false)
    Ward ward;

    @Column(name = "address_line", nullable = false, length = 500)
    String addressLine;

    @Column(precision = 10, scale = 7)
    BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    BigDecimal longitude;

    /** AVAILABLE / RENTED / MAINTENANCE */
    @Enumerated(EnumType.STRING)
    @Column(name = "rental_status", nullable = false, length = 20)
    @Builder.Default
    RoomRentalStatus rentalStatus = RoomRentalStatus.AVAILABLE;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    Boolean isDeleted = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (rentalStatus == null) rentalStatus = RoomRentalStatus.AVAILABLE;
        if (isDeleted == null) isDeleted = false;
        if (electricityPrice == null) electricityPrice = BigDecimal.ZERO;
        if (waterPrice == null) waterPrice = BigDecimal.ZERO;
        if (serviceFee == null) serviceFee = BigDecimal.ZERO;
        if (defaultDeposit == null) defaultDeposit = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}