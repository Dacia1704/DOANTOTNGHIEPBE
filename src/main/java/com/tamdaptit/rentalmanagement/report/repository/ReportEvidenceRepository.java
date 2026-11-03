package com.tamdaptit.rentalmanagement.report.repository;
import com.tamdaptit.rentalmanagement.report.entity.ReportEvidence;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ReportEvidenceRepository extends JpaRepository<ReportEvidence,String> {
    long countByReport_Id(String reportId);
    List<ReportEvidence> findByReport_IdOrderByCreatedAtAscIdAsc(String reportId);
    Optional<ReportEvidence> findByIdAndReport_Id(String id,String reportId);
}
