package com.tamdaptit.rentalmanagement.dashboard.service.impl;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse.BoostSummary;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse.PackageSummary;
import com.tamdaptit.rentalmanagement.dashboard.dto.response.LandlordDashboardResponse.Point;
import com.tamdaptit.rentalmanagement.dashboard.service.LandlordDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LandlordDashboardServiceImpl implements LandlordDashboardService {
    private final JdbcTemplate jdbc;
    private final Clock subscriptionClock;

    @Override
    @Transactional(readOnly = true)
    public LandlordDashboardResponse overview(
        String landlordId,
        LocalDate from,
        LocalDate to
    ) {
        LocalDateTime now = LocalDateTime.now(subscriptionClock);
        LocalDate today = now.toLocalDate();
        if (from == null && to == null) {
            to = today;
            from = today.minusDays(6);
        }

        if (
            from == null ||
                to == null ||
                from.isAfter(to) ||
                to.isAfter(today) ||
            ChronoUnit.DAYS.between(from, to) >= 366
        ) {
            throw new AppException(
                ErrorCode.INVALID_REQUEST,
                "Khoảng thống kê phải từ 1 đến 366 ngày và không vượt quá hôm nay."
            );
        }

        long days = ChronoUnit.DAYS.between(from, to) + 1;
        LocalDate previousStart = from.minusDays(days);
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        Map<LocalDate, Map<String, BigDecimal>> daily = new TreeMap<>();
        for (LocalDate d = previousStart; !d.isAfter(to); d = d.plusDays(1)) {
            daily.put(d, new HashMap<>());
        }

        // Only aggregate rows owned by the authenticated landlord; never accept an owner from the request.
        events(daily, landlordId, previousStart, end, "requests", "rental_requests", "created_at", "1=1");
        events(
            daily,
            landlordId,
            previousStart,
            end,
            "acceptedRequests",
            "rental_requests",
            "created_at",
            "status='ACCEPTED'"
        );
        events(
            daily,
            landlordId,
            previousStart,
            end,
            "respondedRequests",
            "rental_requests",
            "created_at",
            "responded_at IS NOT NULL"
        );
        events(
            daily,
            landlordId,
            previousStart,
            end,
            "confirmedAgreements",
            "rental_agreements",
            "GREATEST(landlord_confirmed_at,tenant_confirmed_at)",
            "landlord_confirmed_at IS NOT NULL AND tenant_confirmed_at IS NOT NULL AND status IN ('ACTIVE','EXPIRED','TERMINATED')"
        );
        events(
            daily,
            landlordId,
            previousStart,
            end,
            "endedAgreements",
            "rental_agreements",
            "end_date",
            "status='EXPIRED'"
        );
        for (var row : jdbc.queryForList("""
                SELECT s.stat_date day, SUM(COALESCE(s.view_count,0)) views,
                  SUM(COALESCE(s.contact_count,0)) contacts
                FROM listing_daily_stats s JOIN listings l ON l.id=s.listing_id
                JOIN properties p ON p.id=l.property_id WHERE p.landlord_id=? AND s.stat_date>=? AND s.stat_date<?
                GROUP BY s.stat_date
                """, landlordId, previousStart, to.plusDays(1))) {
            var values = daily.get(date(row.get("day")));
            values.put("views", decimal(row.get("views")));
            values.put("contacts", decimal(row.get("contacts")));
        }

        for (var row : jdbc.queryForList("""
                SELECT DATE(paid_at) day, type, SUM(amount) amount FROM payments
                WHERE landlord_id=? AND status='SUCCESS' AND requires_review=0
                  AND type IN ('SERVICE_PACKAGE','BOOST_PACKAGE') AND paid_at>=? AND paid_at<?
                GROUP BY DATE(paid_at),type
                """, landlordId, previousStart.atStartOfDay(), end)) {
            daily.get(date(row.get("day")))
                .put(
                    "SERVICE_PACKAGE".equals(row.get("type")) ? "serviceSpend" : "boostSpend",
                    decimal(row.get("amount"))
                );
        }

        Map<String, Number> current = new LinkedHashMap<>();
        current.put("properties", count("SELECT COUNT(*) FROM properties WHERE landlord_id=?", landlordId));
        // One room per rentable unit, including the representative room for WHOLE properties.
        Map<String, Long> rooms = statuses(List.of("RENTED", "AVAILABLE", "HELD", "MAINTENANCE"));
        for (var row : jdbc.queryForList("""
                SELECT CASE WHEN r.rental_status='MAINTENANCE' THEN 'MAINTENANCE'
                  WHEN r.rental_status='RENTED' OR EXISTS (SELECT 1 FROM rental_agreements a
                    WHERE a.room_id=r.id AND a.status='ACTIVE') THEN 'RENTED'
                  WHEN EXISTS (SELECT 1 FROM rental_agreements a WHERE a.room_id=r.id
                    AND a.status IN ('DRAFT','PENDING_CONFIRMATION'))
                    OR EXISTS (SELECT 1 FROM rental_requests q WHERE q.room_id=r.id
                      AND q.status='PENDING' AND q.expires_at>?) THEN 'HELD'
                  ELSE 'AVAILABLE' END effective_status, COUNT(*) total
                FROM rooms r JOIN properties p ON p.id=r.property_id WHERE p.landlord_id=? GROUP BY effective_status
                """, now, landlordId)) {
            rooms.put(row.get("effective_status").toString(), ((Number) row.get("total")).longValue());
        }

        current.put("rooms", rooms.values().stream().mapToLong(Long::longValue).sum());
        current.put(
            "wholeUnits",
            count(
                "SELECT COUNT(*) FROM rooms r JOIN properties p ON p.id=r.property_id WHERE p.landlord_id=? AND p.rental_mode='WHOLE'",
                landlordId
            )
        );
        current.put(
            "activeAgreements",
            count(
                "SELECT COUNT(*) FROM rental_agreements WHERE landlord_id=? AND status='ACTIVE' AND start_date<=? AND end_date>=?",
                landlordId,
                today,
                today
            )
        );
        current.put(
            "pendingAgreements",
            count(
                "SELECT COUNT(*) FROM rental_agreements WHERE landlord_id=? AND status IN ('DRAFT','PENDING_CONFIRMATION')",
                landlordId
            )
        );
        current.put(
            "endingAgreements",
            count(
                "SELECT COUNT(*) FROM rental_agreements WHERE landlord_id=? AND status='ACTIVE' AND end_date>=? AND end_date<=?",
                landlordId,
                today,
                today.plusDays(30)
            )
        );
        Map<String, Long> listings = statuses(List.of("PUBLISHED", "PENDING", "HIDDEN", "EXPIRED", "REJECTED"));
        for (var row : jdbc.queryForList("""
                SELECT CASE WHEN l.status='PUBLISHED' AND l.expires_at<=? THEN 'EXPIRED'
                  WHEN l.status='PENDING' AND l.pending_expires_at<=? THEN 'EXPIRED'
                  ELSE l.status END effective_status, COUNT(*) total
                FROM listings l JOIN properties p ON p.id=l.property_id WHERE p.landlord_id=? GROUP BY effective_status
                """, now, now, landlordId)) {
            listings.put(row.get("effective_status").toString(), ((Number) row.get("total")).longValue());
        }

        var servicePackage = packageSummary(landlordId, now);
        long boostCount = 0;
        long boostHigh = 0;
        long boostLow = 0;
        for (var row : jdbc.queryForList("""
                SELECT GREATEST(0,b.high_priority_slots_snapshot-(SELECT COUNT(*) FROM boost_allocations a
                  WHERE a.boost_subscription_id=b.id AND a.priority_level=2)) high_remaining,
                  GREATEST(0,b.low_priority_slots_snapshot-(SELECT COUNT(*) FROM boost_allocations a
                  WHERE a.boost_subscription_id=b.id AND a.priority_level=1)) low_remaining
                FROM boost_service_package_subscriptions b WHERE b.landlord_id=?
                  AND b.status='ACTIVE' AND b.start_at<=? AND b.end_at>?
                """, landlordId, now, now)) {
            boostCount++;
            boostHigh += ((Number) row.get("high_remaining")).longValue();
            boostLow += ((Number) row.get("low_remaining")).longValue();
        }

        long assigned = count("""
                SELECT COUNT(DISTINCT l.id) FROM boost_allocations a
                JOIN boost_service_package_subscriptions b ON b.id=a.boost_subscription_id
                JOIN listings l ON l.id=a.listing_id JOIN properties p ON p.id=l.property_id
                WHERE b.landlord_id=? AND p.landlord_id=? AND b.status='ACTIVE' AND b.start_at<=? AND b.end_at>?
                  AND a.active=1 AND l.priority_suppressed=0 AND l.status='PUBLISHED' AND l.expires_at>?
                """, landlordId, landlordId, now, now, now);
        var period = totals(daily, from, to);
        var previous = totals(daily, previousStart, from.minusDays(1));
        LocalDate seriesStart = from;
        var series = daily.entrySet()
            .stream()
            .filter(e -> !e.getKey().isBefore(seriesStart))
            .map(e -> new Point(e.getKey(), Map.copyOf(e.getValue())))
            .toList();
        return new LandlordDashboardResponse(
            from,
            to,
            now,
            current,
            period,
            previous,
            rooms,
            listings,
            servicePackage,
            new BoostSummary(boostCount, assigned, boostHigh, boostLow),
            series
        );
    }

    private PackageSummary packageSummary(String owner, LocalDateTime now) {
        // Read-only snapshot: dashboard must not acquire subscription locks or perform lifecycle writes.
        var periods = jdbc.queryForList("""
                SELECT p.*, s.end_at subscription_end FROM service_subscription_periods p
                JOIN service_package_subscriptions s ON s.id=p.subscription_id
                WHERE s.landlord_id=? AND s.status IN ('ACTIVE','SCHEDULED')
                  AND s.start_at<=? AND s.end_at>? AND p.start_at<=? AND p.end_at>?
                ORDER BY p.start_at DESC LIMIT 1
                """, owner, now, now, now, now);
        if (periods.isEmpty()) {
            boolean free = count(
                "SELECT COUNT(*) FROM service_package_subscriptions WHERE landlord_id=? AND status<>'CANCELLED'",
                owner
            ) == 0;
            long used = free ? count(
                "SELECT COUNT(*) FROM listings l JOIN properties p ON p.id=l.property_id WHERE p.landlord_id=? AND l.posting_quota_consumed=1 AND l.posting_quota_period_id IS NULL",
                owner
            ) : 0;
            return new PackageSummary(
                free ? "Gói miễn phí" : "Chưa có gói hiệu lực",
                null,
                free ? 3 : 0,
                used,
                0,
                0L,
                free
            );
        }

        var p = periods.getFirst();
        long used = count(
            "SELECT COUNT(*) FROM listings l JOIN properties p ON p.id=l.property_id WHERE p.landlord_id=? AND l.posting_quota_consumed=1 AND l.posting_quota_period_id=?",
            owner,
            p.get("id")
        );
        long usedHigh = count(
            "SELECT COUNT(*) FROM listings l JOIN properties p ON p.id=l.property_id WHERE p.landlord_id=? AND l.priority_quota_period_id=? AND (l.consumed_priority_levels & 2)<>0",
            owner,
            p.get("id")
        );
        long usedLow = count(
            "SELECT COUNT(*) FROM listings l JOIN properties p ON p.id=l.property_id WHERE p.landlord_id=? AND l.priority_quota_period_id=? AND (l.consumed_priority_levels & 1)<>0",
            owner,
            p.get("id")
        );
        Integer limit = p.get("max_listings_snapshot") == null ? null
            : ((Number) p.get("max_listings_snapshot")).intValue();
        Long low = ((Number) p.get("default_priority_level_snapshot")).intValue() == 1 ? null
            : Math.max(0L, ((Number) p.get("low_priority_slots_snapshot")).longValue() - usedLow);
        return new PackageSummary(
            p.get("package_name_snapshot").toString(),
            p.get("subscription_end") instanceof LocalDateTime end ? end
                : ((Timestamp) p.get("subscription_end")).toLocalDateTime(),
            limit,
            used,
            Math.max(0L, ((Number) p.get("high_priority_slots_snapshot")).longValue() - usedHigh),
            low,
            true
        );
    }

    private void events(
        Map<LocalDate, Map<String, BigDecimal>> daily,
        String owner,
        LocalDate start,
        LocalDateTime end,
        String key,
        String table,
        String timestamp,
        String condition
    ) {
        // Table/column/condition strings are constants above. Owner and dates always use bound parameters.
        for (
            var row : jdbc.queryForList(
                "SELECT DATE(" + timestamp + ") day,COUNT(*) total FROM " + table
                    + " WHERE landlord_id=? AND " + condition + " AND " + timestamp + ">=? AND " + timestamp
                    + "<? GROUP BY DATE(" + timestamp + ")",
                owner,
                start.atStartOfDay(),
                end
            )
        ) {
            daily.get(date(row.get("day"))).put(key, decimal(row.get("total")));
        }
    }

    private long count(String sql, Object... args) {
        return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args));
    }

    private static BigDecimal decimal(Object value) {
        return new BigDecimal(value.toString());
    }

    private static LocalDate date(Object value) {
        return value instanceof Date d ? d.toLocalDate() : LocalDate.parse(value.toString());
    }

    private static Map<String, Long> statuses(List<String> keys) {
        Map<String, Long> result = new LinkedHashMap<>();
        keys.forEach(key -> result.put(key, 0L));
        return result;
    }

    private static Map<String, BigDecimal> totals(
        Map<LocalDate, Map<String, BigDecimal>> daily,
        LocalDate start,
        LocalDate end
    ) {
        Map<String, BigDecimal> result = new HashMap<>();
        daily.forEach((date, values) -> {
            if (!date.isBefore(start) && !date.isAfter(end)) {
                values.forEach((key, value) -> result.merge(key, value, BigDecimal::add));
            }
        });
        result.put(
            "spend",
            result.getOrDefault("serviceSpend", BigDecimal.ZERO).add(result.getOrDefault("boostSpend", BigDecimal.ZERO))
        );
        return result;
    }
}
