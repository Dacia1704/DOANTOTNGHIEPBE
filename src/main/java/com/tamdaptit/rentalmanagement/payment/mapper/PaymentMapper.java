package com.tamdaptit.rentalmanagement.payment.mapper;

import com.tamdaptit.rentalmanagement.payment.dto.response.InvoiceResponse;
import com.tamdaptit.rentalmanagement.payment.dto.response.PaymentResponse;
import com.tamdaptit.rentalmanagement.payment.entity.Invoice;
import com.tamdaptit.rentalmanagement.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PaymentMapper {
    @Mapping(target = "id", source = "payment.id")
    @Mapping(target = "type", source = "payment.type")
    @Mapping(target = "invoiceId", source = "invoice.id")
    @Mapping(target = "targetId", expression = "java(payment.getServiceSubscriptionOrder() != null ? payment.getServiceSubscriptionOrder().getId() : payment.getBoostSubscription().getId())")
    PaymentResponse toResponse(Payment payment, Invoice invoice);

    InvoiceResponse toResponse(Invoice invoice);
}
