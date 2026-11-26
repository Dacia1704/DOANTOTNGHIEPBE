package com.tamdaptit.rentalmanagement.property.repository;

import java.util.Optional;

import com.tamdaptit.rentalmanagement.property.entity.Property;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyRepository extends JpaRepository<Property, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Property p where p.id = :id")
    Optional<Property> findLockedById(@Param("id") String id);

    boolean existsByPropertyType_Id(String typeId);

    Page<Property> findByLandlord_Id(String ownerId, Pageable pageable);
}
