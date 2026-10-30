package com.tamdaptit.rentalmanagement.chat.dto.response;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatConversationResponse {

    String id;

    /** User kia trong conversation (không phải current user). */
    ChatUserResponse otherUser;

    /** Listing context nếu conversation từ listing; null nếu direct chat. */
    ChatListingResponse listing;

    /** Tin nhắn cuối cùng trong conversation. */
    ChatMessageResponse lastMessage;

    LocalDateTime lastMessageAt;

    long unreadCount;
}
