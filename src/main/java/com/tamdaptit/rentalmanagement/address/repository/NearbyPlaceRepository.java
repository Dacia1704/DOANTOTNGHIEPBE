package com.tamdaptit.rentalmanagement.address.repository;

import com.tamdaptit.rentalmanagement.address.entity.NearbyPlace;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface NearbyPlaceRepository extends JpaRepository<NearbyPlace, String> {
    @Query("select p from NearbyPlace p where (:q = '' or lower(p.name) like lower(concat('%', :q, '%')) or lower(p.address) like lower(concat('%', :q, '%'))) and (:category = '' or p.category = :category) order by p.name, p.id")
    List<NearbyPlace> search(String q, String category, Pageable pageable);
}
