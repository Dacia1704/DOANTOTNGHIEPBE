package com.tamdaptit.rentalmanagement.report.dto.response;

import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.report.entity.enums.ReportStatus;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType;

public record ReportResponse(
    String id,
    ReportTargetType targetType,
    String targetId,
    String landlordId,
    String reasonCode,
    String description,
    ReportStatus status,
    String handledBy,
    String resolutionNote,
    LocalDateTime resolvedAt,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    String targetName,
    String handledByName,
    int warningLevel,
    String sanctionAction,
    String sanctionedListingId,
    String originListingId
) {}
