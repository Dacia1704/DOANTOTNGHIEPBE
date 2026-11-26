package com.tamdaptit.rentalmanagement.property.repository;

import java.util.Collection;
import java.util.List;

import com.tamdaptit.rentalmanagement.property.entity.PropertyImage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyImageRepository extends JpaRepository<PropertyImage, String> {
    List<PropertyImage> findAllByProperty_IdOrderBySortOrderAsc(String propertyId);

    List<PropertyImage> findAllByProperty_IdInOrderBySortOrderAsc(Collection<String> ids);
}
