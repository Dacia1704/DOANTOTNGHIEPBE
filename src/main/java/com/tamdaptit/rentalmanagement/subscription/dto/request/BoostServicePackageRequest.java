package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import java.math.BigDecimal;

public record BoostServicePackageRequest(
        @NotBlank(message = "Mã gói không được để trống")
        @Size(max = 50, message = "Mã gói tối đa 50 ký tự")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Mã gói chỉ gồm chữ, số, dấu gạch ngang và gạch dưới")
        String code,

        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 100, message = "Tên gói tối đa 100 ký tự")
        String name,

        @NotNull(message = "Số suất cao không được để trống")
        @PositiveOrZero(message = "Số suất cao không được âm")
        Integer highPrioritySlots,

        @NotNull(message = "Số suất thấp không được để trống")
        @PositiveOrZero(message = "Số suất thấp không được âm")
        Integer lowPrioritySlots,

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
