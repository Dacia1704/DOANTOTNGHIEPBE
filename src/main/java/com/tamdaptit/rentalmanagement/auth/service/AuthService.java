package com.tamdaptit.rentalmanagement.auth.service;

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

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request);

    // ===== OTP / Email verification / Password reset =====

    /** Gui OTP qua email de dat lai mat khau. */
    void forgotPassword(ForgotPasswordRequest request);

    /** Xac thuc OTP va cap nhat mat khau moi. */
    void resetPassword(ResetPasswordRequest request);

    /** Gui OTP qua email de xac thuc dia chi email. */
    void sendVerifyEmail(SendVerifyEmailRequest request);

    /** Xac thuc OTP va danh dau email da duoc xac thuc. */
    void verifyEmail(VerifyEmailRequest request);
}
