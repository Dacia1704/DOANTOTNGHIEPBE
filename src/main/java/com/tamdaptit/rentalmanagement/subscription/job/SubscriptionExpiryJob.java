package com.tamdaptit.rentalmanagement.subscription.job;

import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostPurchaseRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.ServicePackageSubscriptionRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.ServiceSubscriptionOrderRepository;
import com.tamdaptit.rentalmanagement.subscription.service.SubscriptionLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;

@Slf4j
@Component
@RequiredArgsConstructor
public class SubscriptionExpiryJob {
    private final ServicePackageSubscriptionRepository subscriptions;
    private final ServiceSubscriptionOrderRepository orders;
    private final BoostPurchaseRepository boosts;
    private final PaymentRepository payments;
    private final ListingRepository listings;
    private final SubscriptionLifecycleService lifecycle;
    private final Clock subscriptionClock;

    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void refreshDueOwners() {
        var time = LocalDateTime.now(subscriptionClock);
        var owners = new HashSet<>(subscriptions.findLiveLandlordIds());
        owners.addAll(listings.findLiveLandlordIds());
        owners.addAll(orders.findDueLandlordIds(time));
        owners.addAll(boosts.findDueLandlordIds(time));
        owners.addAll(payments.findDueLandlordIds(time));
        for (String owner : owners) {
            try {
                lifecycle.refreshOwner(owner);
            } catch (RuntimeException exception) {
                log.warn("Could not refresh subscriptions for landlord {}: {}", owner, exception.getClass().getSimpleName());
            }
        }
    }
}
