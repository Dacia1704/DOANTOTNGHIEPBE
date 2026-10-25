package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.BoostAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.*;

public interface BoostAllocationRepository extends JpaRepository<BoostAllocation, String> {
    Optional<BoostAllocation> findByListing_Id(String listingId);
    List<BoostAllocation> findBySubscription_Id(String subscriptionId);

    @Query("""
            select a from BoostAllocation a join fetch a.subscription b join fetch a.listing l
            where l.id in :listingIds and b.status = 'ACTIVE' and b.startAt <= :time and b.endAt > :time
            and l.status = 'PUBLISHED' and l.expiresAt > :time
            """)
    List<BoostAllocation> findEffective(Collection<String> listingIds, LocalDateTime time);

    // Caller holds the landlord lock. Clear the persistence context only through explicit entity operations.
    @Query("""
            select a from BoostAllocation a join fetch a.subscription b join fetch a.listing l
            where b.landlord.id = :landlordId and
            (b.status <> 'ACTIVE' or b.startAt > :time or b.endAt <= :time
             or l.status <> 'PUBLISHED' or l.expiresAt is null or l.expiresAt <= :time)
            """)
    List<BoostAllocation> findStale(String landlordId, LocalDateTime time);
}
