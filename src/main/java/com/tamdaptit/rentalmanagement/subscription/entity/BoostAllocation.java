package com.tamdaptit.rentalmanagement.subscription.entity;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "boost_allocations", uniqueConstraints = @UniqueConstraint(
        name = "uq_boost_allocation_consumption", columnNames = {"listing_id", "boost_subscription_id", "priority_level"}))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BoostAllocation {
    @Id @UuidGenerator @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "boost_subscription_id", nullable = false)
    private BoostServicePackageSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false)
    private Listing listing;

    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel;

    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;
}
