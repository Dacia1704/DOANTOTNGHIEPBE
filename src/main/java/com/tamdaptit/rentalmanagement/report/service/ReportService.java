package com.tamdaptit.rentalmanagement.report.service;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.report.dto.request.*;
import com.tamdaptit.rentalmanagement.report.dto.response.*;
import com.tamdaptit.rentalmanagement.report.entity.enums.*;
import java.util.*;
public interface ReportService {
    List<ReportReason> reasons(ReportTargetType type);
    ReportResponse create(String actor,ReportCreateRequest body);
    PageResponse<ReportResponse> mine(String actor,ReportStatus status,int page,int size);
    ReportDetailResponse detail(String actor,String id);
    PageResponse<ReportResponse> admin(String actor,ReportStatus status,ReportReason reason,int page,int size);
    Map<ReportStatus,Long> summary(String actor);
    ReportResponse assign(String actor,String id);
    ReportResponse close(String actor,String id,ReportStatus outcome,ReportResolutionRequest body);
    void checkUpload(String actor,String id);
}
