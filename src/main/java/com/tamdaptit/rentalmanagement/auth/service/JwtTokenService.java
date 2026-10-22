package com.tamdaptit.rentalmanagement.auth.service;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.util.List;

public interface JwtTokenService {

    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    Claims validateToken(String token);

    List<String> getAuthorities(Claims claims);

    List<String> buildAuthorities(User user);

    /** Lay userId (UUID string) tu access token. */
    String getUserId(String token);

    /** Thoi diem token het han (access token). */
    LocalDateTime getAccessTokenExpiresAt();

    /** Kiem tra token co phai access token hay khong. */
    boolean isAccessToken(String token);

    /** Kiem tra token co phai refresh token hay khong. */
    boolean isRefreshToken(String token);
}
