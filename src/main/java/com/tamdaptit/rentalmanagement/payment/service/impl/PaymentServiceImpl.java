package com.tamdaptit.rentalmanagement.payment.service.impl;

import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.payment.config.VnPayConfig;
import com.tamdaptit.rentalmanagement.payment.dto.request.PaymentRequest;
import com.tamdaptit.rentalmanagement.payment.dto.response.*;
import com.tamdaptit.rentalmanagement.payment.entity.Invoice;
import com.tamdaptit.rentalmanagement.payment.entity.Payment;
import com.tamdaptit.rentalmanagement.payment.entity.enums.*;
import com.tamdaptit.rentalmanagement.payment.mapper.PaymentMapper;
import com.tamdaptit.rentalmanagement.payment.repository.InvoiceRepository;
import com.tamdaptit.rentalmanagement.payment.repository.PaymentRepository;
import com.tamdaptit.rentalmanagement.payment.service.PaymentService;
import com.tamdaptit.rentalmanagement.payment.service.VnPaySigner;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.BoostSubscriptionStatus;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderStatus;
import com.tamdaptit.rentalmanagement.subscription.entity.enums.ServiceSubscriptionOrderType;
import com.tamdaptit.rentalmanagement.subscription.repository.BoostPurchaseRepository;
import com.tamdaptit.rentalmanagement.subscription.repository.ServiceSubscriptionOrderRepository;
import com.tamdaptit.rentalmanagement.subscription.service.BoostPurchaseService;
import com.tamdaptit.rentalmanagement.subscription.service.ServiceSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.util.MultiValueMap;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PaymentServiceImpl implements PaymentService {
    private static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
            .withResolverStyle(ResolverStyle.STRICT);
    private final PaymentRepository paymentRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final ServiceSubscriptionOrderRepository orderRepository;
    private final BoostPurchaseRepository boostRepository;
    private final ServiceSubscriptionService subscriptionService;
    private final BoostPurchaseService boostService;
    private final VnPayConfig config;
    private final VnPaySigner signer;
    private final PaymentMapper mapper;
    private final Clock subscriptionClock;

    private LocalDateTime now() {
        return LocalDateTime.now(subscriptionClock).truncatedTo(ChronoUnit.SECONDS);
    }

    @Override
    @Transactional
    public PaymentCheckoutResponse checkout(String landlordId, PaymentRequest request, String ipAddress) {
        signer.requireConfigured();
        User owner = userRepository.findByIdForUpdate(landlordId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (owner.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        LocalDateTime time = now();
        Payment payment = Payment.builder().landlord(owner).type(request.type()).vnPayCreateDate(time)
                .merchantOrderId(UUID.randomUUID().toString().replace("-", ""))
                .paymentMethod(PaymentMethod.VNPAY).expiresAt(time.plusMinutes(15)).build();
        if (request.type() == PaymentType.SERVICE_PACKAGE) {
            subscriptionService.getOrder(landlordId, request.targetId());
            var order = orderRepository.findByIdAndLandlord_Id(request.targetId(), landlordId)
                    .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_ORDER_NOT_FOUND));
            var current = subscriptionService.getCurrent(landlordId);
            if (order.getStatus() != ServiceSubscriptionOrderStatus.PENDING_PAYMENT || !time.isBefore(order.getExpiresAt())
                    || current.scheduledChange() != null
                    || (order.getType() == ServiceSubscriptionOrderType.PURCHASE && current.subscriptionId() != null)
                    || (order.getType() == ServiceSubscriptionOrderType.RENEW && current.subscriptionId() != null
                        && !current.subscriptionId().equals(order.getSubscription().getId()))
                    || (order.getType() == ServiceSubscriptionOrderType.CHANGE && current.subscriptionId() != null
                        && !current.subscriptionId().equals(order.getPreviousSubscription().getId()))
                    || (Boolean.TRUE.equals(order.getImmediateUpgrade())
                        && (current.subscriptionId() == null || !current.subscriptionEndsAt().equals(order.getPreviousEndAt())))
                    || !paymentRepository.findByServiceSubscriptionOrder_IdAndStatus(order.getId(), PaymentStatus.SUCCESS).isEmpty()) {
                throw new AppException(ErrorCode.PAYMENT_CHECKOUT_UNAVAILABLE);
            }
            payment.setServiceSubscriptionOrder(order);
            payment.setAmount(order.getPrice());
            payment.setExpiresAt(order.getExpiresAt());
        } else {
            var boost = boostRepository.findByIdAndLandlord_Id(request.targetId(), landlordId)
                    .orElseThrow(() -> new AppException(ErrorCode.SUBSCRIPTION_NOT_FOUND));
            if (boost.getStatus() != BoostSubscriptionStatus.PENDING_PAYMENT
                    || !time.isBefore(boost.getPaymentExpiresAt())
                    || !paymentRepository.findByBoostSubscription_IdAndStatus(boost.getId(), PaymentStatus.SUCCESS).isEmpty()) {
                throw new AppException(ErrorCode.PAYMENT_CHECKOUT_UNAVAILABLE);
            }
            payment.setBoostSubscription(boost);
            payment.setAmount(boost.getPrice());
            payment.setExpiresAt(boost.getPaymentExpiresAt());
        }
        amount(payment.getAmount());
        for (var pending : paymentRepository.findByLandlord_IdAndStatus(landlordId, PaymentStatus.PENDING)) {
            if (!time.isBefore(pending.getExpiresAt())) {
                pending.setStatus(PaymentStatus.EXPIRED);
            } else if (sameTarget(pending, request)) {
                return new PaymentCheckoutResponse(response(pending), paymentUrl(pending, ipAddress));
            }
        }
        paymentRepository.saveAndFlush(payment);
        return new PaymentCheckoutResponse(response(payment), paymentUrl(payment, ipAddress));
    }

    private boolean sameTarget(Payment payment, PaymentRequest request) {
        return payment.getType() == request.type() && (payment.getServiceSubscriptionOrder() != null
                ? payment.getServiceSubscriptionOrder().getId() : payment.getBoostSubscription().getId()).equals(request.targetId());
    }

    private String amount(BigDecimal amount) {
        // VNPay: integral VND, sent as an integer multiplied by 100 (maximum 12 digits).
        try {
            if (amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 0) {
                throw new ArithmeticException();
            }
            String value = amount.movePointRight(2).toBigIntegerExact().toString();
            if (value.length() > 12) {
                throw new ArithmeticException();
            }
            return value;
        } catch (ArithmeticException exception) {
            throw new AppException(ErrorCode.PAYMENT_AMOUNT_INVALID);
        }
    }

    private String paymentUrl(Payment payment, String ipAddress) {
        Map<String, String> parameters = new TreeMap<>();
        parameters.put("vnp_Version", "2.1.0");
        parameters.put("vnp_Command", "pay");
        parameters.put("vnp_TmnCode", config.getTerminalId());
        parameters.put("vnp_Amount", amount(payment.getAmount()));
        parameters.put("vnp_CurrCode", "VND");
        parameters.put("vnp_TxnRef", payment.getMerchantOrderId());
        parameters.put("vnp_OrderInfo", "Thanh toan " + payment.getMerchantOrderId());
        parameters.put("vnp_OrderType", "other");
        parameters.put("vnp_Locale", "vn");
        parameters.put("vnp_ReturnUrl", config.getReturnUrl());
        parameters.put("vnp_IpAddr", ipAddress);
        parameters.put("vnp_CreateDate", (payment.getVnPayCreateDate() == null ? payment.getCreatedAt() : payment.getVnPayCreateDate()).format(VNP_TIME));
        parameters.put("vnp_ExpireDate", payment.getExpiresAt().format(VNP_TIME));
        return config.getPaymentUrl() + "?" + signer.canonical(parameters) + "&vnp_SecureHash=" + signer.sign(parameters);
    }

    @Override
    @Transactional
    public VnPayIpnResponse processIpn(MultiValueMap<String, String> parameters) {
        if (!signer.verify(parameters) || !config.getTerminalId().equals(parameters.getFirst("vnp_TmnCode"))) {
            return new VnPayIpnResponse("97", "Invalid signature");
        }
        String reference = parameters.getFirst("vnp_TxnRef");
        String ownerId = paymentRepository.findLandlordIdByReference(reference).orElse(null);
        if (ownerId == null) {
            return new VnPayIpnResponse("01", "Order not found");
        }
        userRepository.findByIdForUpdate(ownerId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Payment payment = paymentRepository.findByMerchantOrderId(reference).orElseThrow();
        if (!amount(payment.getAmount()).equals(parameters.getFirst("vnp_Amount"))
                || (parameters.containsKey("vnp_CurrCode") && !"VND".equals(parameters.getFirst("vnp_CurrCode")))) {
            return new VnPayIpnResponse("04", "Invalid amount");
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            return new VnPayIpnResponse("02", "Order already confirmed");
        }
        String responseCode = parameters.getFirst("vnp_ResponseCode");
        String transactionStatus = parameters.getFirst("vnp_TransactionStatus");
        if (responseCode == null || !responseCode.matches("[0-9]{2}")
                || transactionStatus == null || !transactionStatus.matches("[0-9]{2}")) {
            return new VnPayIpnResponse("99", "Invalid response");
        }
        boolean success = "00".equals(responseCode)
                && "00".equals(parameters.getFirst("vnp_TransactionStatus"));
        if (!success) {
            if (payment.getStatus() != PaymentStatus.PENDING) {
                return new VnPayIpnResponse("02", "Order already confirmed");
            }
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.saveAndFlush(payment);
            return new VnPayIpnResponse("00", "Confirm success");
        }
        String providerId = parameters.getFirst("vnp_TransactionNo");
        if (providerId == null || !providerId.matches("[0-9]{1,15}") || providerId.matches("0+")) {
            return new VnPayIpnResponse("99", "Invalid transaction");
        }
        LocalDateTime paidAt;
        try {
            paidAt = LocalDateTime.parse(parameters.getFirst("vnp_PayDate"), VNP_TIME);
        } catch (RuntimeException exception) {
            return new VnPayIpnResponse("99", "Invalid payment date");
        }
        settlePayment(payment, providerId, paidAt);
        return new VnPayIpnResponse("00", "Confirm success");
    }

    private void settlePayment(Payment payment, String providerId, LocalDateTime paidAt) {
        String ownerId = payment.getLandlord().getId();
        payment.setProviderTxnId(providerId);
        payment.setPaidAt(paidAt);
        payment.setStatus(PaymentStatus.SUCCESS);
        paymentRepository.saveAndFlush(payment);
        var successful = payment.getType() == PaymentType.SERVICE_PACKAGE
                ? paymentRepository.findByServiceSubscriptionOrder_IdAndStatus(payment.getServiceSubscriptionOrder().getId(), PaymentStatus.SUCCESS)
                : paymentRepository.findByBoostSubscription_IdAndStatus(payment.getBoostSubscription().getId(), PaymentStatus.SUCCESS);
        if (successful.size() > 1) {
            review(payment, "DUPLICATE_COLLECTION");
        } else if (!paidAt.isBefore(payment.getExpiresAt())) {
            review(payment, "PAID_AFTER_DEADLINE");
        } else if (payment.getType() == PaymentType.SERVICE_PACKAGE) {
            var order = subscriptionService.applyPaidOrder(ownerId, payment.getServiceSubscriptionOrder().getId());
            if (order.status() == ServiceSubscriptionOrderStatus.REQUIRES_REVIEW) {
                review(payment, "SUBSCRIPTION_CONDITIONS_CHANGED");
            }
        } else if (!boostService.applyPaidBoost(ownerId, payment.getBoostSubscription().getId())) {
            review(payment, "BOOST_CONDITIONS_CHANGED");
        }
        invoiceRepository.saveAndFlush(Invoice.builder().payment(payment)
                .type(payment.getType() == PaymentType.SERVICE_PACKAGE ? InvoiceType.SERVICE_PACKAGE : InvoiceType.BOOST_PACKAGE)
                .servicePackage(payment.getServiceSubscriptionOrder() == null ? null : payment.getServiceSubscriptionOrder().getPackageEntity())
                .boostServicePackage(payment.getBoostSubscription() == null ? null : payment.getBoostSubscription().getBoostPackage())
                .invoiceNo("INV-" + payment.getMerchantOrderId()).totalAmount(payment.getAmount()).taxAmount(BigDecimal.ZERO)
                .buyerName(payment.getLandlord().getFullName()).issuedAt(now()).build());
        paymentRepository.flush();
    }

    @Override
    public PaymentQuerySnapshot getQuerySnapshot(String userId, String id, boolean admin) {
        var payment = findVisible(userId, id, admin);
        if (payment.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new AppException(ErrorCode.INVALID_PAYMENT_REFERENCE);
        }
        return new PaymentQuerySnapshot(payment.getId(), payment.getMerchantOrderId(), payment.getAmount(),
                payment.getVnPayCreateDate() == null ? payment.getCreatedAt() : payment.getVnPayCreateDate(), payment.getStatus());
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public PaymentResponse applyQueryResult(String userId, String id, boolean admin, Map<String, String> verified) {
        String reference = verified.get("vnp_TxnRef");
        String ownerId = paymentRepository.findLandlordIdByReference(reference)
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
        if (!admin && !ownerId.equals(userId)) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        userRepository.findByIdForUpdate(ownerId).orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        var payment = paymentRepository.findByMerchantOrderId(reference).orElseThrow();
        if (!payment.getId().equals(id) || payment.getPaymentMethod() != PaymentMethod.VNPAY) {
            throw new AppException(ErrorCode.PAYMENT_NOT_FOUND);
        }
        if (payment.getStatus() == PaymentStatus.SUCCESS || payment.getStatus() == PaymentStatus.REFUNDED) {
            return response(payment);
        }
        if (!"00".equals(verified.get("vnp_ResponseCode")) || !"01".equals(verified.get("vnp_TransactionType"))
                || !config.getTerminalId().equals(verified.get("vnp_TmnCode"))
                || !amount(payment.getAmount()).equals(verified.get("vnp_Amount"))) {
            throw new AppException(ErrorCode.VNPAY_QUERY_INVALID);
        }
        String status = verified.get("vnp_TransactionStatus");
        if ("00".equals(status)) {
            String providerId = verified.getOrDefault("vnp_TransactionNo", "");
            if (!providerId.matches("[0-9]{1,15}") || providerId.matches("0+")) {
                throw new AppException(ErrorCode.VNPAY_QUERY_INVALID);
            }
            LocalDateTime paidAt;
            try {
                paidAt = LocalDateTime.parse(verified.get("vnp_PayDate"), VNP_TIME);
            } catch (RuntimeException exception) {
                throw new AppException(ErrorCode.VNPAY_QUERY_INVALID);
            }
            settlePayment(payment, providerId, paidAt);
        } else if ("02".equals(status) && payment.getStatus() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.saveAndFlush(payment);
        }
        return response(payment);
    }

    private void review(Payment payment, String reason) {
        payment.setRequiresReview(true);
        payment.setReviewReason(reason);
        // Reload targets with locking reads: the callback's reference lookup may have established
        // a REPEATABLE_READ snapshot before another callback committed under the same owner lock.
        if (payment.getServiceSubscriptionOrder() != null) {
            var order = orderRepository.findByIdAndLandlord_Id(payment.getServiceSubscriptionOrder().getId(), payment.getLandlord().getId()).orElseThrow();
            if (order.getStatus() == ServiceSubscriptionOrderStatus.PENDING_PAYMENT || order.getStatus() == ServiceSubscriptionOrderStatus.EXPIRED) {
                order.setStatus(ServiceSubscriptionOrderStatus.REQUIRES_REVIEW);
            }
        }
        if (payment.getBoostSubscription() != null) {
            var boost = boostRepository.findByIdAndLandlord_Id(payment.getBoostSubscription().getId(), payment.getLandlord().getId()).orElseThrow();
            if (boost.getStatus() == BoostSubscriptionStatus.PENDING_PAYMENT) {
                boost.setStatus(BoostSubscriptionStatus.CANCELLED);
            }
        }
    }

    @Override
    public VnPayReturnResponse verifyReturn(MultiValueMap<String, String> parameters) {
        boolean valid = signer.verify(parameters) && config.getTerminalId().equals(parameters.getFirst("vnp_TmnCode"));
        return new VnPayReturnResponse(valid, valid ? parameters.getFirst("vnp_TxnRef") : null,
                valid ? parameters.getFirst("vnp_ResponseCode") : null,
                valid ? parameters.getFirst("vnp_TransactionStatus") : null,
                valid ? "Kết quả từ trình duyệt; theo dõi trạng thái thanh toán để chờ xác nhận IPN" : "Chữ ký không hợp lệ");
    }

    private Payment findVisible(String userId, String id, boolean admin) {
        return (admin ? paymentRepository.findById(id) : paymentRepository.findByIdAndLandlord_Id(id, userId))
                .orElseThrow(() -> new AppException(ErrorCode.PAYMENT_NOT_FOUND));
    }

    private PaymentResponse response(Payment payment) {
        return mapper.toResponse(payment, invoiceRepository.findByPayment_Id(payment.getId()).orElse(null));
    }

    @Override
    public PaymentResponse getById(String userId, String id, boolean admin) {
        return response(findVisible(userId, id, admin));
    }

    @Override
    public InvoiceResponse getInvoice(String userId, String paymentId, boolean admin) {
        var payment = findVisible(userId, paymentId, admin);
        return mapper.toResponse(invoiceRepository.findByPayment_Id(payment.getId())
                .orElseThrow(() -> new AppException(ErrorCode.INVOICE_NOT_FOUND)));
    }

    @Override
    public PageResponse<PaymentResponse> getHistory(String userId, int page, int size) {
        return page(paymentRepository.findByLandlord_Id(userId, pageable(page, size)));
    }

    @Override
    public PageResponse<PaymentResponse> getReviewQueue(int page, int size) {
        return page(paymentRepository.findByRequiresReviewTrue(pageable(page, size)));
    }

    private PageRequest pageable(int page, int size) {
        return PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, 100), Sort.by(Sort.Direction.DESC, "createdAt", "id"));
    }

    private PageResponse<PaymentResponse> page(Page<Payment> result) {
        return PageResponse.<PaymentResponse>builder().currentPage(result.getNumber()).pageSize(result.getSize())
                .totalPages(result.getTotalPages()).totalElements(result.getTotalElements())
                .data(result.getContent().stream().map(this::response).toList()).build();
    }
}
