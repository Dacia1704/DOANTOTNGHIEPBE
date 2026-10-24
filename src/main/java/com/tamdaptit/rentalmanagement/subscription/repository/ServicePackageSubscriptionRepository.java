package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.ServicePackageSubscription;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface ServicePackageSubscriptionRepository extends JpaRepository<ServicePackageSubscription, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ServicePackageSubscription> findByLandlord_IdOrderByStartAtAsc(String landlordId);

    Page<ServicePackageSubscription> findByLandlord_Id(String landlordId, Pageable pageable);

    @Query("select distinct s.landlord.id from ServicePackageSubscription s where s.status in ('ACTIVE','SCHEDULED')")
    List<String> findLiveLandlordIds();
}
