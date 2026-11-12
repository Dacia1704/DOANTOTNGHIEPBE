package com.tamdaptit.rentalmanagement.rental.dto.response;

import java.util.Map;

public record RentalSummaryResponse(long totalRequests, Map<String, Long> statuses, long totalAgreements) {}
