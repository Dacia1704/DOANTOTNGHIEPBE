package com.tamdaptit.RentalManagement.chat;

import com.tamdaptit.rentalmanagement.chat.controller.ChatWebSocketController;
import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatErrorResponse;
import com.tamdaptit.rentalmanagement.chat.service.ChatService;
import com.tamdaptit.rentalmanagement.common.exception.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.security.Principal;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatWebSocketControllerTest {
    @Test void returnsBusinessErrorToSenderWithConversationId() {
        ChatService service = mock(ChatService.class);
        SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
        ChatWebSocketController controller = new ChatWebSocketController(service, messaging);
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId("conversation");
        Principal principal = () -> "sender";
        doThrow(new AppException(ErrorCode.NOT_CONVERSATION_PARTY)).when(service).sendMessage(request, principal);
        controller.sendMessage(request, principal);
        ArgumentCaptor<ChatErrorResponse> error = ArgumentCaptor.forClass(ChatErrorResponse.class);
        verify(messaging).convertAndSendToUser(eq("sender"), eq("/queue/errors"), error.capture());
        assertEquals("conversation", error.getValue().conversationId());
        assertEquals("NOT_CONVERSATION_PARTY", error.getValue().code());
        assertFalse(error.getValue().message().isBlank());
    }
}
