package com.tamdaptit.rentalmanagement.chat.controller;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.service.ChatService;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatErrorResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
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
@Slf4j
public class ChatWebSocketController {

    private final ChatService chatService;
    private final SimpMessagingTemplate messagingTemplate;

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
        try {
            chatService.sendMessage(request, principal);
        } catch (AppException exception) {
            if (principal == null) throw exception;
            messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors",
                    new ChatErrorResponse(request.getConversationId(), exception.getErrorCode().name(), exception.getMessage()));
        }
    }

    @MessageExceptionHandler
    @SendToUser(value = "/queue/errors", broadcast = false)
    public ChatErrorResponse handleUnexpectedError(Exception exception) {
        log.error("Unable to process chat message", exception);
        return new ChatErrorResponse(null, "CHAT_SEND_FAILED",
                "Chưa nhận được xác nhận gửi tin. Vui lòng kiểm tra cuộc trò chuyện trước khi gửi lại.");
    }
}
