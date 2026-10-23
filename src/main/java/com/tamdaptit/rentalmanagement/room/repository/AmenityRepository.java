package com.tamdaptit.rentalmanagement.room.repository;

import com.tamdaptit.rentalmanagement.room.entity.Amenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, String> {

    boolean existsByNameIgnoreCase(String name);

    Page<Amenity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    java.util.List<Amenity> findByNameInIgnoreCase(java.util.Collection<String> names);
}
