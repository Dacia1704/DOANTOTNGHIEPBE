package com.tamdaptit.rentalmanagement.address.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Entity
@Table(name = "nearby_places")
@Getter
@NoArgsConstructor
public class NearbyPlace {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false, length = 255) private String name;
    @Column(nullable = false, length = 30) private String category;
    @Column(length = 500) private String address;
    @Column(nullable = false, precision = 10, scale = 8) private BigDecimal latitude;
    @Column(nullable = false, precision = 11, scale = 8) private BigDecimal longitude;
}
