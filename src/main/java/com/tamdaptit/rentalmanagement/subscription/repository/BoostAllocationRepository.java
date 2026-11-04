package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.BoostAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface BoostAllocationRepository extends JpaRepository<BoostAllocation, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoostAllocation> findByListing_IdAndActiveTrue(String listingId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoostAllocation> findByListing_IdAndSubscription_IdAndPriorityLevel(String listingId, String subscriptionId, int priorityLevel);
    List<BoostAllocation> findAllByListing_Id(String listingId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BoostAllocation> findBySubscription_Id(String subscriptionId);

    @Query("""
            select a from BoostAllocation a join fetch a.subscription b join fetch a.listing l
            where l.id in :listingIds and a.active = true and b.status = 'ACTIVE' and b.startAt <= :time and b.endAt > :time
            and ((l.status = 'PUBLISHED' and l.expiresAt > :time)
                 or (l.status = 'PENDING' and l.pendingExpiresAt > :time))
            """)
    List<BoostAllocation> findSelected(Collection<String> listingIds, LocalDateTime time);

    // Caller holds the landlord lock. Clear the persistence context only through explicit entity operations.
    @Query("""
            select a from BoostAllocation a join fetch a.subscription b join fetch a.listing l
            where b.landlord.id = :landlordId and a.active = true and
            (b.status <> 'ACTIVE' or b.startAt > :time or b.endAt <= :time
             or not ((l.status = 'PUBLISHED' and l.expiresAt is not null and l.expiresAt > :time)
                     or (l.status = 'PENDING' and l.pendingExpiresAt is not null and l.pendingExpiresAt > :time)))
            """)
    List<BoostAllocation> findStale(String landlordId, LocalDateTime time);
}
