package com.tamdaptit.rentalmanagement.identity.entity;

import com.tamdaptit.rentalmanagement.address.entity.Ward;
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
 * Phuong mong muon cua tenant. {@code desired_ward_id} trong
 * {@link TenantProfile} giu nguyen lam "phuong chinh".
 */
@Entity
@Table(name = "tenant_profile_wards")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class TenantProfileWard {

    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_profile_id", nullable = false)
    private TenantProfile tenantProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ward_id", nullable = false)
    private Ward ward;
}
