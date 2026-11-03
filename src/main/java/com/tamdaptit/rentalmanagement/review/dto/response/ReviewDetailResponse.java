package com.tamdaptit.rentalmanagement.review.dto.response;
import java.time.LocalDateTime;
/** Only the author and admins receive moderation and agreement details. */
public record ReviewDetailResponse(ReviewResponse review, String rentalAgreementId,
    boolean hidden, String hiddenBy, LocalDateTime hiddenAt, String hiddenReason,
    String targetId, String targetName) {}
