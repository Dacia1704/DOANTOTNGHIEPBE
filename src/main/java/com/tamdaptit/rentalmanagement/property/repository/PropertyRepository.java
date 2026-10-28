package com.tamdaptit.rentalmanagement.property.repository;

import com.tamdaptit.rentalmanagement.property.entity.Property;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyRepository extends JpaRepository<Property, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Property p where p.id = :id")
    java.util.Optional<Property> findLockedById(@org.springframework.data.repository.query.Param("id") String id);
    boolean existsByPropertyType_Id(String typeId);
    org.springframework.data.domain.Page<Property> findByLandlord_Id(String ownerId, org.springframework.data.domain.Pageable pageable);
}
