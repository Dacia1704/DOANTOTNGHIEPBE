package com.tamdaptit.rentalmanagement.report.service.impl;

import java.util.UUID;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.report.dto.response.ReportEvidenceResponse;
import com.tamdaptit.rentalmanagement.report.entity.ReportEvidence;
import com.tamdaptit.rentalmanagement.report.mapper.ReportMapper;
import com.tamdaptit.rentalmanagement.report.repository.ReportEvidenceRepository;
import com.tamdaptit.rentalmanagement.report.repository.ReportRepository;
import com.tamdaptit.rentalmanagement.report.service.ReportEvidenceService;
import com.tamdaptit.rentalmanagement.report.service.ReportEvidenceStorage;
import com.tamdaptit.rentalmanagement.report.service.ReportService;
import com.tamdaptit.rentalmanagement.storage.service.ImageValidator;
import com.tamdaptit.rentalmanagement.storage.service.StorageFolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.IMAGE_UPLOAD_FAILED;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.REPORT_EVIDENCE_NOT_FOUND;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.REPORT_EVIDENCE_UNAVAILABLE;
import static com.tamdaptit.rentalmanagement.common.exception.ErrorCode.REPORT_NOT_FOUND;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportEvidenceServiceImpl implements ReportEvidenceService {
    private final ReportService service;
    private final ReportRepository reports;
    private final ReportEvidenceRepository evidences;
    private final ReportEvidenceStorage storage;
    private final ImageValidator validator;
    private final ReportMapper mapper;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.NEVER)
    @Override
    public ReportEvidenceResponse upload(
        String actor,
        String reportId,
        MultipartFile file
    ) {
        service.checkUpload(actor, reportId);
        byte[] bytes = validator.validate(file);
        String id = UUID.randomUUID().toString();
        String publicId = "rental-management/" + StorageFolder.forReport(reportId) + "/" + id
            + ("image/png".equals(file.getContentType()) ? ".png" : ".jpg");
        try {
            storage.upload(publicId, bytes);
            var tx = new TransactionTemplate(transactionManager);
            tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
            return tx.execute(status -> {
                var report = reports.lock(reportId).orElseThrow(() -> new AppException(REPORT_NOT_FOUND));
                service.checkUpload(actor, reportId);
                var evidence = ReportEvidence.builder()
                    .report(report)
                    .imagePublicId(publicId)
                    .imageUrl("/api/reports/" + reportId + "/evidences/" + id + "/content")
                    .build();
                // Hibernate generates the evidence ID; replace the URL after persisting.
                evidence = evidences.saveAndFlush(evidence);
                evidence.setImageUrl("/api/reports/" + reportId + "/evidences/" + evidence.getId() + "/content");
                evidences.flush();
                return mapper.toResponse(evidence);
            });
        } catch (Exception ex) {
            cleanup(publicId);
            if (ex instanceof AppException app) {
                throw app;
            }

            if (ex instanceof DataAccessException data) {
                throw data;
            }

            throw new AppException(IMAGE_UPLOAD_FAILED);
        }
    }

    @Transactional(propagation = Propagation.NEVER)
    @Override
    public Content download(
        String actor,
        String reportId,
        String evidenceId
    ) {
        String publicId = new TransactionTemplate(transactionManager).execute(status -> {
            service.detail(actor, reportId);
            return evidences.findByIdAndReport_Id(evidenceId, reportId)
                .orElseThrow(() -> new AppException(REPORT_EVIDENCE_NOT_FOUND))
                .getImagePublicId();
        });
        // Never follow legacy public image URLs or a client-supplied URL.
        String prefix = "rental-management/" + StorageFolder.forReport(reportId) + "/";
        if (
            publicId == null ||
                !publicId.startsWith(prefix) ||
            !(publicId.endsWith(".png") || publicId.endsWith(".jpg"))
        ) {
            throw new AppException(REPORT_EVIDENCE_UNAVAILABLE);
        }

        try {
            return new Content(storage.download(publicId), publicId.endsWith(".png") ? "image/png" : "image/jpeg");
        } catch (Exception ex) {
            throw new AppException(REPORT_EVIDENCE_UNAVAILABLE);
        }
    }

    private void cleanup(String publicId) {
        try {
            storage.delete(publicId);
        } catch (Exception ex) {
            // Asset ID only, no image contents or signed URLs. Operators can retry this exact asset.
            log.error("Report evidence cleanup required for authenticated asset {}", publicId);
        }
    }
}
