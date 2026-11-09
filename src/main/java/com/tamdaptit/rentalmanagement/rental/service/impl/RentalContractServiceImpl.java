package com.tamdaptit.rentalmanagement.rental.service.impl;

import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.rental.service.RentalContractPdfService;
import com.tamdaptit.rentalmanagement.rental.service.RentalContractService;
import com.tamdaptit.rentalmanagement.rental.service.RentalPdfStorage;
import com.tamdaptit.rentalmanagement.rental.service.RentalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RentalContractServiceImpl implements RentalContractService {
    private final RentalService rentals;
    private final RentalPdfStorage storage;

    @Override
    @Transactional(propagation = Propagation.NEVER)
    public Content download(String userId, String agreementId, boolean admin) {
        // Authorization and readiness are checked inside a short read transaction.
        // Network I/O starts only after that transaction has ended.
        var reference = rentals.pdf(userId, agreementId, admin);
        try {
            byte[] bytes = storage.download(reference.publicId());
            if (!RentalContractPdfService.sha256(bytes).equals(reference.hash())) {
                throw new AppException(ErrorCode.CONTRACT_CHANGED);
            }
            return new Content(bytes, reference.code());
        } catch (AppException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new AppException(ErrorCode.PDF_GENERATION_FAILED);
        }
    }
}
