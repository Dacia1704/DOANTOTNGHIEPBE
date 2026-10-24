package com.tamdaptit.rentalmanagement.listing.repository;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ListingRepository extends JpaRepository<Listing, String> {
    String EFFECTIVE_PRIORITY = """
            case when exists (select b.id from BoostServicePackageSubscription b
                where b.listing = l and b.status = 'ACTIVE' and b.startAt <= :time and b.endAt > :time) then 2
            when exists (select p.id from ServiceSubscriptionPeriod p where p.subscription.landlord = l.room.landlord
                and p.subscription.status in ('ACTIVE','SCHEDULED') and p.startAt <= :time and p.endAt > :time
                and p.subscription.startAt <= :time and p.subscription.endAt > :time
                and l.serviceSubscriptionPeriod = p and l.packagePriorityLevel = 2) then 2
            when exists (select p.id from ServiceSubscriptionPeriod p where p.subscription.landlord = l.room.landlord
                and p.subscription.status in ('ACTIVE','SCHEDULED') and p.startAt <= :time and p.endAt > :time
                and p.subscription.startAt <= :time and p.subscription.endAt > :time
                and (p.defaultPriorityLevelSnapshot = 1 or (l.serviceSubscriptionPeriod = p and l.packagePriorityLevel = 1)))
                then 1 else 0 end
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Listing> findByIdAndRoom_Landlord_Id(String id, String landlordId);

    @Query("select l.room.landlord.id from Listing l where l.id = :id")
    Optional<String> findLandlordIdById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Listing> findByRoom_Landlord_Id(String landlordId);

    @Query(value = "select l from Listing l where l.status = 'PUBLISHED' and l.expiresAt > :time order by "
            + EFFECTIVE_PRIORITY + " desc, l.publishedAt desc, l.id",
            countQuery = "select count(l) from Listing l where l.status = 'PUBLISHED' and l.expiresAt > :time")
    Page<Listing> findPublished(LocalDateTime time, Pageable pageable);

    Page<Listing> findByRoom_Landlord_Id(String landlordId, Pageable pageable);
    Page<Listing> findByStatusAndPendingExpiresAtGreaterThan(ListingStatus status, LocalDateTime time, Pageable pageable);

    @Query("select distinct l.room.landlord.id from Listing l where l.status in ('PENDING','PUBLISHED')")
    List<String> findLiveLandlordIds();
}
