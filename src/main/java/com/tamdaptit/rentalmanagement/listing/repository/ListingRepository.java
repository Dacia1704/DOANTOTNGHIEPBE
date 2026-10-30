package com.tamdaptit.rentalmanagement.listing.repository;

import java.math.BigDecimal;
import com.tamdaptit.rentalmanagement.room.entity.enums.RoomRentalStatus;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
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
@Repository
public interface ListingRepository extends JpaRepository<Listing, String> {
    String EFFECTIVE_PRIORITY = """
            case when exists (select a.id from BoostAllocation a join a.subscription b
                where a.listing = l and a.priorityLevel = 2 and b.status = 'ACTIVE' and b.startAt <= :time and b.endAt > :time) then 2
            when exists (select p.id from ServiceSubscriptionPeriod p where p.subscription.landlord = l.property.landlord
                and p.subscription.status in ('ACTIVE','SCHEDULED') and p.startAt <= :time and p.endAt > :time
                and p.subscription.startAt <= :time and p.subscription.endAt > :time
                and l.serviceSubscriptionPeriod = p and l.packagePriorityLevel = 2) then 2
            when exists (select a.id from BoostAllocation a join a.subscription b
                where a.listing = l and a.priorityLevel = 1 and b.status = 'ACTIVE' and b.startAt <= :time and b.endAt > :time) then 1
            when exists (select p.id from ServiceSubscriptionPeriod p where p.subscription.landlord = l.property.landlord
                and p.subscription.status in ('ACTIVE','SCHEDULED') and p.startAt <= :time and p.endAt > :time
                and p.subscription.startAt <= :time and p.subscription.endAt > :time
                and (p.defaultPriorityLevelSnapshot = 1 or (l.serviceSubscriptionPeriod = p and l.packagePriorityLevel = 1)))
                then 1 else 0 end
            """;

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Listing> findByIdAndProperty_Landlord_Id(String id, String landlordId);

    @Query("select l.property.landlord.id from Listing l where l.id = :id")
    Optional<String> findLandlordIdById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Listing> findByProperty_Landlord_Id(String landlordId);

    String PUBLIC_CONDITION = """
            l.status = 'PUBLISHED' and l.expiresAt > :time
            and not exists (select newer.id from Listing newer where newer.property = l.property
                and newer.status = 'PUBLISHED' and newer.expiresAt > :time
                and (newer.publishedAt > l.publishedAt or (newer.publishedAt = l.publishedAt and newer.id > l.id)))
            """;

    @Query(value = "select l from Listing l where " + PUBLIC_CONDITION + " order by "
            + EFFECTIVE_PRIORITY + " desc, l.publishedAt desc, l.id",
            countQuery = "select count(l) from Listing l where " + PUBLIC_CONDITION)
    Page<Listing> findPublished(LocalDateTime time, Pageable pageable);

    String PUBLIC_FILTER = "from Listing l where " + PUBLIC_CONDITION + """
              and (:provinceId is null or l.property.province.id = :provinceId)
              and (:wardId is null or l.property.ward.id = :wardId)
              and exists (select r.id from Room r where r.property = l.property
                and (:typeName is null or lower(r.roomType.name) = lower(:typeName))
                and (:rentalStatus is null or r.rentalStatus = :rentalStatus)
                and (:minPrice is null or r.price >= :minPrice)
                and (:maxPrice is null or r.price <= :maxPrice)
                and (:amenityCount = 0 or (select count(distinct lower(a.name)) from Amenity a
                     where lower(a.name) in :amenityNames and
                       (exists (select ra.id from RoomAmenity ra where ra.room = r and ra.amenity = a)
                        or exists (select pa.id from PropertyAmenity pa where pa.property = l.property and pa.amenity = a))) = :amenityCount))
            """;

    @Query(value = "select l " + PUBLIC_FILTER + " order by " + EFFECTIVE_PRIORITY + " desc, l.publishedAt desc, l.id",
            countQuery = "select count(l) " + PUBLIC_FILTER)
    Page<Listing> searchPublished(LocalDateTime time, String typeName, String provinceId, String wardId,
            RoomRentalStatus rentalStatus, BigDecimal minPrice, BigDecimal maxPrice,
            List<String> amenityNames, long amenityCount, Pageable pageable);

    @Query("""
            select l from Listing l where (:status is null or
                (case when (l.status = 'PUBLISHED' and l.expiresAt <= :time)
                        or (l.status = 'PENDING' and l.pendingExpiresAt <= :time)
                 then 'EXPIRED' else cast(l.status as string) end) = :status)
            """)
    Page<Listing> findForAdmin(String status, LocalDateTime time, Pageable pageable);

    Page<Listing> findByProperty_Landlord_Id(String landlordId, Pageable pageable);

    Page<Listing> findByStatusAndPendingExpiresAtGreaterThan(ListingStatus status, LocalDateTime time, Pageable pageable);

    @Query("select distinct l.property.landlord.id from Listing l where l.status in ('PENDING','PUBLISHED')")
    List<String> findLiveLandlordIds();

    /**
     * Fetch Listing cùng Room và Room.landlord để tránh N+1 khi chat module cần landlord.
     */
    @Query("""
            SELECT l
            FROM Listing l
            JOIN FETCH l.property p
            JOIN FETCH p.landlord
            WHERE l.id = :id
            """)
    Optional<Listing> findByIdWithPropertyAndLandlord(@Param("id") String id);
}
