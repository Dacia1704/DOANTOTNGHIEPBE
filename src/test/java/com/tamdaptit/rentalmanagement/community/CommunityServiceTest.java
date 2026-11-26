package com.tamdaptit.rentalmanagement.community;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.CommentInput;
import com.tamdaptit.rentalmanagement.community.service.impl.CommunityServiceImpl;
import com.tamdaptit.rentalmanagement.discovery.CommunityDiscoveryService;
import com.tamdaptit.rentalmanagement.discovery.FeedRanking;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.moderation.service.ContentModerationService;
import com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher;
import com.tamdaptit.rentalmanagement.storage.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phần 1: Quản lý cộng đồng & Kiểm duyệt - CommunityService")
class CommunityServiceTest {
    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private CommunityAccess access;

    @Mock
    private ContentModerationService moderation;

    @Mock
    private FileStorageService images;

    @Mock
    private ListingRepository listings;

    @Mock
    private CommunityDiscoveryService discovery;

    @Mock
    private NotificationPublisher notifications;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private CommunityServiceImpl communityService;

    @BeforeEach
    void setUp() {
        communityService = new CommunityServiceImpl(
            jdbc,
            access,
            moderation,
            images,
            listings,
            clock,
            discovery,
            notifications
        );
    }

    @Test
    @DisplayName("Trích xuất hashtags từ nội dung bài viết cộng đồng chính xác")
    void testExtractHashtags() {
        String content = "Tìm bạn ở ghép phòng trọ #phongtro #caugiay #hanoi giá rẻ";
        Set<String> tags = FeedRanking.tags(content);

        assertThat(tags).containsExactlyInAnyOrder("#phongtro", "#caugiay", "#hanoi");
        assertThat(FeedRanking.tags("Nội dung không có hashtag")).isEmpty();
        assertThat(FeedRanking.tags(null)).isEmpty();
    }

