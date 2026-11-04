package com.tamdaptit.rentalmanagement.payment;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.payment.controller.PaymentController;
import com.tamdaptit.rentalmanagement.payment.dto.response.PaymentResponse;
import com.tamdaptit.rentalmanagement.payment.entity.Payment;
import com.tamdaptit.rentalmanagement.payment.mapper.PaymentMapper;
import com.tamdaptit.rentalmanagement.payment.repository.InvoiceRepository;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.payment.service.PaymentQueryService;
import com.tamdaptit.rentalmanagement.payment.service.PaymentService;
import com.tamdaptit.rentalmanagement.payment.service.impl.PaymentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AdminPaymentApiTest {
    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanListEveryPaymentWithoutChangingLandlordHistory() {
        try (var context = new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            var service = context.getBean(PaymentService.class);
            var result = PageResponse.<PaymentResponse>builder().totalElements(2L).data(List.of()).build();
            when(service.getAllPayments(1, 10)).thenReturn(result);
            SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("admin", "unused", "ROLE_ADMIN"));
            var response = context.getBean(PaymentController.class).all(1, 10);
            assertSame(result, response.getData());
            verify(service).getAllPayments(1, 10);
            verify(service, never()).getHistory(anyString(), anyInt(), anyInt());
        }
    }

    @Test
    void landlordAndTenantCannotReadTheAdminList() {
        try (var context = new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            var controller = context.getBean(PaymentController.class);
            for (var role : List.of("ROLE_LANDLORD", "ROLE_TENANT")) {
                SecurityContextHolder.getContext().setAuthentication(
                        new TestingAuthenticationToken("user", "unused", role));
                assertThrows(AccessDeniedException.class, () -> controller.all(0, 10));
            }
            verifyNoInteractions(context.getBean(PaymentService.class));
        }
    }

    @Test
    void unauthenticatedRequestsCannotReadTheAdminList() {
        try (var context = new AnnotationConfigApplicationContext(SecurityTestConfig.class)) {
            var controller = context.getBean(PaymentController.class);
            assertThrows(AuthenticationCredentialsNotFoundException.class, () -> controller.all(0, 10));
            verifyNoInteractions(context.getBean(PaymentService.class));
        }
    }

    @Test
    void allPaymentsArePagedWithTotalsAndStableNewestFirstOrdering() {
        var repository = mock(PaymentRepository.class);
        var invoices = mock(InvoiceRepository.class);
        var mapper = mock(PaymentMapper.class);
        var service = new PaymentServiceImpl(repository, invoices, null, null, null,
                null, null, null, null, mapper, Clock.systemUTC());
        var first = Payment.builder().id("payment-a").build();
        var second = Payment.builder().id("payment-b").build();
        var dtoA = new PaymentResponse("payment-a", null, null, null, null, null, null, null, false, null, null);
        var dtoB = new PaymentResponse("payment-b", null, null, null, null, null, null, null, true, "LATE_PAYMENT", null);
        when(mapper.toResponse(first, null)).thenReturn(dtoA);
        when(mapper.toResponse(second, null)).thenReturn(dtoB);
        when(repository.findAll(any(Pageable.class))).thenAnswer(call ->
                new PageImpl<>(List.of(first, second), call.getArgument(0, Pageable.class), 5));
        var result = service.getAllPayments(1, 2);
        assertEquals(5, result.getTotalElements());
        assertEquals(1, result.getCurrentPage());
        assertEquals(List.of(dtoA, dtoB), result.getData());
        var pagination = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAll(pagination.capture());
        assertEquals(Sort.by(Sort.Direction.DESC, "createdAt", "id"), pagination.getValue().getSort());
        assertEquals(2, pagination.getValue().getPageSize());
        verify(repository, never()).findByLandlord_Id(anyString(), any());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity
    static class SecurityTestConfig {
        @Bean PaymentService service() { return mock(PaymentService.class); }
        @Bean PaymentQueryService queries() { return mock(PaymentQueryService.class); }
        @Bean PaymentController controller(PaymentService service, PaymentQueryService queries) {
            return new PaymentController(service, queries);
        }
    }
}
