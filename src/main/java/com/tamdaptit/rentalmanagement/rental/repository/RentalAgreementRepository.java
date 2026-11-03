package com.tamdaptit.rentalmanagement.rental.repository;

import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.*;

public interface RentalAgreementRepository extends JpaRepository<RentalAgreement,String> {
    List<RentalAgreementStatus> OCCUPIED = List.of(RentalAgreementStatus.DRAFT,RentalAgreementStatus.PENDING_CONFIRMATION,RentalAgreementStatus.ACTIVE);
    boolean existsByRoom_IdAndStatusIn(String roomId,Collection<RentalAgreementStatus> states);
    boolean existsByRoom_Id(String roomId);
    @Query("""
        select count(a)>0 from RentalAgreement a
        where a.tenant.id=:tenantId and a.landlord.id=:landlordId
          and a.status in :states and a.tenantConfirmedAt is not null and a.landlordConfirmedAt is not null
        """)
    boolean hasConfirmedRelationship(@Param("tenantId") String tenantId, @Param("landlordId") String landlordId,
        @Param("states") Collection<RentalAgreementStatus> states);
    Optional<RentalAgreement> findByRentalRequest_Id(String requestId);
    @EntityGraph(attributePaths={"rentalRequest"})
    List<RentalAgreement> findAllByRentalRequest_IdIn(Collection<String> requestIds);
    @Query("select a.room.id from RentalAgreement a where a.id=:id")
    Optional<String> roomId(@Param("id") String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from RentalAgreement a where a.id=:id")
    Optional<RentalAgreement> lock(@Param("id") String id);
    @Query("select a.room.id as roomId, a.status as status from RentalAgreement a where a.room.id in :ids and a.status in :states")
    List<Occupancy> occupancy(@Param("ids") Collection<String> ids,@Param("states") Collection<RentalAgreementStatus> states);
    interface Occupancy { String getRoomId(); RentalAgreementStatus getStatus(); }
    @EntityGraph(attributePaths={"room","room.property","tenant","landlord"})
    @Query("select a from RentalAgreement a where (:owner=true and a.landlord.id=:user or :owner=false and a.tenant.id=:user)")
    Page<RentalAgreement> search(@Param("user") String user,@Param("owner") boolean owner,Pageable pageable);
    @Query("select a.id from RentalAgreement a where a.status=com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus.PENDING_CONFIRMATION and a.confirmationExpiresAt<=:now order by a.confirmationExpiresAt")
    List<String> expired(@Param("now") LocalDateTime now,Pageable limit);
    @Query("""
      select a.id from RentalAgreement a where a.contractSnapshot is not null
      and a.status=com.tamdaptit.rentalmanagement.rental.entity.enums.RentalAgreementStatus.PENDING_CONFIRMATION
      and a.confirmationExpiresAt>:now
      and ((a.pdfStatus in (com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus.PENDING,com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus.FAILED)
      and a.pdfAttemptCount<6 and (a.pdfNextRetryAt is null or a.pdfNextRetryAt<=:now))
      or (a.pdfStatus=com.tamdaptit.rentalmanagement.rental.entity.enums.ContractPdfStatus.PROCESSING and a.pdfLeaseUntil<=:now)) order by a.createdAt
      """)
    List<String> pdfJobs(@Param("now") LocalDateTime now,Pageable limit);
}
