package com.tamdaptit.rentalmanagement.controller;

import com.tamdaptit.rentalmanagement.common.ApiResponse;
import com.tamdaptit.rentalmanagement.dto.auth.request.LoginRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.LogoutRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.RefreshTokenRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.dto.auth.response.LoginResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RegisterResponse;
import com.tamdaptit.rentalmanagement.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Authentication", description = "Đăng ký, đăng nhập, làm mới token và đăng xuất")
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
}