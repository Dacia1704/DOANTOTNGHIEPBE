package com.tamdaptit.rentalmanagement.report.dto.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportReason;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReportCreateRequest(
    @NotNull ReportTargetType targetType,
    @NotBlank @Size(max = 36) String targetId,
    @NotNull ReportReason reasonCode,
    @Size(max = 5000) String description,
    @Size(max = 36) String originListingId,
    @Size(max = 36) String rentalAgreementId
) {
    public ReportCreateRequest(
        ReportTargetType targetType,
        String targetId,
        ReportReason reasonCode,
        String description
    ) {
        this(targetType, targetId, reasonCode, description, null, null);
    }

    @JsonAnySetter
    public void rejectUnknown(String name, Object value) {
        throw new IllegalArgumentException("Trường không được hỗ trợ: " + name);
    }
}
