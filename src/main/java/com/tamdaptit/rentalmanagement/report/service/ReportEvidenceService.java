package com.tamdaptit.rentalmanagement.report.service;

import com.tamdaptit.rentalmanagement.report.dto.response.ReportEvidenceResponse;
import org.springframework.web.multipart.MultipartFile;

public interface ReportEvidenceService {
    ReportEvidenceResponse upload(String actor, String reportId, MultipartFile file);
    Content download(String actor, String reportId, String evidenceId);

    record Content(byte[] bytes, String contentType) {}
}
