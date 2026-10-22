package com.tamdaptit.rentalmanagement.room.repository;

import com.tamdaptit.rentalmanagement.room.entity.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, String> {

    boolean existsByNameIgnoreCase(String name);
}
