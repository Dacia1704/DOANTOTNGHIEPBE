package com.tamdaptit.rentalmanagement.rental.service;

/** Access-controlled download of the exact PDF associated with an agreement. */
public interface RentalContractService {
    Content download(String userId, String agreementId, boolean admin);

    record Content(byte[] bytes, String code) {}
}
