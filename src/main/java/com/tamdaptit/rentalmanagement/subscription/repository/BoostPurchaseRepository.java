package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.BoostServicePackageSubscription;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.BoostSubscriptionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface BoostPurchaseRepository extends JpaRepository<BoostServicePackageSubscription, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<BoostServicePackageSubscription> findByIdAndLandlord_Id(String id, String landlordId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<BoostServicePackageSubscription> findByLandlord_IdAndStatus(String landlordId, BoostSubscriptionStatus status);

    @Query("select distinct b.landlord.id from BoostServicePackageSubscription b "
            + "where (b.status = 'ACTIVE' and b.endAt <= :time) "
            + "or (b.status = 'PENDING_PAYMENT' and b.paymentExpiresAt <= :time)")
    List<String> findDueLandlordIds(LocalDateTime time);

    Page<BoostServicePackageSubscription> findByLandlord_Id(String landlordId, Pageable pageable);
}
