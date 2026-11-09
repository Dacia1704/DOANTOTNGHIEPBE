package com.tamdaptit.rentalmanagement.report.controller;

import com.tamdaptit.rentalmanagement.common.dto.*;
import com.tamdaptit.rentalmanagement.report.dto.request.*;
import com.tamdaptit.rentalmanagement.report.dto.response.*;
import com.tamdaptit.rentalmanagement.report.entity.enums.*;
import com.tamdaptit.rentalmanagement.report.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.security.Principal;
import java.util.*;

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
    public ApiResponse<ReportResponse> create(Principal actor,@Valid @RequestBody ReportCreateRequest body) {
        return ApiResponse.success(service.create(actor.getName(),body));
    }
    @GetMapping("/reports/mine")
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<PageResponse<ReportResponse>> mine(Principal actor,@RequestParam(required=false) ReportStatus status,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.mine(actor.getName(),status,page,size));
    }
    @GetMapping("/reports/{id}")
    @PreAuthorize("hasRole('TENANT') or hasAuthority('REPORT_READ')")
    public ApiResponse<ReportDetailResponse> detail(Principal actor,@PathVariable String id) {
        return ApiResponse.success(service.detail(actor.getName(),id));
    }
    @PostMapping(value="/reports/{id}/evidences",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('TENANT')")
    public ApiResponse<ReportEvidenceResponse> upload(Principal actor,@PathVariable String id,@RequestPart("file") MultipartFile file) {
        return ApiResponse.success(evidences.upload(actor.getName(),id,file));
    }
    @GetMapping("/reports/{id}/evidences/{evidenceId}/content")
    @PreAuthorize("hasRole('TENANT') or hasAuthority('REPORT_READ')")
    public ResponseEntity<byte[]> content(Principal actor,@PathVariable String id,@PathVariable String evidenceId) {
        var content=evidences.download(actor.getName(),id,evidenceId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(content.contentType()))
            .cacheControl(CacheControl.noStore().cachePrivate())
            .header("X-Content-Type-Options","nosniff").body(content.bytes());
    }
    @GetMapping("/admin/reports")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<PageResponse<ReportResponse>> admin(Principal actor,@RequestParam(required=false) ReportStatus status,
        @RequestParam(required=false) ReportReason reasonCode,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="10") int size) {
        return ApiResponse.success(service.admin(actor.getName(),status,reasonCode,page,size));
    }
    @GetMapping("/admin/reports/summary")
    @PreAuthorize("hasAuthority('REPORT_READ')")
    public ApiResponse<Map<ReportStatus,Long>> summary(Principal actor) {
        return ApiResponse.success(service.summary(actor.getName()));
    }
    @PostMapping("/admin/reports/{id}/assign")
    @PreAuthorize("hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportResponse> assign(Principal actor,@PathVariable String id) {
        return ApiResponse.success(service.assign(actor.getName(),id));
    }
    @PostMapping("/admin/reports/{id}/resolve")
    @PreAuthorize("hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportResponse> resolve(Principal actor,@PathVariable String id,@Valid @RequestBody ReportResolutionRequest body) {
        return ApiResponse.success(service.close(actor.getName(),id,ReportStatus.RESOLVED,body));
    }
    @PostMapping("/admin/reports/{id}/reject")
    @PreAuthorize("hasAuthority('REPORT_HANDLE')")
    public ApiResponse<ReportResponse> reject(Principal actor,@PathVariable String id,@Valid @RequestBody ReportResolutionRequest body) {
        return ApiResponse.success(service.close(actor.getName(),id,ReportStatus.REJECTED,body));
    }
}
