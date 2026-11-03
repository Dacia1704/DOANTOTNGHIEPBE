package com.tamdaptit.rentalmanagement.review.repository;

import com.tamdaptit.rentalmanagement.review.entity.RoomReview;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomReviewRepository extends JpaRepository<RoomReview, String> {
    String VISIBLE = "r.room.property.id = :propertyId and (:roomId is null or r.room.id = :roomId) and (r.hidden = false or r.hidden is null)";

    @EntityGraph(attributePaths = {"tenant", "room", "rentalAgreement"})
    @Query("select r from RoomReview r where " + VISIBLE)
    Page<RoomReview> findVisible(@Param("propertyId") String propertyId, @Param("roomId") String roomId, Pageable page);

    @Query("select r.rating as rating, count(r.id) as total, sum(case when r.landlordReply is not null and trim(r.landlordReply) <> '' then 1 else 0 end) as replied from RoomReview r where " + VISIBLE + " group by r.rating")
    List<RatingBucket> summarize(@Param("propertyId") String propertyId, @Param("roomId") String roomId);
}
