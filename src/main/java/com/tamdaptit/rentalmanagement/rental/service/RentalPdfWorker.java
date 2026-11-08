package com.tamdaptit.rentalmanagement.rental.service;

import com.tamdaptit.rentalmanagement.rental.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.*;

@Component @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name="app.rental.jobs-enabled",havingValue="true",matchIfMissing=true)
public class RentalPdfWorker {
    private final RentalPdfJobs jobs;
    private final RentalService rentals;
    private final RentalAgreementRepository agreements;
    private final RentalRequestRepository requests;
    private final RentalContractPdfService renderer;
    private final RentalPdfStorage storage;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    @Scheduled(fixedDelayString="${app.rental.job-delay-ms:5000}")
    public void run() {
        var now=LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        for(String id:agreements.expired(now,PageRequest.of(0,25))) safely(()->rentals.expireAgreement(id));
        for(String id:requests.expired(now,PageRequest.of(0,25))) safely(()->rentals.expireRequest(id));
        for(String id:agreements.pdfJobs(now,PageRequest.of(0,5))) safely(()->process(id));
    }
    public void process(String id) {
        var claim=jobs.claim(id); if(claim==null) return;
        try {
            byte[] bytes=renderer.render(rentals.readSnapshot(claim.snapshot()));
            storage.upload(claim.publicId(),bytes);
            if(!jobs.complete(claim,RentalContractPdfService.sha256(bytes))) cleanup(claim.publicId());
        } catch(Exception ex) {
            // Durable intent handles cleanup even if upload succeeded but completion/cleanup failed.
            safely(()->jobs.fail(claim));
            log.warn("Rental PDF attempt failed: agreement={}, claim={}",id,claim.token());
        }
    }
    @Scheduled(fixedDelayString="${app.rental.cleanup-delay-ms:300000}")
    public void reconcile() {
        // A stale process uploading after cleanup is harmless: another pass retries the durable intent.
        var ids=jdbc.queryForList("""
            SELECT p.public_id FROM rental_pdf_assets p LEFT JOIN rental_agreements a ON a.id=p.agreement_id
            WHERE p.created_at<? AND (a.pdf_public_id IS NULL OR a.pdf_public_id<>p.public_id)
              AND (a.pdf_lease_until IS NULL OR a.pdf_lease_until<?)
            ORDER BY COALESCE(p.last_cleanup_at,p.created_at) LIMIT 25
            """,String.class,LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minusMinutes(20),LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).minusMinutes(5));
        for(String id:ids) cleanup(id);
    }
    private void cleanup(String id) {
        try { storage.delete(id); } catch(Exception ex) { log.warn("Rental PDF cleanup will retry"); }
        jdbc.update("UPDATE rental_pdf_assets SET last_cleanup_at=? WHERE public_id=?",LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS),id);
        // Keep intents for reconciliation: a timed-out HTTP upload could still finish remotely.
    }
    private void safely(Runnable action) { try { action.run(); } catch(Exception ex) { log.warn("Rental background operation will retry ({})",ex.getClass().getSimpleName()); } }
}
