package com.tamdaptit.rentalmanagement.payment.service.impl;

import com.tamdaptit.rentalmanagement.payment.dto.response.VnPayQueryResponse;
import com.tamdaptit.rentalmanagement.payment.entity.enums.PaymentStatus;
import com.tamdaptit.rentalmanagement.payment.service.PaymentQueryService;
import com.tamdaptit.rentalmanagement.payment.service.PaymentService;
import com.tamdaptit.rentalmanagement.payment.service.VnPayQueryClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentQueryServiceImpl implements PaymentQueryService {
    private final PaymentService payments;
    private final VnPayQueryClient client;

    @Override
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public VnPayQueryResponse check(String userId, String paymentId, boolean admin) {
        var snapshot = payments.getQuerySnapshot(userId, paymentId, admin);
        if (snapshot.status() == PaymentStatus.SUCCESS || snapshot.status() == PaymentStatus.REFUNDED) {
            return new VnPayQueryResponse(payments.getById(userId, paymentId, admin), null, null, "Giao dịch đã được ghi nhận");
        }
        // The network call runs after the snapshot transaction has closed, without holding database locks.
        var verified = client.query(snapshot);
        var payment = payments.applyQueryResult(userId, paymentId, admin, verified);
        return new VnPayQueryResponse(payment, verified.get("vnp_ResponseCode"), verified.get("vnp_TransactionStatus"),
                "00".equals(verified.get("vnp_TransactionStatus")) ? "Đã xác nhận kết quả thanh toán" : "VNPay chưa xác nhận thanh toán thành công");
    }
}
