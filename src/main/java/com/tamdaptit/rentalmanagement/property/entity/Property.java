package com.tamdaptit.rentalmanagement.property.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.address.entity.Province;
import com.tamdaptit.rentalmanagement.address.entity.Ward;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
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

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Property {
    @Id
    @UuidGenerator
    @Column(length = 36)
    String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    User landlord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_type_id", nullable = false)
    PropertyType propertyType;

    @Column(nullable = false, length = 100)
    String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_mode", nullable = false, length = 20)
    PropertyRentalMode rentalMode;

    @Column(name = "total_floors")
    Integer totalFloors;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "province_id", nullable = false)
    Province province;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ward_id", nullable = false)
    Ward ward;

    @Column(name = "address_line", nullable = false, length = 255)
    String addressLine;

    @Column(precision = 10, scale = 8)
    BigDecimal latitude;

    @Column(precision = 11, scale = 8)
    BigDecimal longitude;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;
}
