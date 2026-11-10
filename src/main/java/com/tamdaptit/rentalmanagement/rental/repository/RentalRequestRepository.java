package com.tamdaptit.rentalmanagement.rental.repository;

import com.tamdaptit.rentalmanagement.rental.entity.RentalRequest;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface RentalRequestRepository extends JpaRepository<RentalRequest,String> {
    @Query("select r.room.id from RentalRequest r where r.id=:id")
    Optional<String> roomId(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRequest r where r.id=:id")
    Optional<RentalRequest> lock(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RentalRequest r where r.room.id=:room and r.status=com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus.PENDING order by r.id")
    List<RentalRequest> pendingForRoom(@Param("room") String room);
    @EntityGraph(attributePaths={"listing","listing.property","listing.property.ward","listing.property.province","room","room.roomType","tenant","landlord"})
    @Query("select r from RentalRequest r where (:owner=true and r.landlord.id=:user or :owner=false and r.tenant.id=:user) and (:status is null or r.status=:status) and (:property is null or r.listing.property.id=:property) and (:room is null or r.room.id=:room)")
    Page<RentalRequest> search(@Param("user") String user,@Param("owner") boolean owner,@Param("status") RentalRequestStatus status,@Param("property") String property,@Param("room") String room,Pageable pageable);
    @EntityGraph(attributePaths={"listing","listing.property","listing.property.ward","listing.property.province","room","room.roomType","tenant","landlord"})
    @Query("""
        select r from RentalRequest r left join r.room room
        where r.tenant.id=:user and (:status is null or r.status=:status)
        and (:q='' or locate(lower(:q),lower(r.listing.property.name))>0
          or locate(lower(:q),lower(r.listing.title))>0 or locate(lower(:q),lower(room.roomNumber))>0
          or locate(lower(:q),lower(r.listing.property.addressLine))>0
          or locate(lower(:q),lower(r.listing.property.ward.name))>0
          or locate(lower(:q),lower(r.listing.property.province.name))>0)
        """)
    Page<RentalRequest> searchMine(String user,RentalRequestStatus status,String q,Pageable pageable);
    @Query("select r.status as status,count(r) as total from RentalRequest r where r.tenant.id=:user group by r.status")
    List<StatusCount> countStatuses(String user);
    interface StatusCount { RentalRequestStatus getStatus(); long getTotal(); }
    @Query("select r.id from RentalRequest r where r.status=com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus.PENDING and r.expiresAt<=:now order by r.expiresAt")
    List<String> expired(@Param("now") java.time.LocalDateTime now,Pageable limit);
}
