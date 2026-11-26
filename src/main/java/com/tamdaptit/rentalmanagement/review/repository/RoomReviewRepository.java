package com.tamdaptit.rentalmanagement.review.repository;

import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.review.entity.RoomReview;
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

public interface RoomReviewRepository extends JpaRepository<RoomReview, String>, JpaSpecificationExecutor<RoomReview> {
    Optional<RoomReview> findByRentalAgreement_Id(String agreementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from RoomReview r where r.id=:id")
    Optional<RoomReview> lock(@Param("id") String id);

    @Override
    @EntityGraph(
        attributePaths = { "tenant", "rentalAgreement.landlord", "rentalAgreement.room.property", "room.property",
            "hiddenBy" }
    )
    Page<RoomReview> findAll(Specification<RoomReview> spec, Pageable pageable);

    @Query(
        "select r.rating as rating, count(r) as total, sum(case when r.replyStatus = 'VISIBLE' and r.landlordReply is not null and trim(r.landlordReply) <> '' then 1 else 0 end) as replied from RoomReview r where r.hidden=false and (:roomId is null or r.room.id=:roomId) and (:propertyId is null or r.room.property.id=:propertyId) group by r.rating"
    )
    List<RatingCount> ratings(@Param("roomId") String roomId, @Param("propertyId") String propertyId);
}
