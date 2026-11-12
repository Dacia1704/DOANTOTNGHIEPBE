package com.tamdaptit.rentalmanagement.rental.service;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.rental.dto.request.RentalRequestCreateRequest;
import com.tamdaptit.rentalmanagement.rental.dto.request.RentalAgreementConfirmRequest;
import com.tamdaptit.rentalmanagement.rental.dto.request.RentalCheckoutRequest;
import com.tamdaptit.rentalmanagement.rental.dto.response.RentalRequestResponse;
import com.tamdaptit.rentalmanagement.rental.dto.response.RentalAgreementResponse;
import com.tamdaptit.rentalmanagement.rental.dto.response.RentalSummaryResponse;
import com.tamdaptit.rentalmanagement.rental.entity.enums.RentalRequestStatus;
import java.util.Map;

public interface RentalService {
    RentalRequestResponse create(String userId, RentalRequestCreateRequest body);
    RentalAgreementResponse accept(String userId, String id);
    RentalRequestResponse cancelRequest(String userId, String id, boolean landlord, String reason);
    RentalAgreementResponse confirm(String userId, String id, RentalAgreementConfirmRequest body);
    RentalAgreementResponse decline(String userId, String id);
    RentalAgreementResponse checkout(String userId, String id, RentalCheckoutRequest body);
    void expireRequest(String id);
    void expireAgreement(String id);
    RentalRequestResponse request(String userId, String id, boolean admin);
    RentalAgreementResponse agreement(String userId, String id, boolean admin);
    PdfReference pdf(String userId, String id, boolean admin);
    PageResponse<RentalRequestResponse> requests(String user, boolean owner, int page, int size,
            RentalRequestStatus status, String property, String room);
    PageResponse<RentalAgreementResponse> agreements(String user, boolean owner, int page, int size);
    PageResponse<RentalRequestResponse> myRequests(String user,int page,int size,RentalRequestStatus status,String q,String sort);
    PageResponse<RentalAgreementResponse> myAgreements(String user,int page,int size,String q,String sort);
    RentalSummaryResponse mySummary(String user);
    Map<String, String> readSnapshot(String snapshot);

    record PdfReference(String publicId, String hash, String code) {}
}
