package com.tamdaptit.rentalmanagement.payment.repository;

import com.tamdaptit.rentalmanagement.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByPayment_Id(String paymentId);
}
