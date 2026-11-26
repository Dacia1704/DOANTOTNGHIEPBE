package com.tamdaptit.rentalmanagement.report.repository;

import java.util.List;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.report.entity.ReportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence, String> {
    long countByReport_Id(String reportId);

    List<ReportEvidence> findByReport_IdOrderByCreatedAtAscIdAsc(String reportId);

    Optional<ReportEvidence> findByIdAndReport_Id(String id, String reportId);
}
