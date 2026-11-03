package com.tamdaptit.rentalmanagement.review.repository;

import com.tamdaptit.rentalmanagement.review.entity.LandlordReview;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LandlordReviewRepository extends JpaRepository<LandlordReview, String> {
    String VISIBLE = "r.landlord.id = :ownerId and (r.hidden = false or r.hidden is null)";

    @EntityGraph(attributePaths = {"tenant", "rentalAgreement", "rentalAgreement.room"})
    @Query("select r from LandlordReview r where " + VISIBLE)
    Page<LandlordReview> findVisible(@Param("ownerId") String ownerId, Pageable page);

    @Query("select r.rating as rating, count(r.id) as total, sum(case when r.landlordReply is not null and trim(r.landlordReply) <> '' then 1 else 0 end) as replied from LandlordReview r where " + VISIBLE + " group by r.rating")
    List<RatingBucket> summarize(@Param("ownerId") String ownerId);
}
