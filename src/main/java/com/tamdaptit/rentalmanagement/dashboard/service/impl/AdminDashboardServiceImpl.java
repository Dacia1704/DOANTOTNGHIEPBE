package com.tamdaptit.rentalmanagement.dashboard.service.impl;

import com.tamdaptit.rentalmanagement.dashboard.service.AdminDashboardService;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.AdminDashboardResponse;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.AdminDashboardResponse.Point;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    @Override
    public AdminDashboardResponse overview(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now();
        if (from == null && to == null) { to = today; from = today.minusDays(6); }
        if (from == null || to == null || from.isAfter(to) || to.isAfter(today)
                || ChronoUnit.DAYS.between(from, to) >= 366) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Khoảng thống kê phải từ 1 đến 366 ngày và không vượt quá hôm nay.");
        }
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousStart = from.minusDays(days);
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Map<LocalDate, Map<String, BigDecimal>> daily = new TreeMap<>();
        for (LocalDate date = previousStart; !date.isAfter(to); date = date.plusDays(1)) {
            daily.put(date, new HashMap<>());
        }

        // All SQL identifiers below are constants; dates are bound parameters. Never fetch individual records.
        events(daily, previousStart, end, "newUsers", "users", "created_at", "1=1");
        String role = "EXISTS (SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id "
                + "WHERE ur.user_id=users.id AND r.code='%s')";
        String nonAdmin = " AND NOT " + role.formatted("ADMIN");
        events(daily, previousStart, end, "newLandlords", "users", "created_at", role.formatted("LANDLORD") + nonAdmin);
        events(daily, previousStart, end, "newTenants", "users", "created_at",
                role.formatted("TENANT") + " AND NOT " + role.formatted("LANDLORD") + nonAdmin);
        events(daily, previousStart, end, "newProperties", "properties", "created_at", "1=1");
        events(daily, previousStart, end, "newListings", "listings", "created_at", "1=1");
        events(daily, previousStart, end, "rentalRequests", "rental_requests", "created_at", "1=1");
        events(daily, previousStart, end, "newAgreements", "rental_agreements",
                "GREATEST(landlord_confirmed_at,tenant_confirmed_at)",
                "landlord_confirmed_at IS NOT NULL AND tenant_confirmed_at IS NOT NULL "
                + "AND status IN ('ACTIVE','EXPIRED','TERMINATED')");
        events(daily, previousStart, end, "posts", "community_posts", "created_at", "deleted_at IS NULL");
        events(daily, previousStart, end, "comments", "community_comments", "created_at", "deleted_at IS NULL");
        events(daily, previousStart, end, "reactions", "community_reactions", "created_at", "1=1");
        for (var row : jdbc.queryForList("""
                SELECT DATE(paid_at) day, type, SUM(amount) amount FROM payments
                WHERE status='SUCCESS' AND requires_review=0 AND paid_at>=? AND paid_at<?
                GROUP BY DATE(paid_at),type
                """, previousStart.atStartOfDay(), end)) {
            var values = daily.get(date(row.get("day")));
            if (values != null) values.put("SERVICE_PACKAGE".equals(row.get("type")) ? "serviceRevenue" : "boostRevenue",
                    new BigDecimal(row.get("amount").toString()));
        }

        var current = new LinkedHashMap<String, Number>();
        current.put("users", count("SELECT COUNT(*) FROM users"));
        current.put("properties", count("SELECT COUNT(*) FROM properties"));
        // Availability follows RentalAvailabilityService: pending contracts also hold a room.
        current.put("availableRooms", count("""
                SELECT COUNT(*) FROM rooms r WHERE r.rental_status='AVAILABLE'
                AND NOT EXISTS (SELECT 1 FROM rental_agreements a WHERE a.room_id=r.id
                  AND a.status IN ('DRAFT','PENDING_CONFIRMATION','ACTIVE'))
                """));
        current.put("activeAgreements", count("SELECT COUNT(*) FROM rental_agreements WHERE status='ACTIVE' AND end_date>=CURRENT_DATE"));
        current.put("paidLandlords", count("""
                SELECT COUNT(DISTINCT s.landlord_id) FROM service_package_subscriptions s
                JOIN service_subscription_periods p ON p.subscription_id=s.id
                WHERE s.status='ACTIVE' AND s.start_at<=CURRENT_TIMESTAMP AND s.end_at>CURRENT_TIMESTAMP
                AND p.start_at<=CURRENT_TIMESTAMP AND p.end_at>CURRENT_TIMESTAMP AND p.package_code_snapshot<>'FREE'
                """));
        current.put("activeBoosts", count("SELECT COUNT(*) FROM boost_service_package_subscriptions WHERE status='ACTIVE' AND start_at<=CURRENT_TIMESTAMP AND end_at>CURRENT_TIMESTAMP"));
        current.put("openReports", count("SELECT COUNT(*) FROM reports WHERE status IN ('PENDING','PROCESSING')"));
        current.put("pendingModeration", count("SELECT COUNT(*) FROM content_moderation_cases WHERE resolved=0"));
        current.put("reviewPayments", count("SELECT COUNT(*) FROM payments WHERE requires_review=1"));
        Map<String, Long> statuses = new LinkedHashMap<>();
        for (String status : List.of("PUBLISHED", "HIDDEN", "EXPIRED", "PENDING", "REJECTED")) statuses.put(status, 0L);
        for (var row : jdbc.queryForList("""
                SELECT CASE
                  WHEN status='PUBLISHED' AND expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED'
                  WHEN status='PENDING' AND pending_expires_at<=CURRENT_TIMESTAMP THEN 'EXPIRED'
                  ELSE status END effective_status, COUNT(*) total FROM listings GROUP BY effective_status
                """)) statuses.put(row.get("effective_status").toString(), ((Number) row.get("total")).longValue());
        current.put("publishedListings", statuses.get("PUBLISHED"));
        var period = totals(daily, from, to);
        var previous = totals(daily, previousStart, from.minusDays(1));
        LocalDate seriesStart = from;
        List<Point> series = daily.entrySet().stream().filter(e -> !e.getKey().isBefore(seriesStart))
                .map(e -> new Point(e.getKey(), Map.copyOf(e.getValue()))).toList();
        return new AdminDashboardResponse(from, to, LocalDateTime.now(), current, period, previous, statuses, series);
    }

    private void events(Map<LocalDate, Map<String, BigDecimal>> daily, LocalDate start, LocalDateTime end,
            String key, String table, String timestamp, String condition) {
        String sql = "SELECT DATE(" + timestamp + ") day, COUNT(*) total FROM " + table
                + " WHERE " + condition + " AND " + timestamp + ">=? AND " + timestamp + "<? GROUP BY DATE(" + timestamp + ")";
        for (var row : jdbc.queryForList(sql, start.atStartOfDay(), end)) {
            var values = daily.get(date(row.get("day")));
            if (values != null) values.put(key, new BigDecimal(row.get("total").toString()));
        }
    }

    private long count(String sql) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class)); }

    private static LocalDate date(Object value) {
        return value instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(value.toString());
    }

    private static Map<String, BigDecimal> totals(Map<LocalDate, Map<String, BigDecimal>> daily, LocalDate start, LocalDate end) {
        Map<String, BigDecimal> total = new HashMap<>();
        daily.forEach((date, values) -> {
            if (!date.isBefore(start) && !date.isAfter(end)) values.forEach((key, value) -> total.merge(key, value, BigDecimal::add));
        });
        total.put("revenue", total.getOrDefault("serviceRevenue", BigDecimal.ZERO).add(total.getOrDefault("boostRevenue", BigDecimal.ZERO)));
        return total;
    }
}
