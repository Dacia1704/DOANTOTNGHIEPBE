package com.tamdaptit.rentalmanagement.subscription.dto.request;

import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ServiceSubscriptionOrderRequest(
        @NotBlank String priceOptionId,
        @NotNull ServiceSubscriptionOrderType type) {
}
