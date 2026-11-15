package com.tamdaptit.rentalmanagement.community.dto;

import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.constraints.*;

public final class CommunityDtos {
    private CommunityDtos() {}
    public record PostInput(@Size(max=10000) String content, @Size(max=150) String area,
        @Size(max=10) List<String> mediaIds, @Size(max=3) List<String> listingIds, Long expectedVersion) {}
    public record CommentInput(@NotBlank @Size(max=4000) String content, String parentId, Long expectedVersion) {}
    public record ReactionInput(@Pattern(regexp="LIKE|LOVE|WOW") @NotNull String type) {}
    public record ReportInput(@NotBlank @Size(max=1000) String reason) {}
    public record DecisionInput(@Pattern(regexp="VISIBLE|RESTRICTED|PENDING|HIDDEN|REMOVED") @NotNull String status,
        @NotBlank @Size(max=1000) String reason, long expectedVersion) {}
    public record RuleInput(@NotBlank @Size(max=500) String pattern,
        @Pattern(regexp="TOKEN|PHRASE|DOMAIN") @NotNull String matchType,
        @Pattern(regexp="RESTRICT|REQUIRE_REVIEW|HIDE") @NotNull String action,
        boolean accentInsensitive, boolean applyPosts, boolean applyComments, boolean applyReviews,
        boolean applyReplies, boolean enabled, @NotBlank @Size(max=255) String reason, long version) {}
    public record Rule(String id, String pattern, String matchType, String action, boolean accentInsensitive,
        boolean applyPosts, boolean applyComments, boolean applyReviews, boolean applyReplies, boolean enabled, String reason, long version) {}
    public record Evaluation(String status, String reason) {}
    public record Media(String id, String url) {}
    public record ListingLink(String id, String title, boolean available) {}
    public record Post(String id, String authorId, String authorName, String avatarUrl, String role,
        String content, String area, String status, long version, boolean pinned, boolean edited,
        LocalDateTime createdAt, List<Media> media, List<ListingLink> listings,
        long reactionCount, long commentCount, String myReaction, boolean saved, boolean following) {}
    public record Comment(String id, String postId, String parentId, String authorId, String authorName,
        String avatarUrl, String content, String status, long version, LocalDateTime createdAt,
        long reactionCount, String myReaction, boolean deleted) {}
    public record Slice<T>(List<T> items, String nextCursor, boolean hasMore) {}
    public record ModerationCase(String id, String targetType, String targetId, String authorName,
        String snapshot, String reason, String status, String source, boolean resolved, long contentVersion, LocalDateTime createdAt) {}
}
