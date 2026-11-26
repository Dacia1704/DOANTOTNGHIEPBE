package com.tamdaptit.rentalmanagement.review.dto.response;

import java.math.BigDecimal;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewSummaryResponse(
    long totalReviews,
    BigDecimal averageRating,
    Map<Integer, Long> distribution,
    Integer replyRate
) {}
