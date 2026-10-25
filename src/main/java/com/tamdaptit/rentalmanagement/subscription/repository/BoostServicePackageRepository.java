package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface BoostServicePackageRepository extends JpaRepository<BoostServicePackage, String> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);

    @Query("""
            select p from BoostServicePackage p
            where :includeInactive = true or
              (p.active = true and p.priorityLevel = 2 and p.durationMonths in (1,3,6))
            """)
    Page<BoostServicePackage> findCatalog(boolean includeInactive, Pageable pageable);
}
