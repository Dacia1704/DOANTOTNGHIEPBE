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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

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
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_province_id")
    private Province desiredProvince;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_ward_id")
    private Ward desiredWard;
    @Column(name = "price_min", precision = 12, scale = 2)
    private BigDecimal priceMin;
    @Column(name = "price_max", precision = 12, scale = 2)
    private BigDecimal priceMax;
    @Column(name = "area_min", precision = 8, scale = 2)
    private BigDecimal areaMin;
    @Column(name = "area_max", precision = 8, scale = 2)
    private BigDecimal areaMax;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "desired_room_type_id")
    private RoomType desiredRoomType;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}