package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.time.LocalDateTime;

public record CurrentSubscriptionResponse(
        String subscriptionId, boolean free, boolean canSubmitListings,
        LocalDateTime subscriptionEndsAt, ServiceSubscriptionPeriodResponse currentPeriod,
        ServiceSubscriptionPeriodResponse nextPeriod,
        SubscriptionRightsResponse rights, ServiceSubscriptionOrderResponse scheduledChange) {
}
