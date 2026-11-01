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
    @EntityGraph(attributePaths={"listing","listing.property","room","tenant","landlord"})
    @Query("select r from RentalRequest r where (:owner=true and r.landlord.id=:user or :owner=false and r.tenant.id=:user) and (:status is null or r.status=:status) and (:property is null or r.listing.property.id=:property) and (:room is null or r.room.id=:room)")
    Page<RentalRequest> search(@Param("user") String user,@Param("owner") boolean owner,@Param("status") RentalRequestStatus status,@Param("property") String property,@Param("room") String room,Pageable pageable);
    @Query("select r.id from RentalRequest r where r.status=com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus.PENDING and r.expiresAt<=:now order by r.expiresAt")
    List<String> expired(@Param("now") java.time.LocalDateTime now,Pageable limit);
}
