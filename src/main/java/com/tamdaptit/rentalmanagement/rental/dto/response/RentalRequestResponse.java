package com.tamdaptit.rentalmanagement.rental.dto.response;
import java.time.*;
public record RentalRequestResponse(String id,String listingId,String roomId,String roomNumber,
    String propertyId,String propertyName,String tenantName,String tenantPhone,String tenantEmail,
    String maskedCitizenId,String permanentAddress,LocalDate proposedMoveInDate,LocalDate endDate,
    Integer durationMonths,Integer occupantCount,String message,String status,String resolutionReason,
    LocalDateTime createdAt,LocalDateTime expiresAt,String agreementId,String pdfStatus,String thumbnailUrl,
    RentalRequestRoomResponse roomDetails,RentalCardResponse card) {}
