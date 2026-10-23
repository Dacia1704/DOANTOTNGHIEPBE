package com.tamdaptit.rentalmanagement.auth.controller;

import com.tamdaptit.rentalmanagement.auth.dto.request.ForgotPasswordRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.LoginRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.LogoutRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.RefreshTokenRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.ResetPasswordRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.SendVerifyEmailRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.VerifyEmailRequest;
import com.tamdaptit.rentalmanagement.auth.dto.response.LoginResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RegisterResponse;
import com.tamdaptit.rentalmanagement.auth.service.AuthService;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, làm mới token, đăng xuất, OTP và xác thực email")
public class AuthController {

    AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Đăng ký tài khoản mới", description = "Chỉ cho phép đăng ký với vai trò TENANT hoặc LANDLORD")
    public ApiResponse<RegisterResponse> register(@RequestBody @Valid RegisterRequest request) {
        return ApiResponse.success(
                "Đăng ký thành công",
                authService.register(request)
        );
    }

    @PostMapping("/login")
    @Operation(summary = "Đăng nhập", description = "Trả về access token, refresh token và danh sách quyền")
    public ApiResponse<LoginResponse> login(@RequestBody @Valid LoginRequest request) {
        return ApiResponse.success(
                "Đăng nhập thành công",
                authService.login(request)
        );
    }

    @PostMapping("/refresh")
    @Operation(summary = "Làm mới token", description = "Đổi refresh token cũ (đã bị revoke) lấy cặp token mới")
    public ApiResponse<RefreshTokenResponse> refresh(@RequestBody @Valid RefreshTokenRequest request) {
        return ApiResponse.success(
                "Làm mới token thành công",
                authService.refreshToken(request)
        );
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Đăng xuất", description = "Revoke refresh token hiện tại")
    public ApiResponse<String> logout(@RequestBody @Valid LogoutRequest request) {
        authService.logout(request);
        return ApiResponse.success("Đăng xuất thành công", null);
    }

    // =====================================================================
    // OTP - Quên mật khẩu
    // =====================================================================

    @PostMapping("/forgot-password")
    @Operation(
            summary = "Quên mật khẩu",
            description = "Gửi mã OTP 6 số về email. Giới hạn 1 lần / 60 giây / email.")
    public ApiResponse<String> forgotPassword(@RequestBody @Valid ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        return ApiResponse.success("Nếu email tồn tại trong hệ thống, mã OTP đã được gửi", null);
    }

    @PostMapping("/reset-password")
    @Operation(
            summary = "Đặt lại mật khẩu",
            description = "Xác thực OTP và cập nhật mật khẩu mới. OTP chỉ dùng được một lần.")
    public ApiResponse<String> resetPassword(@RequestBody @Valid ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success("Đặt lại mật khẩu thành công", null);
    }

    // =====================================================================
    // OTP - Xác thực email
    // =====================================================================

    @PostMapping("/send-verify-email")
    @Operation(
            summary = "Gửi OTP xác thực email",
            description = "Gửi mã OTP 6 số về email để xác thực địa chỉ email. Giới hạn 1 lần / 60 giây.")
    public ApiResponse<String> sendVerifyEmail(@RequestBody @Valid SendVerifyEmailRequest request) {
        authService.sendVerifyEmail(request);
        return ApiResponse.success("Mã OTP xác thực email đã được gửi", null);
    }

    @PostMapping("/verify-email")
    @Operation(
            summary = "Xác thực email",
            description = "Xác thực địa chỉ email bằng mã OTP. OTP chỉ dùng được một lần.")
    public ApiResponse<String> verifyEmail(@RequestBody @Valid VerifyEmailRequest request) {
        authService.verifyEmail(request);
        return ApiResponse.success("Xác thực email thành công", null);
    }
}
