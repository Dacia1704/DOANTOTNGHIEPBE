package com.tamdaptit.rentalmanagement.property.repository;

import com.tamdaptit.rentalmanagement.property.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, String> {
    java.util.List<PropertyImage> findAllByProperty_IdOrderBySortOrderAsc(String propertyId);
    java.util.List<PropertyImage> findAllByProperty_IdInOrderBySortOrderAsc(java.util.Collection<String> ids);
}
