package com.tamdaptit.rentalmanagement.identity.entity;

import com.tamdaptit.rentalmanagement.address.entity.Province;
import com.tamdaptit.rentalmanagement.address.entity.Ward;
import com.tamdaptit.rentalmanagement.identity.entity.enums.TenantType;
import com.tamdaptit.rentalmanagement.identity.entity.enums.TopPriority;
import com.tamdaptit.rentalmanagement.room.entity.RoomType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 1-1 voi users: user_id vua la khoa chinh vua la khoa ngoai.
 *
 * <p>Cac truong phong cho AI (tenant_type, occupants, move_in_date,
 * anchor_*, max_distance_km, price_tolerance_pct, top_priority, note,
 * allow_personalization) deu KHONG bat buoc nhap.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", length = 20)
    private TenantType tenantType;

    @Column(name = "occupants")
    private Integer occupants;

    @Column(name = "move_in_date")
    private LocalDate moveInDate;

    /** Ten diem neo (truong hoc, noi lam viec) de hien thi. */
    @Column(name = "anchor_label", length = 255)
    private String anchorLabel;

    @Column(name = "anchor_latitude", precision = 10, scale = 8)
    private BigDecimal anchorLatitude;

    @Column(name = "anchor_longitude", precision = 11, scale = 8)
    private BigDecimal anchorLongitude;

    @Column(name = "max_distance_km", precision = 5, scale = 2)
    private BigDecimal maxDistanceKm;

    /** Muc vuot gia chap nhan duoc, mac dinh 20%. Kieu TINYINT trong MySQL. */
    @Column(name = "price_tolerance_pct", nullable = false)
    @Builder.Default
    private byte priceTolerancePct = 20;

    @Enumerated(EnumType.STRING)
    @Column(name = "top_priority", length = 20)
    private TopPriority topPriority;

    /** Ghi chu tu do. KHONG tra cho landlord. */
    @Column(name = "note", length = 500)
    private String note;

    /** Cho phep AI dung hanh vi (xem/luu/lien he) de ca nhan hoa. */
    @Column(name = "allow_personalization", nullable = false)
    @Builder.Default
    private boolean allowPersonalization = true;

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
