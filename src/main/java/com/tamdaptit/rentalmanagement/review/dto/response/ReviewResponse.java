package com.tamdaptit.rentalmanagement.review.dto.response;
import com.tamdaptit.rentalmanagement.review.entity.enums.ReviewType;
import java.time.LocalDateTime;
/** Safe for public pages: no agreement, author ID, or moderation details. */
public record ReviewResponse(String id, ReviewType type, Integer rating, String comment,
    String landlordReply, String authorName, String authorAvatarUrl,
    LocalDateTime createdAt, LocalDateTime updatedAt, boolean verifiedStay, String textStatus, String replyStatus) {
    public ReviewResponse(String id, ReviewType type, Integer rating, String comment, String landlordReply,
        String authorName, String authorAvatarUrl, LocalDateTime createdAt, LocalDateTime updatedAt, boolean verifiedStay) {
        this(id,type,rating,comment,landlordReply,authorName,authorAvatarUrl,createdAt,updatedAt,verifiedStay,"VISIBLE","VISIBLE");
    }
}
