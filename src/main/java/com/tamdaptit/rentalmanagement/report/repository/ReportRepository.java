package com.tamdaptit.rentalmanagement.report.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.report.entity.Report;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReportRepository extends JpaRepository<Report, String>, JpaSpecificationExecutor<Report> {
    @Query("select r.landlord.id from Report r where r.id=:id")
    Optional<String> landlordId(@Param("id") String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Report r where r.id=:id")
    Optional<Report> lock(@Param("id") String id);

    boolean existsByTenant_IdAndListing_IdAndStatusIn(
        String tenantId,
        String listingId,
        Collection<ReportStatus> statuses
    );

    boolean existsByTenant_IdAndLandlord_IdAndListingIsNullAndStatusIn(
        String tenantId,
        String landlordId,
        Collection<ReportStatus> statuses
    );

    @Override
    @EntityGraph(attributePaths = { "tenant", "landlord", "listing", "originListing", "handledBy" })
    Page<Report> findAll(Specification<Report> spec, Pageable pageable);

    @Query("select r.status as status,count(r) as total from Report r group by r.status")
    List<StatusCount> counts();

    interface StatusCount {
        ReportStatus getStatus();

        Long getTotal();
    }
}
