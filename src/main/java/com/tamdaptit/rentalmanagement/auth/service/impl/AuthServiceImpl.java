package com.tamdaptit.rentalmanagement.auth.service.impl;

import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import com.tamdaptit.rentalmanagement.auth.dto.request.LoginRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.LogoutRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.RefreshTokenRequest;
import com.tamdaptit.rentalmanagement.auth.dto.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.auth.dto.response.LoginResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RefreshTokenResponse;
import com.tamdaptit.rentalmanagement.auth.dto.response.RegisterResponse;
import com.tamdaptit.rentalmanagement.auth.entity.RefreshToken;
import com.tamdaptit.rentalmanagement.auth.mapper.AuthMapper;
import com.tamdaptit.rentalmanagement.auth.repository.RefreshTokenRepository;
import com.tamdaptit.rentalmanagement.auth.service.AuthService;
import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.common.util.HashUtils;
import com.tamdaptit.rentalmanagement.identity.entity.Role;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.UserRole;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.mapper.UserMapper;
import com.tamdaptit.rentalmanagement.identity.repository.RoleRepository;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.identity.repository.UserRoleRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.LocalDateTime;
import io.jsonwebtoken.Claims;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** Chi chap nhan hai vai tro duoc phep tu dang ky cong cong. */
    private static final List<RoleCode> PUBLIC_ROLE_CODES = List.of(RoleCode.TENANT, RoleCode.LANDLORD);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final AuthMapper authMapper;
    private final JwtTokenService jwtTokenService;

    @Value("${app.jwt.refresh-expiration-ms}")
    private long refreshExpiration;

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new AppException(
                    ErrorCode.EMAIL_ALREADY_EXISTS,
                    String.format("Email %s đã tồn tại", email));
        }

        String phone = request.phone().trim();
        if (userRepository.existsByPhone(phone)) {
            throw new AppException(
                    ErrorCode.PHONE_ALREADY_EXISTS,
                    String.format("Số điện thoại %s đã tồn tại", phone));
        }

        String citizenId = blankToNull(request.citizenId());
        if (citizenId != null && userRepository.existsByCitizenId(citizenId)) {
            throw new AppException(
                    ErrorCode.CITIZEN_ID_ALREADY_EXISTS,
                    String.format("Số CCCD/CMND %s đã tồn tại", citizenId));
        }

        // ADMIN chi duoc gan tu API noi bo, khong cho tu dang ky tu public.
        if (!PUBLIC_ROLE_CODES.contains(request.roleCode())) {
            throw new AppException(
                    ErrorCode.INVALID_REGISTER_ROLE,
                    "Chỉ có thể đăng ký tài khoản với vai trò người thuê hoặc chủ nhà");
        }

        Role role = roleRepository.findByCode(request.roleCode())
                .orElseThrow(() -> new AppException(
                        ErrorCode.ROLE_NOT_FOUND,
                        String.format("Không tìm thấy vai trò %s", request.roleCode())));

        User user = userMapper.toUser(new RegisterRequest(
                email,
                phone,
                request.password(),
                request.fullName().trim(),
                citizenId,
                request.roleCode()));

        user.setPassword(passwordEncoder.encode(request.password()));
        user.setEmailVerifiedAt(null);
        user.setPhoneVerifiedAt(null);

        User savedUser = userRepository.save(user);
        userRoleRepository.save(UserRole.builder().user(savedUser).role(role).build());

        // Nap lai user kem roles + permissions de build authorities
        User userWithRoles = userRepository
                .findByIdWithRolesAndPermissions(savedUser.getId())
                .orElse(savedUser);

        List<String> authorities = jwtTokenService.buildAuthorities(userWithRoles);
        String accessToken = jwtTokenService.generateAccessToken(userWithRoles);
        String rawRefreshToken = jwtTokenService.generateRefreshToken(userWithRoles);
        saveRefreshToken(savedUser, rawRefreshToken);

        return authMapper.toRegisterResponse(userWithRoles, authorities, accessToken, rawRefreshToken);
    }

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new AppException(
                        ErrorCode.INVALID_CREDENTIALS,
                        "Email hoặc mật khẩu không đúng"));

        ensureActive(user);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new AppException(
                    ErrorCode.INVALID_CREDENTIALS,
                    "Email hoặc mật khẩu không đúng");
        }

        List<String> authorities = jwtTokenService.buildAuthorities(user);
        String accessToken = jwtTokenService.generateAccessToken(user);
        String rawRefreshToken = jwtTokenService.generateRefreshToken(user);
        saveRefreshToken(user, rawRefreshToken);

        return authMapper.toLoginResponse(
                user,
                authorities,
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpiresAt());
    }

    /**
     * Xoay vong refresh token.
     *
     * <p>Dong {@code noRollbackFor} vi o buoc het han da set {@code revoked = true}.
     * Neu nem {@link AppException} binh thuong trong transaction, thay doi se bi
     * rollback va token cu khong bao giờ thuc su bi thu hoi.
     */
    @Override
    @Transactional(noRollbackFor = AppException.class)
    public RefreshTokenResponse refreshToken(RefreshTokenRequest request) {
        Claims claims = parseRefreshClaims(request.refreshToken());

        // Khoá dong token de tranh hai request dong thoi cung cap cap token moi.
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenForUpdate(HashUtils.sha256Hex(request.refreshToken()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (refreshToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshTokenRepository.save(refreshToken);
            throw new AppException(ErrorCode.TOKEN_EXPIRED, "Refresh token đã hết hạn");
        }

        User user = userRepository
                .findByIdForUpdate(refreshToken.getUser().getId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Number version = (Number) claims.get("authVersion");
        if (user.getAuthVersion() != (version == null ? 0 : version.intValue())) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        ensureActive(user);

        // Thu hoi token cu truoc khi cap token moi (rotation)
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);

        String accessToken = jwtTokenService.generateAccessToken(user);
        String rawRefreshToken = jwtTokenService.generateRefreshToken(user);
        saveRefreshToken(user, rawRefreshToken);

        return authMapper.toRefreshTokenResponse(
                accessToken,
                rawRefreshToken,
                jwtTokenService.getAccessTokenExpiresAt());
    }

    @Override
    @Transactional
    public void logout(LogoutRequest request) {
        RefreshToken refreshToken = refreshTokenRepository
                .findByTokenAndRevokedFalse(HashUtils.sha256Hex(request.refreshToken()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.getUser().getId().equals(currentUserId())) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        refreshToken.setRevoked(true);
        refreshTokenRepository.save(refreshToken);
    }

    private Claims parseRefreshClaims(String token) {
        try {
            Claims claims = jwtTokenService.validateToken(token);
            if (!jwtTokenService.isRefreshToken(token)) {
                throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
            }
            return claims;
        } catch (AppException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
    }

    private void ensureActive(User user) {
        if (user.getStatus() == UserStatus.BLOCKED) {
            throw new AppException(ErrorCode.USER_BLOCKED);
        }
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
    }

    private void saveRefreshToken(User user, String rawRefreshToken) {
        refreshTokenRepository.save(
                RefreshToken.builder()
                        .user(user)
                        // Chi luu SHA-256 hex; rawRefreshToken tra ve client mot lan duy nhat.
                        .token(HashUtils.sha256Hex(rawRefreshToken))
                        .expiresAt(LocalDateTime.now().plus(Duration.ofMillis(refreshExpiration)))
                        .revoked(false)
                        .build());
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AppException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return authentication.getName();
    }
}
