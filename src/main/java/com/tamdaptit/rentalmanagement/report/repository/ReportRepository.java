package com.tamdaptit.rentalmanagement.report.repository;
import com.tamdaptit.rentalmanagement.report.entity.Report;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface ReportRepository extends JpaRepository<Report,String>, JpaSpecificationExecutor<Report> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id=:id")
    Optional<Report> lock(@Param("id") String id);

    boolean existsByTenant_IdAndListing_IdAndStatusIn(String tenantId,String listingId,Collection<ReportStatus> statuses);
    boolean existsByTenant_IdAndLandlord_IdAndListingIsNullAndStatusIn(String tenantId,String landlordId,Collection<ReportStatus> statuses);

    @Override
    @EntityGraph(attributePaths={"tenant","landlord","listing","handledBy"})
    Page<Report> findAll(Specification<Report> spec,Pageable pageable);

    @Query("select r.status as status,count(r) as total from Report r group by r.status")
    List<StatusCount> counts();
    interface StatusCount { ReportStatus getStatus(); Long getTotal(); }
}
