package com.tamdaptit.rentalmanagement.chat.dto.request;

import com.tamdaptit.rentalmanagement.chat.entity.enums.MessageType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChatMessageRequest {

    String conversationId;

    String content;

    MessageType messageType = MessageType.TEXT;
}
