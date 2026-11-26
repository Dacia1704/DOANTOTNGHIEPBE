package com.tamdaptit.rentalmanagement.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record LandlordDashboardResponse(
    LocalDate from,
    LocalDate to,
    LocalDateTime generatedAt,
    Map<String, Number> current,
    Map<String, BigDecimal> period,
    Map<String, BigDecimal> previous,
    Map<String, Long> roomStatuses,
    Map<String, Long> listingStatuses,
    PackageSummary servicePackage,
    BoostSummary boost,
    List<Point> series
) {
    public record Point(LocalDate date, Map<String, BigDecimal> values) {}

    public record PackageSummary(
        String name,
        LocalDateTime endsAt,
        Integer maxListings,
        long usedListings,
        long remainingHigh,
        Long remainingLow,
        boolean active
    ) {}

    public record BoostSummary(
        long activePackages,
        long assignedListings,
        long remainingHigh,
        long remainingLow
    ) {}
}
