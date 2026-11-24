package com.tamdaptit.rentalmanagement.moderation;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.community.CommunityAccess;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Evaluation;
import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Rule;
import com.tamdaptit.rentalmanagement.moderation.service.impl.ContentModerationServiceImpl;
import com.tamdaptit.rentalmanagement.notification.service.NotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("Phần 1: Quản lý cộng đồng & Kiểm duyệt - ContentModerationService")
class ContentModerationServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private CommunityAccess access;

    @Mock
    private NotificationPublisher notifications;

    private final Clock clock = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));

    private ContentModerationServiceImpl moderationService;

    @BeforeEach
    void setUp() {
        moderationService = new ContentModerationServiceImpl(jdbc, access, clock, notifications);
    }

    @Test
    @DisplayName("Nội dung sạch không khớp rule nào sẽ trả về trạng thái VISIBLE")
    void testEvaluateCleanContent() {
        Rule rule = new Rule("r1", "lua dao", "KEYWORD", "HIDE", true, true, true, true, true, true, "Lừa đảo", 1L);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(rule));

        Evaluation eval = moderationService.evaluate("POST", "Phòng trọ sinh viên Cầu Giấy sạch sẽ thoáng mát");

        assertThat(eval.status()).isEqualTo("VISIBLE");
        assertThat(eval.reason()).isEmpty();
    }

    @Test
    @DisplayName("Khớp quy tắc HIDE thì bài viết nhận trạng thái HIDDEN")
    void testEvaluateHiddenContent() {
        Rule rule = new Rule("r1", "lua dao", "KEYWORD", "HIDE", true, true, true, true, true, true, "Hành vi lừa đảo", 1L);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(rule));

        Evaluation eval = moderationService.evaluate("POST", "Cảnh báo phòng này lua dao mọi người nhé");

        assertThat(eval.status()).isEqualTo("HIDDEN");
        assertThat(eval.reason()).contains("Hành vi lừa đảo");
    }

    @Test
    @DisplayName("Khớp quy tắc REQUIRE_REVIEW thì bài viết nhận trạng thái PENDING chờ admin duyệt")
    void testEvaluatePendingReviewContent() {
        Rule rule = new Rule("r2", "nhay cam", "KEYWORD", "REQUIRE_REVIEW", true, true, true, true, true, true, "Từ ngữ nhạy cảm", 1L);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(rule));

        Evaluation eval = moderationService.evaluate("POST", "Nội dung có chứa từ nhay cam cần xem xét");

        assertThat(eval.status()).isEqualTo("PENDING");
        assertThat(eval.reason()).contains("Từ ngữ nhạy cảm");
    }

    @Test
    @DisplayName("Scope không áp dụng cho POST thì bài viết không bị kích hoạt rule của bình luận/đánh giá")
    void testScopeFiltering() {
        // Rule chỉ áp dụng cho Reviews và Comments, không áp dụng cho Posts
        Rule commentOnlyRule = new Rule("r3", "tu cam", "KEYWORD", "HIDE", true, false, true, true, true, true, "Cấm bình luận", 1L);
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(commentOnlyRule));

        Evaluation evalPost = moderationService.evaluate("POST", "Bài viết chứa tu cam nhưng rule chỉ áp dụng comment");
        assertThat(evalPost.status()).isEqualTo("VISIBLE");

        Evaluation evalComment = moderationService.evaluate("POST_COMMENT", "Bình luận chứa tu cam");
        assertThat(evalComment.status()).isEqualTo("HIDDEN");
    }

    @Test
    @DisplayName("Record chỉ tạo case trong bảng content_moderation_cases khi status khác VISIBLE")
    void testRecordModerationCase() {
        Evaluation hiddenEval = new Evaluation("HIDDEN", "Vi phạm từ khóa cấm");
        moderationService.record("POST", "post-1", "user-1", 1L, "Nội dung vi phạm", hiddenEval);

        verify(jdbc, times(1)).update(
                contains("INSERT INTO content_moderation_cases"),
                anyString(), eq("POST"), eq("post-1"), eq("user-1"), eq(1L),
                eq("Nội dung vi phạm"), contains("Vi phạm từ khóa cấm"), eq("HIDDEN"), eq("AUTO_RULE"), any()
        );

        // Với status VISIBLE thì không ghi case
        Evaluation visibleEval = new Evaluation("VISIBLE", "");
        moderationService.record("POST", "post-2", "user-2", 1L, "Nội dung sạch", visibleEval);
        verifyNoMoreInteractions(jdbc);
    }

    @Test
    @DisplayName("Xem danh sách rule yêu cầu quyền MODERATION_READ")
    void testRulesPermissionCheck() {
        doThrow(new AppException(ErrorCode.UNAUTHORIZED)).when(access).permission("non-admin", "MODERATION_READ");

        assertThatThrownBy(() -> moderationService.rules("non-admin"))
                .isInstanceOf(AppException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.UNAUTHORIZED);
    }
}
