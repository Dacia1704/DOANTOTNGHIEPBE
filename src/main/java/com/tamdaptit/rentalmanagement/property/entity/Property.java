package com.tamdaptit.rentalmanagement.property.entity;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.address.entity.Province;
import com.tamdaptit.rentalmanagement.address.entity.Ward;
import com.tamdaptit.rentalmanagement.property.entity.enums.PropertyRentalMode;

@Entity
@Table(name = "properties")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Property {
    @Id @UuidGenerator @Column(length = 36)
    String id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "landlord_id", nullable = false)
    User landlord;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "property_type_id", nullable = false)
    PropertyType propertyType;
    @Column(nullable = false, length = 100)
    String name;
    @Enumerated(EnumType.STRING) @Column(name = "rental_mode", nullable = false, length = 20)
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
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;
}
