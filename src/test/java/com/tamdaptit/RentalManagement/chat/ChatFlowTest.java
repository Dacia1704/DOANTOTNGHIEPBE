package com.tamdaptit.RentalManagement.chat;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.entity.*;
import com.tamdaptit.rentalmanagement.chat.repository.*;
import com.tamdaptit.rentalmanagement.chat.service.impl.ChatServiceImpl;
import com.tamdaptit.rentalmanagement.common.exception.*;
import com.tamdaptit.rentalmanagement.identity.entity.*;
import com.tamdaptit.rentalmanagement.identity.entity.enums.RoleCode;
import com.tamdaptit.rentalmanagement.identity.repository.UserRepository;
import com.tamdaptit.rentalmanagement.listing.entity.Listing;
import com.tamdaptit.rentalmanagement.listing.entity.enums.ListingStatus;
import com.tamdaptit.rentalmanagement.listing.repository.ListingRepository;
import com.tamdaptit.rentalmanagement.property.entity.Property;
import org.junit.jupiter.api.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ChatFlowTest {
    private final ChatConversationRepository conversations = mock(ChatConversationRepository.class);
    private final ChatConversationParticipantRepository participants = mock(ChatConversationParticipantRepository.class);
    private final ChatMessageRepository messages = mock(ChatMessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final ListingRepository listings = mock(ListingRepository.class);
    private final ChatServiceImpl service = new ChatServiceImpl(conversations, participants, messages, users,
            listings, mock(SimpMessagingTemplate.class));
    private final Map<String, List<ChatConversationParticipant>> memberships = new HashMap<>();
    private static final String FIXED_ADMIN = "admin.demo@trotot.example.test";

    static User user(String id, RoleCode code) {
        User user = User.builder().id(id).email(id + "@unit.test").fullName(id).build();
        user.setUserRoles(Set.of(UserRole.builder().user(user).role(Role.builder().code(code).build()).build()));
        return user;
    }
    private ChatConversation conversation(String id, Listing listing, User... parties) {
        ChatConversation conversation = ChatConversation.builder().id(id).listing(listing).build();
        memberships.put(id, Arrays.stream(parties).map(user ->
                ChatConversationParticipant.builder().conversation(conversation).user(user).build()).toList());
        return conversation;
    }
    private Listing listing(User owner) {
        return Listing.builder().id("post").property(Property.builder().landlord(owner).build())
                .status(ListingStatus.PUBLISHED).expiresAt(LocalDateTime.now().plusDays(1)).build();
    }

    @BeforeEach void setUp() {
        when(conversations.save(any())).thenAnswer(invocation -> {
            ChatConversation item = invocation.getArgument(0); item.setId("created"); return item;
        });
        when(participants.save(any())).thenAnswer(invocation -> {
            ChatConversationParticipant item = invocation.getArgument(0);
            memberships.computeIfAbsent(item.getConversation().getId(), key -> new ArrayList<>()).add(item);
            return item;
        });
        when(participants.findByConversationId(anyString())).thenAnswer(invocation ->
                memberships.getOrDefault(invocation.getArgument(0), List.of()));
        when(participants.findOtherParticipant(anyString(), anyString())).thenAnswer(invocation ->
                memberships.getOrDefault(invocation.getArgument(0), List.of()).stream()
                        .filter(p -> !p.getUser().getId().equals(invocation.getArgument(1))).findFirst());
    }

    @Test void listingChatUsesPropertyOwnerAndReusesTheExistingConversation() {
        User tenant = user("tenant", RoleCode.TENANT), actualOwner = user("actual-owner", RoleCode.LANDLORD);
        Listing post = listing(actualOwner);
        when(listings.findByIdWithPropertyAndLandlord("post")).thenReturn(Optional.of(post));
        when(users.findByIdForUpdate("tenant")).thenReturn(Optional.of(tenant));
        ChatConversation existing = conversation("existing", post, tenant, actualOwner);
        when(conversations.findListingConversation("post", "tenant", "actual-owner")).thenReturn(Optional.of(existing));
        var result = service.getOrCreateListingConversation("post", "tenant");
        assertEquals("actual-owner", result.getOtherUser().getId());
        assertEquals("existing", result.getId());
        verify(conversations, never()).save(any());
        verify(users, never()).findAllAdmins();
    }

    @Test void hiddenPostCannotCreateAChatByGuessingItsId() {
        Listing hidden = listing(user("owner", RoleCode.LANDLORD));
        hidden.setStatus(ListingStatus.HIDDEN);
        when(listings.findByIdWithPropertyAndLandlord("post")).thenReturn(Optional.of(hidden));
        assertEquals(ErrorCode.LISTING_NOT_FOUND, assertThrows(AppException.class,
                () -> service.getOrCreateListingConversation("post", "tenant")).getErrorCode());
        verify(conversations, never()).save(any());
    }

    private void assertSupportFor(RoleCode role) {
        User client = user("client", role), admin = user("fixed-admin", RoleCode.ADMIN);
        admin.setEmail(FIXED_ADMIN);
        when(users.findByIdForUpdate("client")).thenReturn(Optional.of(client));
        when(users.findByEmailWithRolesAndPermissions(FIXED_ADMIN)).thenReturn(Optional.of(admin));
        var result = service.getOrCreateSupportConversation("client");
        assertEquals("fixed-admin", result.getOtherUser().getId());
        assertNull(result.getListing());
        verify(conversations).findDirectConversation("client", "fixed-admin");
        verify(users, never()).findAllAdmins();
    }
    @Test void tenantCanContactTheFixedSeedAdmin() { assertSupportFor(RoleCode.TENANT); }
    @Test void landlordCanContactTheSameFixedSeedAdmin() { assertSupportFor(RoleCode.LANDLORD); }

    @Test void missingFixedAdminDoesNotFallBackToAnotherAdmin() {
        when(users.findByIdForUpdate("client")).thenReturn(Optional.of(user("client", RoleCode.TENANT)));
        when(users.findByEmailWithRolesAndPermissions(FIXED_ADMIN)).thenReturn(Optional.empty());
        assertEquals(ErrorCode.SUPPORT_ADMIN_UNAVAILABLE, assertThrows(AppException.class,
                () -> service.getOrCreateSupportConversation("client")).getErrorCode());
        verify(users, never()).findAllAdmins();
        verify(conversations, never()).save(any());
    }

    @Test void adminCannotCreateASupportConversationWithThemself() {
        when(users.findByIdForUpdate("admin")).thenReturn(Optional.of(user("admin", RoleCode.ADMIN)));
        assertEquals(ErrorCode.CHAT_NOT_ALLOWED, assertThrows(AppException.class,
                () -> service.getOrCreateSupportConversation("admin")).getErrorCode());
        verify(conversations, never()).save(any());
    }

    @Test void inboxExcludesArbitraryDirectChatsAndTheWrongListingOwner() {
        User client = user("client", RoleCode.TENANT), other = user("other", RoleCode.TENANT);
        User owner = user("owner", RoleCode.LANDLORD), stranger = user("stranger", RoleCode.LANDLORD);
        User fixedAdmin = user("fixed-admin", RoleCode.ADMIN); fixedAdmin.setEmail(FIXED_ADMIN);
        User anotherAdmin = user("another-admin", RoleCode.ADMIN);
        var validPost = conversation("post-chat", listing(owner), client, owner);
        var validSupport = conversation("support-chat", null, client, fixedAdmin);
        var arbitrary = conversation("direct", null, client, other);
        var wrongAdmin = conversation("wrong-admin", null, client, anotherAdmin);
        var wrongOwner = conversation("wrong-owner", listing(owner), client, stranger);
        when(conversations.findConversationsByUserId("client")).thenReturn(List.of(validPost, arbitrary, validSupport, wrongAdmin, wrongOwner));
        assertEquals(List.of("post-chat", "support-chat"),
                service.getMyConversations("client").stream().map(c -> c.getId()).toList());
    }

    @Test void membershipAloneCannotAuthorizeSendingAnArbitraryDirectChat() {
        var direct = conversation("direct", null, user("client", RoleCode.TENANT), user("other", RoleCode.LANDLORD));
        when(conversations.findById("direct")).thenReturn(Optional.of(direct));
        when(participants.existsByConversationIdAndUserId("direct", "client")).thenReturn(true);
        ChatMessageRequest request = new ChatMessageRequest(); request.setConversationId("direct"); request.setContent("Not allowed");
        assertEquals(ErrorCode.CHAT_NOT_ALLOWED, assertThrows(AppException.class,
                () -> service.sendMessage(request, () -> "client")).getErrorCode());
        verifyNoInteractions(messages);
    }
}
