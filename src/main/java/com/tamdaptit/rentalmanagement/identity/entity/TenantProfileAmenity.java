package com.tamdaptit.rentalmanagement.identity.entity;

import com.tamdaptit.rentalmanagement.room.entity.Amenity;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.UuidGenerator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Tien ich uu tien cua tenant.
 *
 * <p>{@code isRequired} = TRUE: phong thieu tien ich nay bi loai khi goi y.
 * FALSE: chi cong diem neu co.
 */
@Entity
@Table(name = "tenant_profile_amenities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TenantProfileAmenity {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_profile_id", nullable = false)
    private TenantProfile tenantProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "amenity_id", nullable = false)
    private Amenity amenity;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean isRequired = false;
}
