package com.tamdaptit.rentalmanagement.subscription.repository;

import com.tamdaptit.rentalmanagement.subscription.entity.ServiceSubscriptionPeriod;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Collection;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;

public interface ServiceSubscriptionPeriodRepository extends JpaRepository<ServiceSubscriptionPeriod, String> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<ServiceSubscriptionPeriod> findBySubscription_IdOrderByPeriodNumberAsc(String subscriptionId);

    Page<ServiceSubscriptionPeriod> findBySubscription_Landlord_Id(String landlordId, Pageable pageable);

    @Query("""
            select p from ServiceSubscriptionPeriod p where p.subscription.landlord.id in :landlordIds
            and p.subscription.status in ('ACTIVE','SCHEDULED') and p.startAt <= :time and p.endAt > :time
            and p.subscription.startAt <= :time and p.subscription.endAt > :time
            """)
    List<ServiceSubscriptionPeriod> findEffectivePeriods(Collection<String> landlordIds, LocalDateTime time);
}
