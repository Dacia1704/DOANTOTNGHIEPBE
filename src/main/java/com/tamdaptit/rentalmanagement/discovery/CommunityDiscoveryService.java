package com.tamdaptit.rentalmanagement.discovery;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Slice;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.tamdaptit.rentalmanagement.discovery.FeedRanking.Exposure;
import static com.tamdaptit.rentalmanagement.discovery.FeedRanking.Item;
import static com.tamdaptit.rentalmanagement.discovery.FeedRanking.communityScore;
import static com.tamdaptit.rentalmanagement.discovery.FeedRanking.interleave;
import static com.tamdaptit.rentalmanagement.discovery.FeedRanking.num;
import static com.tamdaptit.rentalmanagement.discovery.FeedRanking.str;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommunityDiscoveryService {
    private final JdbcTemplate jdbc;
    private final FeedRanking ranking;
    private final RankingSessions sessions;
    private final Clock clock;

    public Slice<String> ids(
        String actor,
        String tab,
        String cursor,
        String keyword
    ) {
        var prefs = ranking.preferences(actor);
        String q = keyword == null ? "" : keyword.trim();
        if (q.length() > 100) {
            throw new AppException(ErrorCode.INVALID_REQUEST);
        }

        String scope = "community-v1|" + Objects.toString(actor, "") + "|" + tab + "|" + q + "|" + prefs.key();
        var params = new HashMap<String, Object>();
        params.put("actor", actor == null ? "" : actor);
        params.put("recent", LocalDateTime.now(clock).minusHours(24));
        String sql = """
            SELECT p.id,p.author_id,p.content,p.area,p.created_at,
              EXISTS(SELECT 1 FROM community_follows f WHERE f.followee_id=p.author_id AND f.follower_id=:actor) following,
              COALESCE(r.total,0) reactions,COALESCE(c.total,0) commenters,COALESCE(s.total,0) saves,
              COALESCE(v.total,0) violations
            FROM community_posts p JOIN users u ON u.id=p.author_id AND u.status='ACTIVE'
            LEFT JOIN (SELECT r.target_id,COUNT(DISTINCT r.user_id) total FROM community_reactions r
              JOIN users ru ON ru.id=r.user_id AND ru.status='ACTIVE'
              JOIN community_posts rp ON rp.id=r.target_id WHERE r.target_type='POST' AND r.created_at>=:recent
              AND r.user_id<>rp.author_id GROUP BY r.target_id) r ON r.target_id=p.id
            LEFT JOIN (SELECT c.post_id,COUNT(DISTINCT c.author_id) total FROM community_comments c
              JOIN community_posts cp ON cp.id=c.post_id JOIN users cu ON cu.id=c.author_id AND cu.status='ACTIVE'
              WHERE c.status='VISIBLE' AND c.deleted_at IS NULL AND c.created_at>=:recent AND c.author_id<>cp.author_id GROUP BY c.post_id) c ON c.post_id=p.id
            LEFT JOIN (SELECT s.post_id,COUNT(DISTINCT s.user_id) total FROM community_saved_posts s
              JOIN users su ON su.id=s.user_id AND su.status='ACTIVE'
              JOIN community_posts sp ON sp.id=s.post_id WHERE s.created_at>=:recent AND s.user_id<>sp.author_id GROUP BY s.post_id) s ON s.post_id=p.id
            LEFT JOIN (SELECT author_id,COUNT(DISTINCT target_id) total FROM content_moderation_cases
              WHERE resolved=1 AND status IN ('HIDDEN','REMOVED') GROUP BY author_id) v ON v.author_id=p.author_id
            WHERE p.status='VISIBLE' AND p.deleted_at IS NULL
            """;
        if ("FOLLOWING".equals(tab)) {
            sql += " AND EXISTS(SELECT 1 FROM community_follows f WHERE f.followee_id=p.author_id AND f.follower_id=:actor)";
        }

        if (!q.isBlank()) {
            sql += " AND p.content LIKE :keyword ESCAPE '!'";
            params.put("keyword", "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }

        var named = new NamedParameterJdbcTemplate(jdbc);
        var rows = named.queryForList(sql, params);
        var eligible = new HashSet<String>();
        for (var row : rows) {
            eligible.add(str(row, "id"));
        }

        RankingSessions.Snapshot snapshot;
        int offset = 0;
        if (cursor == null || cursor.isBlank()) {
            var seen = ranking.exposures("COMMUNITY", eligible, prefs.enabled() ? actor : null);
            var topics = ranking.topics(actor, prefs);
            var locations = new HashMap<String, List<Map<String, Object>>>();
            if (!eligible.isEmpty()) {
                for (var row : named.queryForList("""
                SELECT pl.post_id,p.province_id,p.ward_id FROM community_post_listings pl
                JOIN listings l ON l.id=pl.listing_id JOIN properties p ON p.id=l.property_id
                WHERE pl.post_id IN (:ids)
                """, Map.of("ids", eligible))) {
                    locations.computeIfAbsent(str(row, "post_id"), k -> new ArrayList<>()).add(row);
                }
            }

            var raw = new HashMap<String, Double>();
            for (var row : rows) {
                raw.put(
                    str(row, "id"),
                    Math.log1p(num(row, "reactions") + 3 * num(row, "commenters") + 4 * num(row, "saves"))
                );
            }

            var distribution = raw.values().stream().sorted().toList();
            double p95 = distribution.isEmpty() ? 1
                : Math.max(
                    1,
                    distribution.get(Math.min(distribution.size() - 1, (int) Math.floor(distribution.size() * .95)))
                );
            var items = new ArrayList<Item>();
            for (var row : rows) {
                String id = str(row, "id");
                var exposure = seen.getOrDefault(id, new Exposure(0, 0));
                LocalDateTime created = row.get("created_at") instanceof LocalDateTime date ? date
                    : ((Timestamp) row.get("created_at")).toLocalDateTime();
                double fresh = ranking.freshness(created, 48);
                double interest = ranking.interest(
                    prefs,
                    topics,
                    str(row, "area"),
                    str(row, "content"),
                    locations.getOrDefault(id, List.of())
                );
                boolean followed = Boolean.TRUE.equals(row.get("following")) || num(row, "following") > 0;
                double score = communityScore(
                    interest,
                    Math.min(1, raw.get(id) / p95),
                    followed,
                    fresh,
                    1.0 / (1 + num(row, "violations")),
                    exposure.mine()
                );
                items.add(new Item(id, str(row, "author_id"), 0, score, fresh, exposure.total()));
            }

            snapshot = sessions.create(scope, interleave(items, false), Map.of());
        } else {
            String[] pieces = cursor.split(":", -1);
            if (pieces.length != 3 || !"r".equals(pieces[0])) {
                throw new AppException(ErrorCode.FEED_SESSION_EXPIRED);
            }

            try {
                offset = Integer.parseInt(pieces[2]);
            } catch (NumberFormatException e) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }

            snapshot = sessions.get(pieces[1], scope);
            if (offset < 0 || offset > snapshot.ids().size()) {
                throw new AppException(ErrorCode.INVALID_REQUEST);
            }
        }

        var page = new ArrayList<String>();
        int next = offset;
        while (next < snapshot.ids().size() && page.size() < 10) {
            String id = snapshot.ids().get(next++);
            if (eligible.contains(id)) {
                page.add(id);
            }
        }

        final int start = next;
        boolean more = snapshot.ids().stream().skip(start).anyMatch(eligible::contains);
        return new Slice<>(page, more ? "r:" + snapshot.id() + ":" + next : null, more);
    }
}
