package com.tamdaptit.rentalmanagement.payment.repository;

import java.util.Optional;

import com.tamdaptit.rentalmanagement.payment.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, String> {
    Optional<Invoice> findByPayment_Id(String paymentId);
}
