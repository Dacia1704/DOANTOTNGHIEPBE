package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ServicePackageRepository extends JpaRepository<ServicePackage, String> {
    boolean existsByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCaseAndIdNot(String code, String id);

    @Query("""
            select p from ServicePackage p
            where :includeInactive = true or
              (p.active = true and (upper(p.code) = 'FREE' or exists
                (select price.id from ServicePackagePrice price
                 where price.packageEntity = p and price.active = true)))
            """)
    Page<ServicePackage> findCatalog(boolean includeInactive, Pageable pageable);
}
