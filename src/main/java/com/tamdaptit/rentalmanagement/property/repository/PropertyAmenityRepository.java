package com.tamdaptit.rentalmanagement.property.repository;

import com.tamdaptit.rentalmanagement.property.entity.PropertyAmenity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, String> {
    java.util.List<PropertyAmenity> findAllByProperty_Id(String propertyId);
    boolean existsByAmenity_Id(String amenityId);
    @org.springframework.data.jpa.repository.Query("select pa from PropertyAmenity pa join fetch pa.amenity where pa.property.id in :ids")
    java.util.List<PropertyAmenity> findAllWithAmenityByPropertyIds(@org.springframework.data.repository.query.Param("ids") java.util.Collection<String> ids);
}
