package com.tamdaptit.rentalmanagement.security;

import com.tamdaptit.rentalmanagement.service.JwtTokenService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenService jwtTokenService;
    private final com.tamdaptit.rentalmanagement.repository.UserRepository userRepository;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtTokenService.validateToken(token);

            String userId = claims.getSubject();
            if (!"access".equals(claims.get("type", String.class))) {
                throw new IllegalArgumentException("Access token required");
            }
            var user = userRepository.findByIdWithRolesAndPermissions(userId).orElseThrow();
            Number version = (Number) claims.get("authVersion");
            if (user.getStatus() != com.tamdaptit.rentalmanagement.entity.enums.UserStatus.ACTIVE
                    || user.getAuthVersion() != (version == null ? 0 : version.intValue())) {
                throw new IllegalArgumentException("Token revoked or account inactive");
            }

            List<SimpleGrantedAuthority> authorities =
                    jwtTokenService
                            .buildAuthorities(user)
                            .stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList();

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userId,
                            null,
                            authorities
                    );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authentication);

        } catch (Exception e) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}
