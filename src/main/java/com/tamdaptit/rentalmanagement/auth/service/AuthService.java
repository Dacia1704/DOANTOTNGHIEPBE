package com.tamdaptit.rentalmanagement.auth.service;

import com.tamdaptit.rentalmanagement.auth.dto.request.LoginRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.LogoutRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.RefreshTokenRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.auth.dto.response.LoginResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}
