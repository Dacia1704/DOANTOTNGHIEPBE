package com.tamdaptit.rentalmanagement.report.service;

import java.util.List;
import java.util.Map;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.report.dto.request.ReportCreateRequest;
import com.tamdaptit.rentalmanagement.report.dto.request.ReportResolutionRequest;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportDetailResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportListingResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportSanctionOptionsResponse;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportReason;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportStatus;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType;

public interface ReportService {
    ReportListingResponse listingDetail(
        String actor,
        String reportId,
        String listingId
    );

    List<ReportReason> reasons(ReportTargetType type);

    ReportResponse create(String actor, ReportCreateRequest body);

    PageResponse<ReportResponse> mine(
        String actor,
        ReportStatus status,
        int page,
        int size
    );

    ReportDetailResponse detail(String actor, String id);

    PageResponse<ReportResponse> admin(
        String actor,
        ReportStatus status,
        ReportReason reason,
        int page,
        int size
    );

    Map<ReportStatus, Long> summary(String actor);

    ReportResponse assign(String actor, String id);

    ReportResponse close(
        String actor,
        String id,
        ReportStatus outcome,
        ReportResolutionRequest body
    );

    void checkUpload(String actor, String id);

    ReportSanctionOptionsResponse sanctionOptions(String actor, String id);
}
