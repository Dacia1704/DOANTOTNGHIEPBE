package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

/**
 * N-N users <-> listings: nguoi thue luu tin dang yeu thich.
 * PK (tenant_id, listing_id) dam bao moi nguoi luu moi tin mot lan.
 */
@Entity
@Table(name = "saved_listings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SavedListing {

    @EmbeddedId
    SavedListingId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tenantId")
    @JoinColumn(name = "tenant_id", nullable = false)
    User tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("listingId")
    @JoinColumn(name = "listing_id", nullable = false)
    Listing listing;

    @Column(name = "created_at", nullable = false, updatable = false)
    LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}