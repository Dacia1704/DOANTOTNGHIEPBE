package com.tamdaptit.RentalManagement.chat;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatMessageResponse;
import com.tamdaptit.rentalmanagement.chat.entity.*;
import com.tamdaptit.rentalmanagement.chat.repository.*;
import com.tamdaptit.rentalmanagement.chat.service.impl.ChatServiceImpl;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.support.*;

import java.time.LocalDateTime;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatDeliveryTest {
    private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    private final ChatConversationParticipantRepository participants = mock(ChatConversationParticipantRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final SimpMessagingTemplate broker = mock(SimpMessagingTemplate.class);
    private final ChatServiceImpl service = new ChatServiceImpl(conversations, participants, messages,
            users, mock(ListingRepository.class), broker);
    private final LocalDateTime createdAt = LocalDateTime.of(2026, 10, 29, 16, 0);

    @BeforeEach void fixtures() {
        User sender = ChatFlowTest.user("sender", com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode.TENANT);
        User receiver = ChatFlowTest.user("receiver", com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode.LANDLORD);
        var listing = com.tamdaptit.rentalmanagement.listing.entity.Listing.builder().property(com.tamdaptit.rentalmanagement.property.entity.Property.builder().landlord(receiver).build()).build();
        var conversation = ChatConversation.builder().id("conversation").listing(listing).build();
        when(conversations.findById("conversation")).thenReturn(Optional.of(conversation));
        when(participants.findByConversationId("conversation")).thenReturn(java.util.List.of(
                ChatConversationParticipant.builder().conversation(conversation).user(sender).build(),
                ChatConversationParticipant.builder().conversation(conversation).user(receiver).build()));
        when(participants.existsByConversationIdAndUserId("conversation", "sender")).thenReturn(true);
        when(users.findById("sender")).thenReturn(Optional.of(User.builder().id("sender").build()));
        when(participants.findOtherParticipant("conversation", "sender")).thenReturn(Optional.of(
                ChatConversationParticipant.builder().user(User.builder().id("receiver").build()).build()));
        when(messages.saveAndFlush(any(ChatMessage.class))).thenAnswer(invocation -> {
            ChatMessage message = invocation.getArgument(0);
            assertNull(message.getCreatedAt(), "CreationTimestamp is assigned by the insert");
            message.setId("persisted-message");
            message.setCreatedAt(createdAt);
            return message;
        });
    }

    @AfterEach void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    private ChatMessageRequest request(String content) {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId("conversation");
        request.setContent(content);
        return request;
    }

    @Test void echoesPersistedTimestampToBothUsersOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        service.sendMessage(request("Xin chào"), () -> "sender");
        verify(messages).saveAndFlush(any(ChatMessage.class));
        verifyNoInteractions(broker);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        ArgumentCaptor<ChatMessageResponse> delivered = ArgumentCaptor.forClass(ChatMessageResponse.class);
        verify(broker).convertAndSendToUser(eq("sender"), eq("/queue/messages"), delivered.capture());
        verify(broker).convertAndSendToUser(eq("receiver"), eq("/queue/messages"), any(ChatMessageResponse.class));
        assertEquals("persisted-message", delivered.getValue().getId());
        assertEquals(createdAt, delivered.getValue().getCreatedAt());
    }

    @Test void neverAcknowledgesRolledBackTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        service.sendMessage(request("Nội dung"), () -> "sender");
        TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(broker);
    }

    @Test void rejectsAnonymousSenderWithoutWriting() {
        AppException error = assertThrows(AppException.class, () -> service.sendMessage(request("Hello"), null));
        assertEquals(ErrorCode.UNAUTHENTICATED, error.getErrorCode());
        verifyNoInteractions(messages, broker);
    }

    @Test void rejectsNonParticipantWithoutWritingOrBroadcasting() {
        when(participants.existsByConversationIdAndUserId("conversation", "sender")).thenReturn(false);
        AppException error = assertThrows(AppException.class, () -> service.sendMessage(request("Hello"), () -> "sender"));
        assertEquals(ErrorCode.NOT_CONVERSATION_PARTY, error.getErrorCode());
        verifyNoInteractions(messages, broker);
    }

    @Test void rejectsOversizedMessageBeforePersistence() {
        AppException error = assertThrows(AppException.class,
                () -> service.sendMessage(request("x".repeat(4001)), () -> "sender"));
        assertEquals(ErrorCode.INVALID_CHAT_MESSAGE, error.getErrorCode());
        verifyNoInteractions(messages, broker);
    }
}
