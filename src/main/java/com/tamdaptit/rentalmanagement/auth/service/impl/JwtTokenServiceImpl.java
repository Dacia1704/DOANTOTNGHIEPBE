package com.tamdaptit.rentalmanagement.auth.service.impl;

import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.Permission;
import com.tamdaptit.rentalmanagement.identity.entity.Role;
import com.tamdaptit.rentalmanagement.identity.entity.RolePermission;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Phat va xac thuc JWT bang HMAC-SHA512.
 *
 * <p>Khoa ky phai dai it nhat 64 byte. Neu {@code app.jwt.secret} ngan hon,
 * ung dung fail ngay khi khoi dong thay vi tu lap lai chuoi secret de du so byte.
 */
@Service
public class JwtTokenServiceImpl implements JwtTokenService {

    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_JTI = "jti";
    private static final String CLAIM_AUTH_VERSION = "authVersion";

    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";

    /** HMAC-SHA512 bat buoc khoa toi thieu 64 byte. */
    private static final int MIN_KEY_BYTES = 64;

    private final String secret;
    private final String issuer;
    private final long accessExpiration;
    private final long refreshExpiration;

    public JwtTokenServiceImpl(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.issuer:rental-management}") String issuer,
            @Value("${app.jwt.access-expiration-ms}") long accessExpiration,
            @Value("${app.jwt.refresh-expiration-ms}") long refreshExpiration) {
        this.secret = secret;
        this.issuer = issuer;
        this.accessExpiration = accessExpiration;
        this.refreshExpiration = refreshExpiration;
        if (secret == null || secret.isBlank()) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "Thiếu cấu hình app.jwt.secret");
        }
    }

    @Override
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + accessExpiration);
        return Jwts.builder()
                .subject(user.getId())
                .claim(CLAIM_AUTH_VERSION, user.getAuthVersion())
                .claim(CLAIM_SCOPE, String.join(" ", buildAuthorities(user)))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_JTI, UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public String generateRefreshToken(User user) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + refreshExpiration);
        return Jwts.builder()
                .subject(user.getId())
                .claim(CLAIM_AUTH_VERSION, user.getAuthVersion())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim(CLAIM_JTI, UUID.randomUUID().toString())
                .issuer(issuer)
                .issuedAt(now)
                .expiration(expiration)
                .signWith(getSigningKey(), Jwts.SIG.HS512)
                .compact();
    }

    @Override
    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public List<String> getAuthorities(Claims claims) {
        String scope = claims.get(CLAIM_SCOPE, String.class);
        if (scope == null || scope.isBlank()) {
            return List.of();
        }
        return List.of(scope.split(" "));
    }

    /**
     * Dung danh sach quyen tu User -> UserRole -> Role -> RolePermission -> Permission.
     * V moi vai tro them {@code ROLE_} + role code, v moi quyen them permission code.
     */
    @Override
    public List<String> buildAuthorities(User user) {
        if (user == null || user.getUserRoles() == null || user.getUserRoles().isEmpty()) {
            return List.of();
        }

        Set<String> authorities = new LinkedHashSet<>();
        for (UserRole userRole : user.getUserRoles()) {
            Role role = userRole.getRole();
            if (role == null || role.getCode() == null) {
                continue;
            }
            authorities.add("ROLE_" + role.getCode().name());

            Set<RolePermission> rolePermissions = role.getRolePermissions();
            if (rolePermissions == null) {
                continue;
            }
            for (RolePermission rolePermission : rolePermissions) {
                Permission permission = rolePermission.getPermission();
                if (permission != null && permission.getCode() != null) {
                    authorities.add(permission.getCode());
                }
            }
        }
        return new ArrayList<>(authorities);
    }

    @Override
    public String getUserId(String token) {
        return validateToken(token).getSubject();
    }

    @Override
    public LocalDateTime getAccessTokenExpiresAt() {
        Instant expiresAt = Instant.now().plusMillis(accessExpiration);
        return LocalDateTime.ofInstant(expiresAt, ZoneId.systemDefault());
    }

    @Override
    public boolean isAccessToken(String token) {
        return hasType(token, TYPE_ACCESS);
    }

    @Override
    public boolean isRefreshToken(String token) {
        return hasType(token, TYPE_REFRESH);
    }

    private boolean hasType(String token, String expected) {
        try {
            return expected.equals(validateToken(token).get(CLAIM_TYPE, String.class));
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /**
     * @throws AppException neu secret ngan hoac rong
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < MIN_KEY_BYTES) {
            throw new AppException(ErrorCode.INVALID_REQUEST,
                    "app.jwt.secret phai dai it nhat " + MIN_KEY_BYTES + " byte cho HS512, hiện tại là "
                            + keyBytes.length + " byte");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
