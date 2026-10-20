package com.tamdaptit.rentalmanagement.service;

import com.tamdaptit.rentalmanagement.dto.auth.request.LoginRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.LogoutRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.RefreshTokenRequest;
import com.tamdaptit.rentalmanagement.dto.auth.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.dto.auth.response.LoginResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.dto.auth.response.RegisterResponse;

public interface AuthService {

    RegisterResponse register(RegisterRequest request);

    LoginResponse login(LoginRequest request);

    RefreshTokenResponse refreshToken(RefreshTokenRequest request);

    void logout(LogoutRequest request);
}