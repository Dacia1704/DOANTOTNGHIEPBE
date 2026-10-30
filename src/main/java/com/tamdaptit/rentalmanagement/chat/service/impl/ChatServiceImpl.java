package com.tamdaptit.rentalmanagement.chat.service.impl;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatConversationResponse;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatListingResponse;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatMessageResponse;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatUserResponse;
import com.tamdaptit.rentalmanagement.chat.entity.ChatConversation;
import com.tamdaptit.rentalmanagement.chat.entity.ChatConversationParticipant;
import com.tamdaptit.rentalmanagement.chat.entity.ChatMessage;
import com.tamdaptit.rentalmanagement.chat.entity.enums.MessageType;
import com.tamdaptit.rentalmanagement.chat.repository.ChatConversationParticipantRepository;
import com.tamdaptit.rentalmanagement.chat.repository.ChatConversationRepository;
import com.tamdaptit.rentalmanagement.chat.repository.ChatMessageRepository;
import com.tamdaptit.rentalmanagement.chat.service.ChatService;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import com.tamdaptit.rentalmanagement.common.exception.AppException;
import com.tamdaptit.rentalmanagement.common.exception.ErrorCode;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    // Stable identity seeded by V2__seed_reference_and_demo_data.sql; never choose an arbitrary admin.
    static final String SUPPORT_ADMIN_EMAIL = "admin.demo@trotot.example.test";

    private final ChatConversationRepository conversationRepository;
    private final ChatConversationParticipantRepository participantRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ListingRepository listingRepository;
    private final SimpMessagingTemplate messagingTemplate;

    // =========================================================================
    // GET OR CREATE LISTING CONVERSATION
    // =========================================================================

    @Override
    @Transactional
    public ChatConversationResponse getOrCreateListingConversation(String listingId, String currentUserId) {
        // The recipient comes exclusively from the listing's Property, never from the client.
        Listing listing = listingRepository.findByIdWithPropertyAndLandlord(listingId)
                .orElseThrow(() -> new AppException(ErrorCode.LISTING_NOT_FOUND));
        if (listing.getStatus() != ListingStatus.PUBLISHED || listing.getExpiresAt() == null
                || !listing.getExpiresAt().isAfter(LocalDateTime.now())) {
            throw new AppException(ErrorCode.LISTING_NOT_FOUND);
        }

        User landlord = listing.getProperty().getLandlord();

        // 3. Load current user
        User currentUser = userRepository.findByIdForUpdate(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (currentUser.getStatus() != UserStatus.ACTIVE || landlord.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // 4. Không được chat với chính mình
        if (currentUserId.equals(landlord.getId())) {
            throw new AppException(ErrorCode.CANNOT_CHAT_WITH_SELF);
        }

        // 5. Validate role rules
        validateChatPermission(currentUser, landlord);

        // 6. Tìm conversation đã tồn tại
        return conversationRepository
                .findListingConversation(listingId, currentUserId, landlord.getId())
                .map(c -> buildConversationResponse(c, currentUserId))
                .orElseGet(() -> {
                    ChatConversation conversation = ChatConversation.builder()
                            .listing(listing)
                            .build();
                    conversation = conversationRepository.save(conversation);

                    createParticipant(conversation, currentUser);
                    createParticipant(conversation, landlord);

                    return buildConversationResponse(conversation, currentUserId);
                });
    }

    // =========================================================================
    // GET OR CREATE SUPPORT CONVERSATION (USER <-> ADMIN)
    // =========================================================================

    @Override
    @Transactional
    public ChatConversationResponse getOrCreateSupportConversation(String currentUserId) {
        User currentUser = userRepository.findByIdForUpdate(currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (currentUser.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }
        if (!isSupportClient(currentUser)) throw new AppException(ErrorCode.CHAT_NOT_ALLOWED);

        User admin = userRepository.findByEmailWithRolesAndPermissions(SUPPORT_ADMIN_EMAIL)
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && isSupportAdmin(user))
                .orElseThrow(() -> new AppException(ErrorCode.SUPPORT_ADMIN_UNAVAILABLE));

        // Tìm direct conversation đã tồn tại giữa current user và admin
        return conversationRepository
                .findDirectConversation(currentUserId, admin.getId())
                .map(c -> buildConversationResponse(c, currentUserId))
                .orElseGet(() -> {
                    ChatConversation conversation = ChatConversation.builder().build();
                    conversation = conversationRepository.save(conversation);

                    createParticipant(conversation, currentUser);
                    createParticipant(conversation, admin);

                    return buildConversationResponse(conversation, currentUserId);
                });
    }

    // =========================================================================
    // GET MY CONVERSATIONS
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public List<ChatConversationResponse> getMyConversations(String currentUserId) {
        return conversationRepository.findConversationsByUserId(currentUserId)
                .stream()
                .filter(c -> isAllowedConversation(c, currentUserId))
                .map(c -> buildConversationResponse(c, currentUserId))
                .collect(Collectors.toList());
    }

    // =========================================================================
    // GET CONVERSATION DETAIL
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public ChatConversationResponse getConversation(String conversationId, String currentUserId) {
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));

        ensureParticipant(conversationId, currentUserId);

        return buildConversationResponse(conversation, currentUserId);
    }

    // =========================================================================
    // GET MESSAGES (PAGINATED)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ChatMessageResponse> getMessages(String conversationId, String currentUserId, int page, int size) {
        conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));

        ensureParticipant(conversationId, currentUserId);

        Pageable pageable = PageRequest.of(page, size);
        Page<ChatMessage> messagePage = messageRepository
                .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        List<ChatMessageResponse> data = messagePage.getContent().stream()
                .map(this::toMessageResponse)
                .collect(Collectors.toList());

        return PageResponse.<ChatMessageResponse>builder()
                .currentPage(page)
                .pageSize(size)
                .totalPages(messagePage.getTotalPages())
                .totalElements(messagePage.getTotalElements())
                .data(data)
                .build();
    }

    // =========================================================================
    // MARK AS READ
    // =========================================================================

    @Override
    @Transactional
    public void markAsRead(String conversationId, String currentUserId) {
        ensureParticipant(conversationId, currentUserId);
        ChatConversationParticipant participant = participantRepository
                .findByConversationIdAndUserId(conversationId, currentUserId)
                .orElseThrow(() -> new AppException(ErrorCode.NOT_CONVERSATION_PARTY));

        participant.setLastReadAt(LocalDateTime.now());
        participantRepository.save(participant);
    }

    // =========================================================================
    // SEND MESSAGE (WEBSOCKET)
    // =========================================================================

    @Override
    @Transactional
    public void sendMessage(ChatMessageRequest request, Principal principal) {
        if (principal == null) {
            throw new AppException(ErrorCode.UNAUTHENTICATED);
        }
        String senderId = principal.getName(); // Principal name = userId

        // 1. Validate input
        if (request.getConversationId() == null || request.getConversationId().isBlank()) {
            throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
        if (request.getContent() == null || request.getContent().isBlank() || request.getContent().length() > 4000) {
            throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
        }
        if (request.getMessageType() == null) {
            request.setMessageType(MessageType.TEXT);
        }
        if (request.getMessageType() == MessageType.TEXT && request.getContent().isBlank()) {
            throw new AppException(ErrorCode.INVALID_CHAT_MESSAGE);
        }

        // 2. Load conversation
        ChatConversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));

        // 3. Kiểm tra sender là participant
        ensureParticipant(request.getConversationId(), senderId);

        // 4. Load sender
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (sender.getStatus() != UserStatus.ACTIVE) {
            throw new AppException(ErrorCode.USER_INACTIVE);
        }

        // 5. Save message
        LocalDateTime now = LocalDateTime.now();
        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(request.getContent())
                .messageType(request.getMessageType())
                .build();
        // Hibernate generates @CreationTimestamp at INSERT/flush, not at save().
        // Build the realtime DTO only after the timestamp and id exist.
        message = messageRepository.saveAndFlush(message);

        // 6. Update lastMessageAt
        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        // 7. Build response
        ChatMessageResponse response = toMessageResponse(message);

        String receiverId = participantRepository.findOtherParticipant(request.getConversationId(), senderId)
                .map(participant -> participant.getUser().getId()).orElse(null);
        Runnable deliver = () -> {
            messagingTemplate.convertAndSend("/topic/conversations/" + response.getConversationId(), response);
            if (receiverId != null) {
                messagingTemplate.convertAndSendToUser(receiverId, "/queue/messages", response);
            }
            messagingTemplate.convertAndSendToUser(senderId, "/queue/messages", response);
        };
        // An echo is a persistence acknowledgement: never publish a transaction that rolls back.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { deliver.run(); }
            });
        } else {
            deliver.run();
        }
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    /**
     * Validate business rule: kiểm tra 2 user có được phép chat với nhau không.
     * Dựa trên role set thực tế của mỗi user.
     */
    private void validateChatPermission(User userA, User userB) {
        Set<RoleCode> rolesA = extractRoles(userA);
        Set<RoleCode> rolesB = extractRoles(userB);

        // Tập hợp roles kết hợp
        boolean hasAdmin = rolesA.contains(RoleCode.ADMIN) || rolesB.contains(RoleCode.ADMIN);
        boolean aIsOnlyTenant = rolesA.contains(RoleCode.TENANT) && !rolesA.contains(RoleCode.LANDLORD) && !rolesA.contains(RoleCode.ADMIN);
        boolean bIsOnlyTenant = rolesB.contains(RoleCode.TENANT) && !rolesB.contains(RoleCode.LANDLORD) && !rolesB.contains(RoleCode.ADMIN);
        boolean aIsOnlyLandlord = rolesA.contains(RoleCode.LANDLORD) && !rolesA.contains(RoleCode.TENANT) && !rolesA.contains(RoleCode.ADMIN);
        boolean bIsOnlyLandlord = rolesB.contains(RoleCode.LANDLORD) && !rolesB.contains(RoleCode.TENANT) && !rolesB.contains(RoleCode.ADMIN);

        // TENANT <-> TENANT: không cho phép
        if (aIsOnlyTenant && bIsOnlyTenant) {
            throw new AppException(ErrorCode.CHAT_NOT_ALLOWED);
        }

        // LANDLORD <-> LANDLORD: không cho phép
        if (aIsOnlyLandlord && bIsOnlyLandlord) {
            throw new AppException(ErrorCode.CHAT_NOT_ALLOWED);
        }

        // Nếu có ADMIN thì luôn cho phép (TENANT <-> ADMIN, LANDLORD <-> ADMIN)
        if (hasAdmin) {
            return;
        }

        // TENANT <-> LANDLORD: cho phép (một bên có TENANT, bên kia có LANDLORD)
        boolean tenantToLandlord = (rolesA.contains(RoleCode.TENANT) && rolesB.contains(RoleCode.LANDLORD))
                || (rolesA.contains(RoleCode.LANDLORD) && rolesB.contains(RoleCode.TENANT));
        if (!tenantToLandlord) {
            throw new AppException(ErrorCode.CHAT_NOT_ALLOWED);
        }
    }

    private Set<RoleCode> extractRoles(User user) {
        if (user.getUserRoles() == null) return EnumSet.noneOf(RoleCode.class);
        return user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null && ur.getRole().getCode() != null)
                .map(ur -> ur.getRole().getCode())
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(RoleCode.class)));
    }

    private void ensureParticipant(String conversationId, String userId) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new AppException(ErrorCode.NOT_CONVERSATION_PARTY);
        }
        ChatConversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CHAT_CONVERSATION_NOT_FOUND));
        if (!isAllowedConversation(conversation, userId)) throw new AppException(ErrorCode.CHAT_NOT_ALLOWED);
    }

    private boolean isSupportAdmin(User user) {
        return SUPPORT_ADMIN_EMAIL.equalsIgnoreCase(user.getEmail()) && extractRoles(user).contains(RoleCode.ADMIN);
    }

    private boolean isSupportClient(User user) {
        Set<RoleCode> roles = extractRoles(user);
        return !roles.contains(RoleCode.ADMIN) && (roles.contains(RoleCode.TENANT) || roles.contains(RoleCode.LANDLORD));
    }

    private boolean isAllowedConversation(ChatConversation conversation, String currentUserId) {
        List<User> parties = participantRepository.findByConversationId(conversation.getId()).stream()
                .map(ChatConversationParticipant::getUser).toList();
        if (parties.size() != 2 || parties.stream().noneMatch(user -> currentUserId.equals(user.getId()))) return false;
        if (conversation.getListing() != null) {
            var property = conversation.getListing().getProperty();
            if (property == null || property.getLandlord() == null
                    || parties.stream().noneMatch(user -> property.getLandlord().getId().equals(user.getId()))) return false;
            try {
                validateChatPermission(parties.get(0), parties.get(1));
                return true;
            } catch (AppException exception) {
                return false;
            }
        }
        if (conversation.getRentalRequest() != null) return false;
        return (isSupportAdmin(parties.get(0)) && isSupportClient(parties.get(1)))
                || (isSupportAdmin(parties.get(1)) && isSupportClient(parties.get(0)));
    }

    private ChatConversationParticipant createParticipant(ChatConversation conversation, User user) {
        ChatConversationParticipant participant = ChatConversationParticipant.builder()
                .conversation(conversation)
                .user(user)
                .build();
        return participantRepository.save(participant);
    }

    /**
     * Build ChatConversationResponse từ entity, dùng cho current user.
     */
    private ChatConversationResponse buildConversationResponse(ChatConversation conversation, String currentUserId) {
        // Other user
        ChatUserResponse otherUser = participantRepository
                .findOtherParticipant(conversation.getId(), currentUserId)
                .map(p -> toUserResponse(p.getUser()))
                .orElse(null);

        // Listing
        ChatListingResponse listingResponse = null;
        if (conversation.getListing() != null) {
            Listing l = conversation.getListing();
            listingResponse = ChatListingResponse.builder()
                    .id(l.getId())
                    .title(l.getTitle())
                    .build();
        }

        // Last message
        ChatMessageResponse lastMessage = messageRepository
                .findLatestMessage(conversation.getId())
                .map(this::toMessageResponse)
                .orElse(null);

        // Unread count (cho current user)
        long unreadCount = computeUnreadCount(conversation.getId(), currentUserId);

        return ChatConversationResponse.builder()
                .id(conversation.getId())
                .otherUser(otherUser)
                .listing(listingResponse)
                .lastMessage(lastMessage)
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(unreadCount)
                .build();
    }

    private long computeUnreadCount(String conversationId, String userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .map(p -> {
                    LocalDateTime cutoff = p.getLastReadAt() != null ? p.getLastReadAt() : p.getJoinedAt();
                    return messageRepository.countUnread(conversationId, userId, cutoff);
                })
                .orElse(0L);
    }

    private ChatMessageResponse toMessageResponse(ChatMessage message) {
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .sender(toUserResponse(message.getSender()))
                .content(message.getContent())
                .messageType(message.getMessageType())
                .createdAt(message.getCreatedAt())
                .build();
    }

    private ChatUserResponse toUserResponse(User user) {
        List<String> roles = user.getUserRoles() == null ? List.of() :
                user.getUserRoles().stream()
                        .filter(ur -> ur.getRole() != null && ur.getRole().getCode() != null)
                        .map(ur -> ur.getRole().getCode().name())
                        .collect(Collectors.toList());

        return ChatUserResponse.builder()
                .id(user.getId())
                .fullName(user.getFullName())
                .avatarUrl(user.getAvatarUrl())
                .roles(roles)
                .build();
    }
}
