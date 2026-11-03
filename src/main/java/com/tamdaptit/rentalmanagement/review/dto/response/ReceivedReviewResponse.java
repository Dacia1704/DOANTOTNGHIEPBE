package com.tamdaptit.rentalmanagement.review.dto.response;

/** The contract landlord receives room/agreement context without moderation details. */
public record ReceivedReviewResponse(ReviewResponse review, String targetId, String targetName,
    String rentalAgreementId, String agreementCode, String roomId, String roomNumber,
    String propertyId, String propertyName, boolean canReply) {}