    @Test
    @DisplayName("Thả reaction không hợp lệ bị từ chối với INVALID_REQUEST")
    void testInvalidReactionType() {
        doNothing().when(access).active("user-1");
        List<Map<String, Object>> mockPost = List
            .of(Map.of("id", "post-1", "status", "VISIBLE", "author_status", "ACTIVE"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(mockPost);

        assertThatThrownBy(() -> communityService.reaction("user-1", "POST", "post-1", "ANGRY"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Thả reaction hợp lệ (LIKE, LOVE, WOW) được chấp nhận")
    void testValidReaction() {
        doNothing().when(access).active("user-1");
        List<Map<String, Object>> mockPost = List
            .of(Map.of("id", "post-1", "status", "VISIBLE", "author_status", "ACTIVE"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(mockPost);

        communityService.reaction("user-1", "POST", "post-1", "LOVE");

        verify(jdbc, times(1)).update(
            contains("community_reactions"),
            eq("POST"),
            eq("post-1"),
            eq("user-1"),
            eq("LOVE"),
            any()
        );
    }

    @Test
    @DisplayName("Gửi reaction với giá trị null sẽ xóa bỏ cảm xúc đã thả")
    void testRemoveReactionWhenNull() {
        doNothing().when(access).active("user-1");
        List<Map<String, Object>> mockPost = List
            .of(Map.of("id", "post-1", "status", "VISIBLE", "author_status", "ACTIVE"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(mockPost);

        communityService.reaction("user-1", "POST", "post-1", null);

        verify(jdbc, times(1)).update(
            contains("DELETE FROM community_reactions WHERE target_type=? AND target_id=? AND user_id=?"),
            eq("POST"),
            eq("post-1"),
            eq("user-1")
        );
    }

    @Test
    @DisplayName("Người dùng không phải tác giả cũng không phải admin thì không có quyền xóa bài")
    void testUnauthorizedDelete() {
        doNothing().when(access).active("stranger");
        when(access.admin("stranger")).thenReturn(false);
        List<Map<String, Object>> mockPost = List
            .of(Map.of("id", "post-1", "author_id", "author-user", "status", "VISIBLE", "author_status", "ACTIVE"));
        when(jdbc.queryForList(anyString(), any(Object.class))).thenReturn(mockPost);

        assertThatThrownBy(() -> communityService.delete("stranger", "POST", "post-1"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Tác giả bài viết hoặc Admin có quyền xóa bài")
    void testAuthorizedDelete() {
        doNothing().when(access).active("author-user");
        when(access.admin("author-user")).thenReturn(false);
        List<Map<String, Object>> mockPost = List
            .of(Map.of("id", "post-1", "author_id", "author-user", "status", "VISIBLE", "author_status", "ACTIVE"));
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(mockPost);

        communityService.delete("author-user", "POST", "post-1");

        verify(jdbc, times(1)).update(
            contains("UPDATE community_posts SET deleted_at=? WHERE id=?"),
            any(),
            eq("post-1")
        );
    }

    @Test
    @DisplayName("Không cho phép comment reply lồng cấp 2 (chỉ hỗ trợ 1 cấp parent)")
    void testCommentNestingConstraint() {
        doNothing().when(access).lockActor("user-1");
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(0);

        List<Map<String, Object>> mockPost = List
            .of(Map.of("id", "post-1", "status", "VISIBLE", "author_status", "ACTIVE", "author_id", "author-1"));
        List<Map<String, Object>> mockChildComment = List.of(
            Map.of(
                "id",
                "comment-child",
                "post_id",
                "post-1",
                "parent_id",
                "root-comment",
                "status",
                "VISIBLE",
                "author_status",
                "ACTIVE"
            )
        );

        when(jdbc.queryForList(contains("community_posts"), any(Object[].class))).thenReturn(mockPost);
        when(jdbc.queryForList(contains("community_comments"), any(Object[].class))).thenReturn(mockChildComment);

        CommentInput input = new CommentInput("Bình luận reply cấp 2", "comment-child", null, null);

        assertThatThrownBy(() -> communityService.comment("user-1", "post-1", null, input))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Truy vấn feed với tab không hợp lệ ném INVALID_REQUEST")
    void testFeedInvalidTab() {
        assertThatThrownBy(() -> communityService.feed("user-1", "TRENDING", null, null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST);

        assertThatThrownBy(() -> communityService.feed("user-1", "UNKNOWN", null, null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Không cho phép người dùng tự theo dõi chính mình (self-follow)")
    void testCannotFollowSelf() {
        doNothing().when(access).active("user-1");

        assertThatThrownBy(() -> communityService.follow("user-1", "user-1", true))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_REQUEST);
    }

    @Test
    @DisplayName("Theo dõi tác giả hợp lệ thực hiện ghi nhận vào community_follows")
    void testFollowSuccess() {
        doNothing().when(access).active("user-1");
        doNothing().when(access).active("author-2");
        when(jdbc.update(contains("INSERT IGNORE INTO community_follows"), eq("user-1"), eq("author-2")))
            .thenReturn(1);

        communityService.follow("user-1", "author-2", true);

        verify(notifications, times(1)).enqueue(
            eq("author-2"),
            any(),
            contains("Người theo dõi mới"),
            any(),
            any(),
            any()
        );
    }

    @Test
    @DisplayName("Không cho phép tự báo cáo bài viết của chính mình")
    void testCannotReportSelf() {
        doNothing().when(access).lockActor("author-user");
        List<Map<String, Object>> mockPost = List.of(
            Map.of(
                "id",
                "post-1",
                "author_id",
                "author-user",
                "status",
                "VISIBLE",
                "author_status",
                "ACTIVE",
                "version",
                1L
            )
        );
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(mockPost);

        assertThatThrownBy(() -> communityService.report("author-user", "POST", "post-1", "Spam"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CANNOT_REPORT_SELF);
    }

    @Test
    @DisplayName("Ghim bài viết yêu cầu quyền Admin")
    void testPinRequiresAdmin() {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED)).when(access).requireAdmin("non-admin");

        assertThatThrownBy(() -> communityService.pin("non-admin", "post-1", true))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("Kháng nghị (requestReview) bài viết đang VISIBLE bị từ chối")
    void testAppealVisiblePostRejected() {
        doNothing().when(access).lockActor("author-1");
        // Bài viết vẫn đang VISIBLE thì không thể tạo kháng nghị
        List<Map<String, Object>> mockPost = List.of(
            Map.of(
                "id",
                "post-1",
                "author_id",
                "author-1",
                "status",
                "VISIBLE",
                "author_status",
                "ACTIVE",
                "version",
                1L
            )
        );
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(mockPost);

        assertThatThrownBy(() -> communityService.requestReview("author-1", "post-1", "Mong xem xét lại"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
