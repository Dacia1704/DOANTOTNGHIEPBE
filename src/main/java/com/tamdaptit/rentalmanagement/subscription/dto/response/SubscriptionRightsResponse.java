package com.tamdaptit.rentalmanagement.subscription.dto.response;

public record SubscriptionRightsResponse(
        String packageCode, String packageName, Integer maxListings,
        Integer highPrioritySlots, Integer lowPrioritySlots,
        Integer defaultPriorityLevel, Boolean hasStats) {
}
