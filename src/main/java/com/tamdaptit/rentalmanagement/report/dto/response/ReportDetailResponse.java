package com.tamdaptit.rentalmanagement.report.dto.response;
import java.util.List;
public record ReportDetailResponse(ReportResponse report,List<ReportEvidenceResponse> evidences) {}
