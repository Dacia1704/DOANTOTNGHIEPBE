package com.tamdaptit.rentalmanagement.chat;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.Optional;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.entity.ChatConversation;
import com.tamdaptit.rentalmanagement.chat.entity.ChatConversationParticipant;
import com.tamdaptit.rentalmanagement.chat.entity.enums.MessageType;
import com.tamdaptit.rentalmanagement.chat.repository.ChatConversationParticipantRepository;
import com.tamdaptit.rentalmanagement.chat.repository.ChatConversationRepository;
import com.tamdaptit.rentalmanagement.chat.repository.ChatMessageRepository;
import com.tamdaptit.rentalmanagement.chat.service.impl.ChatServiceImpl;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Phần 2: Trao đổi, thông báo & Quản trị bổ trợ - ChatService")
class ChatServiceTest {
    @Mock
    private ChatConversationRepository conversationRepository;

    @Mock
    private ChatConversationParticipantRepository participantRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ListingRepository listingRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    private ChatServiceImpl chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatServiceImpl(
            conversationRepository,
            participantRepository,
            messageRepository,
            userRepository,
            listingRepository,
            messagingTemplate
        );
    }

    @Test
    @DisplayName("Chủ trọ không được phép tự tạo cuộc hội thoại với chính tin đăng của mình")
    void testLandlordCannotChatWithSelf() {
        String landlordId = "landlord-123";
        User landlord = new User();
        landlord.setId(landlordId);
        landlord.setStatus(UserStatus.ACTIVE);

        Property property = new Property();
        property.setLandlord(landlord);

        Listing listing = new Listing();
        listing.setId("listing-1");
        listing.setProperty(property);
        listing.setStatus(ListingStatus.PUBLISHED);
        listing.setExpiresAt(LocalDateTime.now().plusDays(30));

        when(listingRepository.findByIdWithPropertyAndLandlord("listing-1")).thenReturn(Optional.of(listing));
        when(userRepository.findByIdForUpdate(landlordId)).thenReturn(Optional.of(landlord));

        assertThatThrownBy(() -> chatService.getOrCreateListingConversation("listing-1", landlordId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CANNOT_CHAT_WITH_SELF);
    }

    @Test
    @DisplayName("Tạo hội thoại với tin đăng đã hết hạn hoặc chưa duyệt ném LISTING_NOT_FOUND")
    void testChatWithExpiredListing() {
        Listing expiredListing = new Listing();
        expiredListing.setId("expired-1");
        expiredListing.setStatus(ListingStatus.PUBLISHED);
        expiredListing.setExpiresAt(LocalDateTime.now().minusDays(1)); // đã hết hạn

        when(listingRepository.findByIdWithPropertyAndLandlord("expired-1")).thenReturn(Optional.of(expiredListing));

        assertThatThrownBy(() -> chatService.getOrCreateListingConversation("expired-1", "user-1"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.LISTING_NOT_FOUND);
    }

    @Test
    @DisplayName("Người dùng không thuộc cuộc trò chuyện sẽ bị từ chối truy cập (NOT_CONVERSATION_PARTY)")
    void testUnauthorizedUserCannotAccessConversation() {
        String strangerId = "stranger-user";
        String conversationId = "conv-1";

        ChatConversation conversation = new ChatConversation();
        conversation.setId(conversationId);

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByConversationIdAndUserId(conversationId, strangerId))
            .thenReturn(false);

        assertThatThrownBy(() -> chatService.getConversation(conversationId, strangerId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_CONVERSATION_PARTY);
    }

    @Test
    @DisplayName("Lấy cuộc trò chuyện không tồn tại ném lỗi CHAT_CONVERSATION_NOT_FOUND")
    void testConversationNotFound() {
        String userId = "user-1";
        String conversationId = "conv-999";

        when(conversationRepository.findById(conversationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getConversation(conversationId, userId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
    }

    @Test
    @DisplayName("Đánh dấu đã đọc ném lỗi NOT_CONVERSATION_PARTY nếu không phải thành viên")
    void testMarkAsReadUnauthorized() {
        String strangerId = "stranger";
        String conversationId = "conv-1";

        when(participantRepository.existsByConversationIdAndUserId(conversationId, strangerId))
            .thenReturn(false);

        assertThatThrownBy(() -> chatService.markAsRead(conversationId, strangerId))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.NOT_CONVERSATION_PARTY);
    }

    @Test
    @DisplayName("Gửi tin nhắn qua WebSocket khi chưa đăng nhập (Principal null) ném UNAUTHENTICATED")
    void testSendMessageUnauthenticated() {
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId("conv-1");
        request.setContent("Xin chào");

        assertThatThrownBy(() -> chatService.sendMessage(request, null))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    @DisplayName("Gửi tin nhắn thiếu conversationId bị ném INVALID_CHAT_MESSAGE")
    void testSendMessageMissingConversationId() {
        Principal principal = () -> "user-1";
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId("   ");
        request.setContent("Nội dung tin nhắn");

        assertThatThrownBy(() -> chatService.sendMessage(request, principal))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_CHAT_MESSAGE);
    }

    @Test
    @DisplayName("Gửi tin nhắn có nội dung rỗng hoặc vượt quá 4000 ký tự bị ném INVALID_CHAT_MESSAGE")
    void testSendMessageInvalidContentLength() {
        Principal principal = () -> "user-1";

        // Tin nhắn rỗng
        ChatMessageRequest emptyRequest = new ChatMessageRequest();
        emptyRequest.setConversationId("conv-1");
        emptyRequest.setContent("   ");

        assertThatThrownBy(() -> chatService.sendMessage(emptyRequest, principal))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_CHAT_MESSAGE);

        // Tin nhắn quá dài (>4000 chars)
        ChatMessageRequest longRequest = new ChatMessageRequest();
        longRequest.setConversationId("conv-1");
        longRequest.setContent("x".repeat(4001));

        assertThatThrownBy(() -> chatService.sendMessage(longRequest, principal))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.INVALID_CHAT_MESSAGE);
    }

    @Test
    @DisplayName("Gửi tin nhắn vào cuộc trò chuyện không tồn tại ném CHAT_CONVERSATION_NOT_FOUND")
    void testSendMessageConversationNotFound() {
        Principal principal = () -> "user-1";
        ChatMessageRequest request = new ChatMessageRequest();
        request.setConversationId("conv-not-found");
        request.setContent("Alo chủ nhà ơi");
        request.setMessageType(MessageType.TEXT);

        when(conversationRepository.findById("conv-not-found")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(request, principal))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.CHAT_CONVERSATION_NOT_FOUND);
    }

    @Test
    @DisplayName("Tạo hỗ trợ Admin ném USER_INACTIVE khi người dùng hiện tại không ACTIVE")
    void testSupportConversationUserInactive() {
        User inactiveUser = new User();
        inactiveUser.setId("user-inactive");
        inactiveUser.setStatus(UserStatus.BLOCKED);

        when(userRepository.findByIdForUpdate("user-inactive")).thenReturn(Optional.of(inactiveUser));

        assertThatThrownBy(() -> chatService.getOrCreateSupportConversation("user-inactive"))
            .isInstanceOf(AppException.class)
            .extracting("errorCode")
            .isEqualTo(ErrorCode.USER_INACTIVE);
    }
}
