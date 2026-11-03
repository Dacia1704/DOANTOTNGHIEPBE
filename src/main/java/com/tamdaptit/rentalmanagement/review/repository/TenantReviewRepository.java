package com.tamdaptit.rentalmanagement.review.repository;

import com.tamdaptit.rentalmanagement.review.entity.TenantReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface TenantReviewRepository extends JpaRepository<TenantReview, String>, JpaSpecificationExecutor<TenantReview> {
    Optional<TenantReview> findByRentalAgreement_Id(String agreementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from TenantReview r where r.id=:id")
    Optional<TenantReview> lock(@Param("id") String id);

    @Override
    @EntityGraph(attributePaths={"landlord", "rentalAgreement", "tenant", "hiddenBy"})
    Page<TenantReview> findAll(Specification<TenantReview> spec, Pageable pageable);

    @Query("select r.rating as rating, count(r) as total, sum(0) as replied from TenantReview r where r.hidden=false and r.tenant.id=:targetId group by r.rating")
    List<RatingCount> ratings(@Param("targetId") String targetId);
}
