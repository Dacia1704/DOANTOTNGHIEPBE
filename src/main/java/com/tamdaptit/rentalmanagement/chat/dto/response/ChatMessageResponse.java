package com.tamdaptit.rentalmanagement.chat.dto.response;

import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.chat.entity.enums.MessageType;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageResponse {
    String id;

    String conversationId;

    ChatUserResponse sender;

    String content;

    MessageType messageType;

    LocalDateTime createdAt;
}
