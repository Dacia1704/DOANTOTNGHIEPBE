package com.tamdaptit.rentalmanagement.moderation;

import com.tamdaptit.rentalmanagement.community.dto.CommunityDtos.Rule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Phần 1: Quản lý cộng đồng & Kiểm duyệt - ContentMatcher Deterministic Token Matching")
class ContentMatcherTest {
    private Rule createRule(
        String id,
        String pattern,
        String matchType,
        boolean accentInsensitive
    ) {
        return new Rule(
            id,
            pattern,
            matchType,
            "HIDE",
            accentInsensitive,
            true,
            true,
            true,
            true,
            true,
            "Vi phạm từ ngữ",
            1L
        );
    }

    @Test
    @DisplayName("Normalize chuỗi loại bỏ dấu, ký tự đặc biệt và chuẩn hóa khoảng trắng")
    void testNormalize() {
        String inputWithAccents = "Phòng trọ giá rẻ tại Cầu Giấy, Hà Nội!!!";
        String normalizedWithoutAccents = ContentMatcher.normalize(inputWithAccents, true);
        assertThat(normalizedWithoutAccents).isEqualTo("phong tro gia re tai cau giay ha noi");

        String normalizedWithAccents = ContentMatcher.normalize(inputWithAccents, false);
        assertThat(normalizedWithAccents).isEqualTo("phòng trọ giá rẻ tại cầu giấy hà nội");

        assertThat(ContentMatcher.normalize(null, true)).isEmpty();
        assertThat(ContentMatcher.normalize("   ", true)).isEmpty();
    }

    @Test
    @DisplayName("Khớp từ khóa không dấu (accent-insensitive)")
    void testAccentInsensitiveKeywordMatch() {
        Rule rule = createRule("r1", "lua dao", "KEYWORD", true);

        assertThat(ContentMatcher.matches(rule, "Coi chừng bị lừa đảo nhé mọi người")).isTrue();
        assertThat(ContentMatcher.matches(rule, "Hành vi lua dao can bi xu ly")).isTrue();
        assertThat(ContentMatcher.matches(rule, "Phòng trọ uy tín giá tốt")).isFalse();
    }

    @Test
    @DisplayName("Khớp từ khóa có dấu (accent-sensitive)")
    void testAccentSensitiveKeywordMatch() {
        Rule rule = createRule("r2", "lừa đảo", "KEYWORD", false);

        assertThat(ContentMatcher.matches(rule, "Coi chừng bị lừa đảo nhé mọi người")).isTrue();
        // Khi accent-sensitive=false (tức giữ nguyên dấu), "lua dao" không khớp với "lừa đảo"
        assertThat(ContentMatcher.matches(rule, "Hành vi lua dao can bi xu ly")).isFalse();
    }

    @Test
    @DisplayName("Khớp ranh giới từ (word boundary) - không khớp chuỗi con không phải từ độc lập")
    void testWordBoundaryMatch() {
        Rule rule = createRule("r3", "spam", "KEYWORD", true);

        assertThat(ContentMatcher.matches(rule, "Bài này là spam")).isTrue();
        // "spammer" có "spam" là chuỗi con nhưng không phải từ riêng lẻ
        assertThat(ContentMatcher.matches(rule, "Anh ấy là spammer")).isFalse();
    }

    @Test
    @DisplayName("Khớp tên miền (DOMAIN rule) trong URL bài viết")
    void testDomainRuleMatch() {
        Rule domainRule = createRule("d1", "spam-site.xyz", "DOMAIN", false);

        assertThat(ContentMatcher.matches(domainRule, "Xem thêm tại https://spam-site.xyz/phong-tro")).isTrue();
        assertThat(ContentMatcher.matches(domainRule, "Link phụ http://sub.spam-site.xyz/test")).isTrue();
        assertThat(ContentMatcher.matches(domainRule, "Truy cập https://trotot.com để tìm phòng")).isFalse();
        assertThat(ContentMatcher.matches(domainRule, "Không có url hợp lệ: spam-site.xyz")).isFalse();
    }

    @Test
    @DisplayName("Xử lý chuỗi rỗng và null an toàn")
    void testNullAndEmptyText() {
        Rule rule = createRule("r1", "vi pham", "KEYWORD", true);

        assertThat(ContentMatcher.matches(rule, null)).isFalse();
        assertThat(ContentMatcher.matches(rule, "")).isFalse();
        assertThat(ContentMatcher.matches(rule, "   ")).isFalse();
    }
}
