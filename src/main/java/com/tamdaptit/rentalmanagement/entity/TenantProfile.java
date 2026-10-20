package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * 1-1 voi users: user_id vua la khoa chinh vua la khoa ngoai.
 */
@Entity
@Table(name = "tenant_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TenantProfile {

    @Id
    @Column(name = "user_id", columnDefinition = "CHAR(36)")
    String userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_province_id")
    Province desiredProvince;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_district_id")
    District desiredDistrict;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_ward_id")
    Ward desiredWard;

    @Column(name = "price_min", precision = 12, scale = 2)
    BigDecimal priceMin;

    @Column(name = "price_max", precision = 12, scale = 2)
    BigDecimal priceMax;

    @Column(name = "area_min", precision = 6, scale = 2)
    BigDecimal areaMin;

    @Column(name = "area_max", precision = 6, scale = 2)
    BigDecimal areaMax;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_room_type_id")
    RoomType desiredRoomType;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}