package com.tamdaptit.rentalmanagement.review.repository;

import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.review.entity.LandlordReview;
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

public interface LandlordReviewRepository
    extends JpaRepository<LandlordReview, String>, JpaSpecificationExecutor<LandlordReview> {
    Optional<LandlordReview> findByRentalAgreement_Id(String agreementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from LandlordReview r where r.id=:id")
    Optional<LandlordReview> lock(@Param("id") String id);

    @Override
    @EntityGraph(
        attributePaths = { "tenant", "rentalAgreement.landlord", "rentalAgreement.room.property", "landlord",
            "hiddenBy" }
    )
    Page<LandlordReview> findAll(Specification<LandlordReview> spec, Pageable pageable);

    @Query(
        "select r.rating as rating, count(r) as total, sum(case when r.replyStatus = 'VISIBLE' and r.landlordReply is not null and trim(r.landlordReply) <> '' then 1 else 0 end) as replied from LandlordReview r where r.hidden=false and r.landlord.id=:targetId group by r.rating"
    )
    List<RatingCount> ratings(@Param("targetId") String targetId);
}
