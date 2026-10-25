package com.tamdaptit.rentalmanagement.payment.repository;

import com.tamdaptit.rentalmanagement.payment.entity.Payment;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Payment> findByServiceSubscriptionOrder_IdAndStatus(String orderId, PaymentStatus status);

    @Query("select p.landlord.id from Payment p where p.merchantOrderId = :reference")
    Optional<String> findLandlordIdByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findByMerchantOrderId(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Payment> findByLandlord_IdAndStatus(String landlordId, PaymentStatus status);

    @Lock(LockModeType.PESSIMISTIC_READ)
    List<Payment> findByBoostSubscription_IdAndStatus(String boostId, PaymentStatus status);

    Optional<Payment> findByIdAndLandlord_Id(String id, String landlordId);
    Page<Payment> findByLandlord_Id(String landlordId, Pageable pageable);
    Page<Payment> findByRequiresReviewTrue(Pageable pageable);

    @Query("select distinct p.landlord.id from Payment p where p.status = 'PENDING' and p.expiresAt <= :time")
    List<String> findDueLandlordIds(java.time.LocalDateTime time);
}
