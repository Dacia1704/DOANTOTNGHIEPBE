package com.tamdaptit.rentalmanagement.room.entity;

import com.tamdaptit.rentalmanagement.property.entity.Property;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
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
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "rooms", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uk_rooms_property_room_number", columnNames = {"property_id", "room_number"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Room {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_id", nullable = false)
    private Property property;

    @Column(name = "room_number", nullable = false, length = 30)
    private String roomNumber;

    @Column(name = "floor_number")
    private Integer floorNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_type_id", nullable = false)
    private RoomType roomType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "electricity_price", precision = 10, scale = 2)
    private BigDecimal electricityPrice;

    @Column(name = "water_price", precision = 10, scale = 2)
    private BigDecimal waterPrice;

    @Column(name = "service_fee", precision = 10, scale = 2)
    private BigDecimal serviceFee;

    @Column(name = "default_deposit", precision = 12, scale = 2)
    private BigDecimal defaultDeposit;

    @Column(name = "area_m2", nullable = false, precision = 8, scale = 2)
    private BigDecimal areaM2;

    @Column(name = "max_occupants")
    @Builder.Default
    private Integer maxOccupants = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_status", nullable = false, length = 20)
    @Builder.Default
    private RoomRentalStatus rentalStatus = RoomRentalStatus.AVAILABLE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

}
