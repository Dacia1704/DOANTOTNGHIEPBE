package com.tamdaptit.rentalmanagement.chat.config;

import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Xác thực JWT khi client gửi STOMP CONNECT.
 *
 * <p>Reuse JwtTokenService và UserRepository từ auth module, không tạo mới.
 * Principal.getName() sẽ trả về userId (UUID) – khớp với REST Authentication.getName().
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final JwtTokenService jwtTokenService;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message, StompHeaderAccessor.class
        );

        if (accessor == null) {
            return message;
        }

        // Chỉ xử lý khi STOMP CONNECT
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("WebSocket CONNECT thiếu Authorization header");
                return message;
            }

            String token = authHeader.substring(7);
            try {
                Claims claims = jwtTokenService.validateToken(token);

                // Đảm bảo là access token
                if (!"access".equals(claims.get("type", String.class))) {
                    log.warn("WebSocket CONNECT: không phải access token");
                    return message;
                }

                String userId = claims.getSubject();

                // Kiểm tra user ACTIVE và authVersion khớp
                var user = userRepository.findByIdWithRolesAndPermissions(userId).orElse(null);
                if (user == null) {
                    log.warn("WebSocket CONNECT: user không tồn tại - {}", userId);
                    return message;
                }

                Number version = (Number) claims.get("authVersion");
                int tokenVersion = version == null ? 0 : version.intValue();
                if (user.getStatus() != UserStatus.ACTIVE || user.getAuthVersion() != tokenVersion) {
                    log.warn("WebSocket CONNECT: token bị thu hồi hoặc tài khoản không hoạt động - {}", userId);
                    return message;
                }

                // Build authorities
                List<SimpleGrantedAuthority> authorities =
                        jwtTokenService.buildAuthorities(user).stream()
                                .map(SimpleGrantedAuthority::new)
                                .toList();

                // Tạo Authentication, principal.getName() = userId
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userId, null, authorities);

                // Gán vào STOMP accessor -> WebSocket session sẽ có Principal
                accessor.setUser(authentication);

                log.debug("WebSocket CONNECT: authenticated userId={}", userId);

            } catch (Exception e) {
                log.warn("WebSocket CONNECT: JWT validation failed - {}", e.getMessage());
            }
        }

        return message;
    }
}
