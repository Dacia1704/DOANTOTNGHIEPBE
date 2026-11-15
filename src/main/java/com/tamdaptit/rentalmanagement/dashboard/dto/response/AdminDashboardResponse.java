package com.tamdaptit.rentalmanagement.dashboard.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record AdminDashboardResponse(LocalDate from, LocalDate to, LocalDateTime generatedAt,
        Map<String, Number> current, Map<String, BigDecimal> period,
        Map<String, BigDecimal> previous, Map<String, Long> listingStatuses, List<Point> series) {
    public record Point(LocalDate date, Map<String, BigDecimal> values) {}
}
