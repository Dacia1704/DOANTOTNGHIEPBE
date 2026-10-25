package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackagePrice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePackagePriceRepository extends JpaRepository<ServicePackagePrice, String> {
    boolean existsByPackageEntity_IdAndActiveTrue(String packageId);

    boolean existsByPackageEntity_IdAndDurationMonths(String packageId, Integer durationMonths);

    boolean existsByPackageEntity_IdAndDurationMonthsAndIdNot(String packageId, Integer durationMonths, String id);

    Optional<ServicePackagePrice> findByIdAndPackageEntity_Id(String id, String packageId);

    @Query("""
            select price from ServicePackagePrice price
            where price.packageEntity.id = :packageId
              and (:includeInactive = true or price.active = true)
            order by price.durationMonths
            """)
    List<ServicePackagePrice> findCatalog(String packageId, boolean includeInactive);
}
