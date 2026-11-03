package com.tamdaptit.rentalmanagement.rental.dto.response;
import java.math.BigDecimal;
import java.time.*;
public record RentalAgreementResponse(String id,String requestId,String agreementCode,String roomId,
    String roomNumber,String propertyName,String tenantId,String tenantName,String landlordId,String landlordName,BigDecimal price,
    BigDecimal deposit,BigDecimal electricityPrice,BigDecimal waterPrice,BigDecimal serviceFee,
    LocalDate startDate,LocalDate endDate,String status,String pdfStatus,String pdfSha256,
    LocalDateTime confirmationExpiresAt,LocalDateTime landlordConfirmedAt,LocalDateTime tenantConfirmedAt,
    boolean canConfirm,boolean canDecline,boolean canDownloadPdf,boolean canCheckout,String cancelReason) {}
