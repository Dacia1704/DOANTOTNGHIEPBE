package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record ServicePackagePriceRequest(
        @NotNull(message = "Thời hạn không được để trống")
        @Positive(message = "Số tháng phải lớn hơn 0")
        Integer durationMonths,

        @NotNull(message = "Giá không được để trống")
        @DecimalMin(value = "0", message = "Giá không được âm")
        @Digits(integer = 10, fraction = 2, message = "Giá tối đa 10 chữ số nguyên và 2 chữ số thập phân")
        BigDecimal price,

        @NotNull(message = "Trạng thái bán không được để trống")
        Boolean active) {
}
