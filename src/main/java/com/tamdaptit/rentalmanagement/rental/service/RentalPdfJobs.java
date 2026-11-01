package com.tamdaptit.rentalmanagement.rental.service;

import com.tamdaptit.rentalmanagement.rental.entity.*;
import com.tamdaptit.rentalmanagement.rental.entity.enums.*;
import com.tamdaptit.rentalmanagement.rental.repository.*;
import com.tamdaptit.rentalmanagement.room.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/** All job transactions lock Room first. Rendering and storage HTTP happen in RentalPdfWorker outside these transactions. */
@Service @RequiredArgsConstructor
public class RentalPdfJobs {
    private final RoomRepository rooms;
    private final RentalAgreementRepository agreements;
    private final RentalEvents events;
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public record Claim(String id,String token,String publicId,String snapshot) {}
    @Transactional
    public Claim claim(String id) {
        var a=locked(id); var now=LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        if(a.getStatus()!=RentalAgreementStatus.PENDING_CONFIRMATION || a.getContractSnapshot()==null
                || a.getConfirmationExpiresAt()==null || !a.getConfirmationExpiresAt().isAfter(now)) return null;
        if(a.getPdfStatus()==ContractPdfStatus.READY) return null;
        if(a.getPdfStatus()==ContractPdfStatus.PROCESSING && a.getPdfLeaseUntil()!=null && a.getPdfLeaseUntil().isAfter(now)) return null;
        if(a.getPdfStatus()==ContractPdfStatus.PROCESSING && a.getPdfAttemptCount()>=6) {
            a.setPdfStatus(ContractPdfStatus.FAILED); a.setPdfLeaseUntil(null);
            a.setPdfError("Đã hết số lần thử tạo PDF. Vui lòng liên hệ hỗ trợ.");
            return null;
        }
        if(a.getPdfStatus()!=ContractPdfStatus.PROCESSING && (a.getPdfAttemptCount()>=6 || a.getPdfNextRetryAt()!=null && a.getPdfNextRetryAt().isAfter(now))) return null;
        String token=UUID.randomUUID().toString(),publicId="rental-management/contracts/"+id+"/"+token+".pdf";
        a.setPdfStatus(ContractPdfStatus.PROCESSING); a.setPdfClaimToken(token); a.setPdfLeaseUntil(now.plusMinutes(5));
        a.setPdfAttemptCount(a.getPdfAttemptCount()+1);
        jdbc.update("INSERT INTO rental_pdf_assets(public_id,agreement_id,created_at) VALUES(?,?,?)",publicId,id,now);
        return new Claim(id,token,publicId,a.getContractSnapshot());
    }
    @Transactional
    public boolean complete(Claim claim,String hash) {
        var a=locked(claim.id());
        if(!Objects.equals(a.getPdfClaimToken(),claim.token()) || a.getPdfStatus()!=ContractPdfStatus.PROCESSING
            || a.getStatus()!=RentalAgreementStatus.PENDING_CONFIRMATION || !a.getConfirmationExpiresAt().isAfter(LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS))) return false;
        a.setPdfPublicId(claim.publicId()); a.setPdfUrl(null); a.setPdfSha256(hash); a.setPdfGeneratedAt(LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        a.setPdfStatus(ContractPdfStatus.READY); a.setPdfError(null); a.setPdfLeaseUntil(null);
        events.emit(null,a.getTenant().getId(),a.getId(),"RENTAL_AGREEMENT","PDF_READY",hash);
        events.emit(null,a.getLandlord().getId(),a.getId(),"RENTAL_AGREEMENT","PDF_READY",hash);
        return true;
    }
    @Transactional
    public void fail(Claim claim) {
        var a=locked(claim.id());
        if(!Objects.equals(a.getPdfClaimToken(),claim.token()) || a.getPdfStatus()!=ContractPdfStatus.PROCESSING) return;
        a.setPdfStatus(ContractPdfStatus.FAILED); a.setPdfError("Không thể tạo/lưu PDF. Vui lòng thử lại sau.");
        a.setPdfLeaseUntil(null); a.setPdfNextRetryAt(LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS).plusSeconds(Math.min(1800,30L << Math.min(a.getPdfAttemptCount(),6))));
        // Notify once per agreement failure lifecycle; retries remain observable via attempt count.
        if(a.getPdfAttemptCount()==1) {
            events.emit(null,a.getTenant().getId(),a.getId(),"RENTAL_AGREEMENT","PDF_FAILED",null);
            events.emit(null,a.getLandlord().getId(),a.getId(),"RENTAL_AGREEMENT","PDF_FAILED",null);
        }
    }
    private RentalAgreement locked(String id) {
        rooms.findByIdForUpdate(agreements.roomId(id).orElseThrow()).orElseThrow();
        return agreements.lock(id).orElseThrow();
    }
}
