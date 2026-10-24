package com.tamdaptit.rentalmanagement.subscription.service;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.service.ListingService;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.BoostSubscriptionStatus;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostPurchaseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class SubscriptionLifecycleService {
    private final UserRepository users;
    private final PaymentRepository payments;
    private final BoostPurchaseRepository boosts;
    private final ListingService listings;
    private final Clock subscriptionClock;

    @Transactional
    public void refreshOwner(String landlordId) {
        users.findByIdForUpdate(landlordId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        var time = LocalDateTime.now(subscriptionClock).truncatedTo(ChronoUnit.SECONDS);
        payments.findByLandlord_IdAndStatus(landlordId, PaymentStatus.PENDING).stream()
                .filter(p -> !time.isBefore(p.getExpiresAt())).forEach(p -> p.setStatus(PaymentStatus.EXPIRED));
        boosts.findByLandlord_IdAndStatus(landlordId, BoostSubscriptionStatus.ACTIVE).stream()
                .filter(b -> !time.isBefore(b.getEndAt())).forEach(b -> b.setStatus(BoostSubscriptionStatus.EXPIRED));
        boosts.findByLandlord_IdAndStatus(landlordId, BoostSubscriptionStatus.PENDING_PAYMENT).stream()
                .filter(b -> !time.isBefore(b.getPaymentExpiresAt())).forEach(b -> b.setStatus(BoostSubscriptionStatus.CANCELLED));
        boosts.flush();
        listings.refreshOwner(landlordId);
    }
}
