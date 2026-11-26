package com.tamdaptit.rentalmanagement.report.controller;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.report.dto.request.ReportCreateRequest;
import com.tamdaptit.rentalmanagement.report.dto.request.ReportResolutionRequest;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportDetailResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportEvidenceResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportListingResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportResponse;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportSanctionOptionsResponse;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportReason;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportStatus;
import com.tamdaptit.rentalmanagement.report.entity.enums.ReportTargetType;
import com.tamdaptit.rentalmanagement.report.service.ReportEvidenceService;
import com.tamdaptit.rentalmanagement.report.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ReportController {
    private final ReportService service;
    private final ReportEvidenceService evidences;

    @GetMapping("/reports/reasons")
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<List<ReportReason>> reasons(@RequestParam ReportTargetType targetType) {
        return ApiResponse.success(service.reasons(targetType));
    }

    @PostMapping("/reports")
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<ReportResponse> create(Principal actor, @Valid @RequestBody ReportCreateRequest body) {
        return ApiResponse.success(service.create(actor.getName(), body));
    }

    @GetMapping("/reports/mine")
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<PageResponse<ReportResponse>> mine(
        Principal actor,
        @RequestParam(required = false) ReportStatus status,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(service.mine(actor.getName(), status, page, size));
    }

    @GetMapping("/reports/{id}")
    @PreAuthorize("hasRole('TENANT') or hasAuthority('REPORT_READ')")
    public ApiResponse<ReportDetailResponse> detail(Principal actor, @PathVariable String id) {
        return ApiResponse.success(service.detail(actor.getName(), id));
    }

    @PostMapping(value = "/reports/{id}/evidences", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<ReportEvidenceResponse> upload(
        Principal actor,
        @PathVariable String id,
        @RequestPart("file") MultipartFile file
    ) {
        return ApiResponse.success(evidences.upload(actor.getName(), id, file));
    }

    @GetMapping("/reports/{id}/evidences/{evidenceId}/content")
    @PreAuthorize("hasRole('TENANT') or hasAuthority('REPORT_READ')")
    public ResponseEntity<byte[]> content(
        Principal actor,
        @PathVariable String id,
        @PathVariable String evidenceId
    ) {
        var content = evidences.download(actor.getName(), id, evidenceId);
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(content.contentType()))
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header("X-Content-Type-Options", "nosniff")
            .body(content.bytes());
    }

    @GetMapping("/admin/reports")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<PageResponse<ReportResponse>> admin(
        Principal actor,
        @RequestParam(required = false) ReportStatus status,
        @RequestParam(required = false) ReportReason reasonCode,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "10") int size
    ) {
        return ApiResponse.success(service.admin(actor.getName(), status, reasonCode, page, size));
    }

    @GetMapping("/admin/reports/{id}/sanction-options")
    @PreAuthorize("hasAuthority('REPORT_READ') and hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportSanctionOptionsResponse> sanctionOptions(Principal actor, @PathVariable String id) {
        return ApiResponse.success(service.sanctionOptions(actor.getName(), id));
    }

    @GetMapping("/admin/reports/{id}/listings/{listingId}")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<ReportListingResponse> listingDetail(
        Principal actor,
        @PathVariable String id,
        @PathVariable String listingId
    ) {
        return ApiResponse.success(service.listingDetail(actor.getName(), id, listingId));
    }

    @GetMapping("/admin/reports/summary")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<Map<ReportStatus, Long>> summary(Principal actor) {
        return ApiResponse.success(service.summary(actor.getName()));
    }

    @PostMapping("/admin/reports/{id}/assign")
    @PreAuthorize("hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportResponse> assign(Principal actor, @PathVariable String id) {
        return ApiResponse.success(service.assign(actor.getName(), id));
    }

    @PostMapping("/admin/reports/{id}/resolve")
    @PreAuthorize("hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportResponse> resolve(
        Principal actor,
        @PathVariable String id,
        @Valid @RequestBody ReportResolutionRequest body
    ) {
        return ApiResponse.success(service.close(actor.getName(), id, ReportStatus.RESOLVED, body));
    }

    @PostMapping("/admin/reports/{id}/reject")
    @PreAuthorize("hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportResponse> reject(
        Principal actor,
        @PathVariable String id,
        @Valid @RequestBody ReportResolutionRequest body
    ) {
        return ApiResponse.success(service.close(actor.getName(), id, ReportStatus.REJECTED, body));
    }
}
