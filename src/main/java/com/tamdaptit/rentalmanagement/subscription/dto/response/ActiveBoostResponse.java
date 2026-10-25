package com.tamdaptit.rentalmanagement.subscription.dto.response;

import java.util.List;

public record ActiveBoostResponse(BoostPurchaseResponse subscription,
        long usedHighPrioritySlots, long usedLowPrioritySlots,
        long remainingHighPrioritySlots, long remainingLowPrioritySlots,
        List<BoostAllocationResponse> allocations) {
}
