package com.tamdaptit.rentalmanagement.discovery;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
@RequiredArgsConstructor
public class DiscoveryController {
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public record Impressions(
        @NotNull @Pattern(regexp = "LISTING|COMMUNITY") String surface,
        @NotEmpty @Size(max = 50) List<@Pattern(regexp = "[a-fA-F0-9-]{36}") String> ids
    ) {}

    @PostMapping("/impressions")
    @Transactional
    public ApiResponse<Void> impressions(Principal principal, @Valid @RequestBody Impressions input) {
        String actor = principal == null ? null : principal.getName();
        if (
            actor == null ||
            jdbc.queryForObject(
                    "SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",
                    Integer.class,
                    actor
                ) != 1
        ) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        if (
            jdbc.queryForObject(
                "SELECT count(*) FROM tenant_profiles WHERE user_id=? AND allow_personalization=0",
                Integer.class,
                actor
            ) > 0
        ) {
            return ApiResponse.success(null);
        }

        var time = LocalDateTime.now(clock);
        for (String id : new HashSet<>(input.ids())) {
            String check = "COMMUNITY".equals(input.surface())
                ? "SELECT count(*) FROM community_posts p JOIN users u ON u.id=p.author_id WHERE p.id=? AND p.status='VISIBLE' AND p.deleted_at IS NULL AND u.status='ACTIVE'"
                : "SELECT count(*) FROM listings l JOIN properties p ON p.id=l.property_id JOIN users u ON u.id=p.landlord_id WHERE l.id=? AND l.status='PUBLISHED' AND l.expires_at>CURRENT_TIMESTAMP AND u.status='ACTIVE'";
            if (jdbc.queryForObject(check, Integer.class, id) == 0) {
                continue;
            }

            int counted = jdbc.update("""
                UPDATE feed_impressions SET impression_count=impression_count+1,seen_at=?
                WHERE user_id=? AND surface=? AND item_id=? AND bucket_date=? AND seen_at<=?
                """, time, actor, input.surface(), id, time.toLocalDate(), time.minusMinutes(30));
            if (counted == 0) {
                counted = jdbc.update(
                    """
                INSERT IGNORE INTO feed_impressions(user_id,surface,item_id,bucket_date,seen_at,impression_count) VALUES(?,?,?,?,?,1)
                """,
                    actor,
                    input.surface(),
                    id,
                    time.toLocalDate(),
                    time
                );
            }

            if (counted > 0 && "LISTING".equals(input.surface())) {
                jdbc.update(
                    """
                INSERT INTO listing_daily_stats(id,listing_id,stat_date,view_count,contact_count) VALUES(UUID(),?,?,1,0)
                ON DUPLICATE KEY UPDATE view_count=COALESCE(view_count,0)+1
                """,
                    id,
                    time.toLocalDate()
                );
            }
        }

        return ApiResponse.success(null);
    }
}
