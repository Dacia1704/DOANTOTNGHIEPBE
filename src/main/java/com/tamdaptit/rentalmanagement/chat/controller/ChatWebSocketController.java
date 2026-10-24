package com.tamdaptit.rentalmanagement.chat.controller;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * WebSocket controller xử lý tin nhắn chat realtime qua STOMP.
 *
 * <p>Frontend gửi tới: /app/chat.send
 * <p>Principal được lấy từ WebSocketAuthInterceptor (JWT -> userId).
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;

    /**
     * Nhận tin nhắn từ client, lưu DB và push realtime tới receiver.
     *
     * <p>Frontend STOMP send:
     * <pre>
     * SEND
     * destination:/app/chat.send
     *
     * {"conversationId":"...","content":"Phòng này còn không?","messageType":"TEXT"}
     * </pre>
     *
     * <p>Không nhận senderId hay receiverId từ client.
     * Sender được xác định từ Principal (JWT).
     */
    @MessageMapping("/chat.send")
    public void sendMessage(ChatMessageRequest request, Principal principal) {
        chatService.sendMessage(request, principal);
    }
}
