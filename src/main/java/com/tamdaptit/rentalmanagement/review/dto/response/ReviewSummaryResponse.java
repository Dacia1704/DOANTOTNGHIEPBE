package com.tamdaptit.rentalmanagement.review.dto.response;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.Map;
@JsonInclude(JsonInclude.Include.ALWAYS)
public record ReviewSummaryResponse(long totalReviews, BigDecimal averageRating, Map<Integer, Long> distribution,
    Integer replyRate) {}
