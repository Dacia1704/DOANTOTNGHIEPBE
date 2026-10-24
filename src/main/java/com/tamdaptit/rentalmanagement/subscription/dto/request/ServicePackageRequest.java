package com.tamdaptit.rentalmanagement.subscription.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

public record ServicePackageRequest(
        @NotBlank(message = "Mã gói không được để trống")
        @Size(max = 50, message = "Mã gói tối đa 50 ký tự")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "Mã gói chỉ gồm chữ, số, dấu gạch ngang và gạch dưới")
        String code,

        @NotBlank(message = "Tên gói không được để trống")
        @Size(max = 100, message = "Tên gói tối đa 100 ký tự")
        String name,

        @PositiveOrZero(message = "Giới hạn tin không được âm")
        Integer maxListings,

        @NotNull(message = "Số suất cao không được để trống")
        @PositiveOrZero(message = "Số suất cao không được âm")
        Integer highPrioritySlots,

        @NotNull(message = "Số suất thấp không được để trống")
        @PositiveOrZero(message = "Số suất thấp không được âm")
        Integer lowPrioritySlots,

        @NotNull(message = "Mức ưu tiên mặc định không được để trống")
        @Min(value = 0, message = "Mức mặc định chỉ là 0 hoặc 1")
        @Max(value = 1, message = "Mức mặc định chỉ là 0 hoặc 1")
        Integer defaultPriorityLevel,

        @NotNull(message = "Quyền thống kê không được để trống")
        Boolean hasStats,

        @NotNull(message = "Trạng thái bán không được để trống")
        Boolean active) {
}
