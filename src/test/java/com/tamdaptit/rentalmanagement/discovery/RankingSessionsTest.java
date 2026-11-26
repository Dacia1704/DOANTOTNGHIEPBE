package com.tamdaptit.rentalmanagement.discovery;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.discovery.RankingSessions.Snapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phần 3: Tìm kiếm & Gợi ý phòng trọ - RankingSessions Token & Pagination Cache")
class RankingSessionsTest {
    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    private final Instant fixedNow = Instant.parse("2026-11-20T10:00:00Z");
    private final Clock clock = Clock.fixed(fixedNow, ZoneId.of("Asia/Ho_Chi_Minh"));

    private RankingSessions rankingSessions;

    @BeforeEach
    void setUp() {
        when(cacheManager.getCache("rankingSessions")).thenReturn(cache);
        rankingSessions = new RankingSessions(cacheManager, clock);
    }

    @Test
    @DisplayName("Tạo phiên tìm kiếm RankingSession mới với thời hạn TTL 30 phút (1800 giây)")
    void testCreateSession() {
        List<String> ids = List.of("listing-1", "listing-2", "listing-3");
        Map<String, Integer> priorities = Map.of("listing-1", 2, "listing-2", 1);

        Snapshot session = rankingSessions.create("LISTING:hn", ids, priorities);

        assertThat(session.id()).isNotBlank();
        assertThat(session.scope()).isEqualTo("LISTING:hn");
        assertThat(session.ids()).containsExactlyElementsOf(ids);
        assertThat(session.expiresAt()).isEqualTo(fixedNow.plusSeconds(1800));

        verify(cache, times(1)).put(eq(session.id()), eq(session));
    }

    @Test
    @DisplayName("Lấy phiên thành công khi session còn hạn và scope trùng khớp")
    void testGetSessionSuccess() {
        List<String> ids = List.of("listing-1", "listing-2");
        Snapshot created = rankingSessions.create("COMMUNITY:feed", ids, Map.of());

        Snapshot retrieved = rankingSessions.get(created.id(), "COMMUNITY:feed");

        assertThat(retrieved).isNotNull();
        assertThat(retrieved.id()).isEqualTo(created.id());
        assertThat(retrieved.ids()).containsExactlyElementsOf(ids);
    }

    @Test
    @DisplayName("Ném lỗi FEED_SESSION_EXPIRED khi scope không khớp (bộ lọc tìm kiếm đã thay đổi)")
    void testGetSessionScopeMismatch() {
        Snapshot created = rankingSessions.create("LISTING:hn:price_min=2000000", List.of("listing-1"), Map.of());

        // Client gửi request trang 2 nhưng scope thay đổi (ví dụ đổi bộ lọc giá)
        assertThatThrownBy(() -> rankingSessions.get(created.id(), "LISTING:hn:price_min=5000000"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FEED_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("Ném lỗi FEED_SESSION_EXPIRED khi phiên không tồn tại trong cache")
    void testGetSessionNotFound() {
        when(cache.get("non-existent-session", Snapshot.class)).thenReturn(null);

        assertThatThrownBy(() -> rankingSessions.get("non-existent-session", "LISTING:hn"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FEED_SESSION_EXPIRED);
    }

    @Test
    @DisplayName("Ném lỗi FEED_SESSION_EXPIRED khi phiên đã hết hạn (quá 30 phút)")
    void testGetSessionExpired() {
        // Tạo session với clock ban đầu
        Snapshot created = rankingSessions.create("LISTING:hn", List.of("listing-1"), Map.of());

        // Giả lập thời gian trôi qua 31 phút sau
        Clock futureClock = Clock.fixed(fixedNow.plusSeconds(1860), ZoneId.of("Asia/Ho_Chi_Minh"));
        RankingSessions futureSessions = new RankingSessions(cacheManager, futureClock);

        // Fallback từ cacheManager trả về session cũ
        when(cache.get(created.id(), Snapshot.class)).thenReturn(created);

        assertThatThrownBy(() -> futureSessions.get(created.id(), "LISTING:hn"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.FEED_SESSION_EXPIRED);
    }
}
