package com.tamdaptit.rentalmanagement.listing.repository;

import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ListingRepository extends JpaRepository<Listing, String> {

    /**
     * Fetch Listing cùng Room và Room.landlord để tránh N+1 khi chat module cần landlord.
     */
    @Query("""
            SELECT l
            FROM Listing l
            JOIN FETCH l.room r
            JOIN FETCH r.landlord
            WHERE l.id = :id
            """)
    Optional<Listing> findByIdWithRoomAndLandlord(@Param("id") String id);
}
