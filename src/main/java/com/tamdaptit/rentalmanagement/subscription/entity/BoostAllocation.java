package com.tamdaptit.rentalmanagement.subscription.entity;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "boost_allocations",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_boost_allocation_consumption",
        columnNames = { "listing_id", "boost_subscription_id", "priority_level" }
    )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoostAllocation {
    @Id
    @UuidGenerator
    @Column(length = 36)
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
