package com.tamdaptit.rentalmanagement.room.repository;

import java.util.Collection;
import java.util.List;

import com.tamdaptit.rentalmanagement.room.entity.Amenity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AmenityRepository extends JpaRepository<Amenity, String> {
    boolean existsByNameIgnoreCase(String name);

    Page<Amenity> findByNameContainingIgnoreCase(String name, Pageable pageable);

    List<Amenity> findByNameInIgnoreCase(Collection<String> names);
}
