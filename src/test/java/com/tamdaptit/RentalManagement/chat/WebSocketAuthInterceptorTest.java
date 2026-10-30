package com.tamdaptit.RentalManagement.chat;

import com.tamdaptit.rentalmanagement.auth.service.JwtTokenService;
import com.tamdaptit.rentalmanagement.chat.config.WebSocketAuthInterceptor;
import com.tamdaptit.rentalmanagement.chat.repository.ChatConversationParticipantRepository;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.authentication.*;
import org.springframework.security.access.AccessDeniedException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthInterceptorTest {
    private final JwtTokenService jwt = mock(JwtTokenService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ChatConversationParticipantRepository participants = mock(ChatConversationParticipantRepository.class);
    private final WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(jwt, users, participants);
    private final MessageChannel channel = mock(MessageChannel.class);

    private Message<byte[]> message(StompCommand command, String destination, boolean authenticated) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setLeaveMutable(true);
        if (destination != null) accessor.setDestination(destination);
        if (authenticated) accessor.setUser(new UsernamePasswordAuthenticationToken("sender", null, List.of()));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private Message<byte[]> connect() {
        Message<byte[]> message = message(StompCommand.CONNECT, null, false);
        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class).setNativeHeader("Authorization", "Bearer access");
        return message;
    }

    private void validToken(int userVersion) {
        Claims claims = mock(Claims.class);
        when(claims.get("type", String.class)).thenReturn("access");
        when(claims.getSubject()).thenReturn("sender");
        when(claims.get("authVersion")).thenReturn(0);
        when(jwt.validateToken("access")).thenReturn(claims);
        User user = User.builder().id("sender").authVersion(userVersion).build();
        when(users.findByIdWithRolesAndPermissions("sender")).thenReturn(Optional.of(user));
        when(jwt.buildAuthorities(user)).thenReturn(List.of("ROLE_TENANT"));
    }

    @Test void missingTokenFailsInsteadOfProducingConnectedSession() {
        assertThrows(BadCredentialsException.class, () ->
                interceptor.preSend(message(StompCommand.CONNECT, null, false), channel));
    }

    @Test void invalidTokenFailsClosed() {
        when(jwt.validateToken("access")).thenThrow(new IllegalArgumentException("Invalid token"));
        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(connect(), channel));
    }

    @Test void revokedTokenCannotConnect() {
        validToken(1);
        assertThrows(BadCredentialsException.class, () -> interceptor.preSend(connect(), channel));
    }

    @Test void validTokenSetsUserIdPrincipal() {
        validToken(0);
        Message<?> authenticated = interceptor.preSend(connect(), channel);
        assertEquals("sender", MessageHeaderAccessor.getAccessor(authenticated, StompHeaderAccessor.class).getUser().getName());
    }

    @Test void anonymousSendIsRejected() {
        assertThrows(BadCredentialsException.class, () ->
                interceptor.preSend(message(StompCommand.SEND, "/app/chat.send", false), channel));
    }

    @Test void privateQueuesAreAllowedButOtherUsersTopicsAreRejected() {
        assertNotNull(interceptor.preSend(message(StompCommand.SUBSCRIBE, "/user/queue/messages", true), channel));
        assertNotNull(interceptor.preSend(message(StompCommand.SUBSCRIBE, "/user/queue/errors", true), channel));
        assertThrows(AccessDeniedException.class, () ->
                interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/conversations/other", true), channel));
        when(participants.existsByConversationIdAndUserId("mine", "sender")).thenReturn(true);
        assertNotNull(interceptor.preSend(message(StompCommand.SUBSCRIBE, "/topic/conversations/mine", true), channel));
    }
}
