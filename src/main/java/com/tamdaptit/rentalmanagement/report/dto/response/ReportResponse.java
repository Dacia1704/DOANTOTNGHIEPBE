package com.tamdaptit.rentalmanagement.report.dto.response;
import com.tamdaptit.rentalmanagement.report.entity.enums.*;
import java.time.LocalDateTime;
public record ReportResponse(String id, ReportTargetType targetType, String targetId, String landlordId,
    String reasonCode, String description, ReportStatus status, String handledBy, String resolutionNote,
    LocalDateTime resolvedAt, LocalDateTime createdAt, LocalDateTime updatedAt,
    String targetName, String handledByName) {}
