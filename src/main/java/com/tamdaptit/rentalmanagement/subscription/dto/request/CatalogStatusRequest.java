package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.NotNull;

public record CatalogStatusRequest(
        @NotNull(message = "Trạng thái bán không được để trống")
        Boolean active) {
}
