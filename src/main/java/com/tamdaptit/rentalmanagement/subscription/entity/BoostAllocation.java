package com.tamdaptit.rentalmanagement.subscription.entity;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "boost_allocations")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BoostAllocation {
    @Id @UuidGenerator @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "boost_subscription_id", nullable = false)
    private BoostServicePackageSubscription subscription;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "listing_id", nullable = false, unique = true)
    private Listing listing;

    @Column(name = "priority_level", nullable = false)
    private Integer priorityLevel;
}
