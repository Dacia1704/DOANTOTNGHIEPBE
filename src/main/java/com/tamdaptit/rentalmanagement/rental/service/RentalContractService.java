package com.tamdaptit.rentalmanagement.rental.service;

import java.io.IOException;
import java.util.Map;

/** Tao, phat hanh va tai PDF hop dong; cac buoc ghi DB co transaction rieng. */
public interface RentalContractService {
    Content download(
        String userId,
        String agreementId,
        boolean admin
    );

    RenderedPdf render(Map<String, String> snapshot) throws IOException;

    PdfClaim claim(String agreementId);

    boolean complete(PdfClaim claim, String hash);

    void fail(PdfClaim claim);

    record Content(byte[] bytes, String code) {}

    record RenderedPdf(byte[] bytes, String sha256) {}

    record PdfClaim(
        String id,
        String token,
        String publicId,
        String snapshot
    ) {}
}
