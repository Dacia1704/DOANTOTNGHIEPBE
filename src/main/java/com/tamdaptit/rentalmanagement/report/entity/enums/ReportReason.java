package com.tamdaptit.rentalmanagement.report.entity.enums;
public enum ReportReason {
    SCAM, MISLEADING_INFORMATION, INVALID_PRICE, UNSAFE_CONDITION, INAPPROPRIATE_CONTENT, OTHER;
    public boolean supports(ReportTargetType target) {
        return this!=INVALID_PRICE || target==ReportTargetType.LISTING;
    }
}
