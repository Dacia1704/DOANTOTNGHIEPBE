package com.tamdaptit.rentalmanagement.property.repository;

import com.tamdaptit.rentalmanagement.property.entity.PropertyType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyTypeRepository extends JpaRepository<PropertyType, String> {
    boolean existsByNameIgnoreCase(String name);
}
