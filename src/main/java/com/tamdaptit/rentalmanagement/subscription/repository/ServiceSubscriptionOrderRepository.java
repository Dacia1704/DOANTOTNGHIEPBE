package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionOrder;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ServiceSubscriptionOrderRepository extends JpaRepository<ServiceSubscriptionOrder, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ServiceSubscriptionOrder> findByIdAndLandlord_Id(String id, String landlordId);

    @Query("select o.landlord.id from ServiceSubscriptionOrder o where o.id = :id")
    Optional<String> findLandlordIdById(String id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ServiceSubscriptionOrder> findByLandlord_IdAndStatusIn(String landlordId,
            Collection<ServiceSubscriptionOrderStatus> statuses);

    Page<ServiceSubscriptionOrder> findByLandlord_Id(String landlordId, Pageable pageable);

    Page<ServiceSubscriptionOrder> findByStatus(ServiceSubscriptionOrderStatus status, Pageable pageable);

    @Query("select distinct o.landlord.id from ServiceSubscriptionOrder o where o.status = 'PENDING_PAYMENT' and o.expiresAt <= :time")
    List<String> findDueLandlordIds(java.time.LocalDateTime time);
}
