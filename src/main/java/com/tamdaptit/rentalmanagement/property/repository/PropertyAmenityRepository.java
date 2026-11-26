package com.tamdaptit.rentalmanagement.property.repository;

import java.util.Collection;
import java.util.List;

import com.tamdaptit.rentalmanagement.property.entity.PropertyAmenity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PropertyAmenityRepository extends JpaRepository<PropertyAmenity, String> {
    List<PropertyAmenity> findAllByProperty_Id(String propertyId);

    boolean existsByAmenity_Id(String amenityId);

    @Query("select pa from PropertyAmenity pa join fetch pa.amenity where pa.property.id in :ids")
    List<PropertyAmenity> findAllWithAmenityByPropertyIds(@Param("ids") Collection<String> ids);
}
