package com.tamdaptit.rentalmanagement.review.dto.response;
public record ReviewEligibilityResponse(Eligibility room, Eligibility landlord, Eligibility tenant) {
    public record Eligibility(boolean canCreate, String reviewId, String reason) {}
}
