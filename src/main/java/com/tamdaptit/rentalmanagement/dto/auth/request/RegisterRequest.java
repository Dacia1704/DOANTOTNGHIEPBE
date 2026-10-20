package com.tamdaptit.rentalmanagement.dto.auth.request;

import com.tamdaptit.rentalmanagement.entity.enums.RoleCode;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterRequest {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(max = 100, message = "Email không được vượt quá 100 ký tự")
    String email;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
            regexp = "^(\\+84|84|0)[0-9]{9}$",
            message = "Số điện thoại không đúng định dạng (VD: 0901234567)"
    )
    String phone;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 100, message = "Mật khẩu phải có từ 8 đến 100 ký tự")
    String password;

    @NotBlank(message = "Họ và tên không được để trống")
    @Size(max = 100, message = "Họ và tên không được vượt quá 100 ký tự")
    String fullName;

    @Size(max = 20, message = "Số CCCD/CMND không được vượt quá 20 ký tự")
    String citizenId;

    /**
     * Vai tro dang ky. Chi chap nhan TENANT hoac LANDLORD.
     * ADMIN duoc gan boi admin qua API noi bo, khong cho dang ky tu public.
     */
    @NotNull(message = "Vui lòng chọn loại tài khoản")
    RoleCode roleCode;
}