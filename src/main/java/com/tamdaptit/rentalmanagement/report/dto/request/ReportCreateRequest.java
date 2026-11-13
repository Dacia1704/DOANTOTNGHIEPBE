package com.tamdaptit.rentalmanagement.report.dto.request;
import com.tamdaptit.rentalmanagement.report.entity.enums.*;
import jakarta.validation.constraints.*;
public record ReportCreateRequest(@NotNull ReportTargetType targetType,
    @NotBlank @Size(max=36) String targetId, @NotNull ReportReason reasonCode,
    @Size(max=5000) String description,
    @Size(max=36) String originListingId, @Size(max=36) String rentalAgreementId) {
    public ReportCreateRequest(ReportTargetType targetType,String targetId,ReportReason reasonCode,String description) {
        this(targetType,targetId,reasonCode,description,null,null);
    }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknown(String name,Object value) {
        throw new IllegalArgumentException("Trường không được hỗ trợ: "+name);
    }
}
