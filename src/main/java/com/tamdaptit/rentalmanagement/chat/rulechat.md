# TASK: IMPLEMENT REALTIME CHAT BACKEND - SPRING WEBSOCKET + STOMP

## 1. Mục tiêu

Implement hoàn chỉnh chức năng **chat realtime** cho hệ thống cho thuê phòng trọ.

Technology hiện tại:

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring Data JPA
- MySQL 8
- Flyway
- Spring WebSocket
- STOMP
- Lombok

Base package:

```text
com.tamdaptit.rentalmanagement
```

Yêu cầu implement:

```text
TENANT   <-> LANDLORD
TENANT   <-> ADMIN
LANDLORD <-> ADMIN
```

Đặc biệt:

- Tenant đang xem chi tiết một `Listing` có thể nhấn nút chat để nhắn trực tiếp với Landlord sở hữu phòng của Listing đó.
- Ở giao diện chat chính, user có thể tìm người phù hợp để bắt đầu chat.
- Tin nhắn phải lưu MySQL.
- WebSocket chỉ dùng để truyền realtime.
- User offline vẫn phải nhận được message history/unread khi đăng nhập lại.

---

# 2. QUAN TRỌNG: ĐỌC PROJECT TRƯỚC KHI CODE

Trước khi thay đổi bất kỳ file nào, phải inspect code hiện tại.

Đặc biệt đọc:

```text
com.tamdaptit.rentalmanagement.auth
com.tamdaptit.rentalmanagement.common
com.tamdaptit.rentalmanagement.identity
com.tamdaptit.rentalmanagement.chat.entity
com.tamdaptit.rentalmanagement.listing.entity
com.tamdaptit.rentalmanagement.rental.entity
com.tamdaptit.rentalmanagement.room
```

Phải xác định:

1. JWT hiện tại được generate/validate như thế nào.
2. `SecurityConfig` hiện tại.
3. `Principal#getName()` hoặc Authentication principal hiện tại đại diện cho:
   - userId,
   - email,
   - username,
   - hay giá trị khác.
4. Cách lấy current authenticated User hiện tại.
5. `User` map với `Role` như thế nào.
6. `Listing` map với `Room` như thế nào.
7. `Room` map với Landlord như thế nào.
8. `ApiResponse` hiện tại.
9. `AppException`, `ErrorCode`, `GlobalExceptionHandler` hoặc class tương đương.
10. Convention Controller / Service / Repository / DTO hiện tại.
11. Flyway migration hiện tại.
12. `pom.xml` đã có WebSocket dependency hay chưa.

KHÔNG tạo duplicate infrastructure nếu project đã có.

KHÔNG tạo JWT implementation mới.

KHÔNG refactor những module không liên quan.

---

# 3. PACKAGE STRUCTURE PHẢI ĐÚNG VỚI PROJECT HIỆN TẠI

Project hiện tại đang có structure dạng:

```text
com.tamdaptit.rentalmanagement
│
├── address
├── auth
├── chat.entity
├── common
├── identity
├── listing.entity
├── notification.entity
├── payment.entity
├── rental.entity
├── report.entity
├── review.entity
├── room
└── storage
```

Entity chat hiện tại nằm tại:

```text
com.tamdaptit.rentalmanagement.chat.entity
```

PHẢI GIỮ NGUYÊN cách tổ chức này.

Sau khi implement, target structure:

```text
com.tamdaptit.rentalmanagement
│
├── chat
│   │
│   ├── config
│   │   ├── WebSocketConfig.java
│   │   └── WebSocketAuthInterceptor.java
│   │
│   ├── controller
│   │   ├── ChatController.java
│   │   └── ChatWebSocketController.java
│   │
│   ├── dto
│   │   ├── request
│   │   │   └── ChatMessageRequest.java
│   │   │
│   │   └── response
│   │       ├── ChatConversationResponse.java
│   │       ├── ChatMessageResponse.java
│   │       ├── ChatUserResponse.java
│   │       └── ChatListingResponse.java
│   │
│   ├── repository
│   │   ├── ChatConversationRepository.java
│   │   ├── ChatConversationParticipantRepository.java
│   │   └── ChatMessageRepository.java
│   │
│   └── service
│       ├── ChatService.java
│       └── impl
│           └── ChatServiceImpl.java
│
├── chat.entity
│   ├── ChatConversation.java
│   ├── ChatConversationParticipant.java
│   ├── ChatMessage.java
│   └── enums
│       └── MessageType.java
│
├── address
├── auth
├── common
├── identity
├── listing.entity
├── notification.entity
├── payment.entity
├── rental.entity
├── report.entity
├── review.entity
├── room
└── storage
```

Package declarations phải tương ứng:

```java
package com.tamdaptit.rentalmanagement.chat.entity;
```

```java
package com.tamdaptit.rentalmanagement.chat.entity.enums;
```

```java
package com.tamdaptit.rentalmanagement.chat.controller;
```

```java
package com.tamdaptit.rentalmanagement.chat.dto.request;
```

```java
package com.tamdaptit.rentalmanagement.chat.dto.response;
```

```java
package com.tamdaptit.rentalmanagement.chat.repository;
```

```java
package com.tamdaptit.rentalmanagement.chat.service;
```

```java
package com.tamdaptit.rentalmanagement.chat.service.impl;
```

```java
package com.tamdaptit.rentalmanagement.chat.config;
```

KHÔNG chuyển các entity chat vào:

```text
com.tamdaptit.rentalmanagement.chat.entity.entity
```

hoặc bất kỳ package lồng sai nào khác.

---

# 4. DATABASE MODEL CHO CHAT

Model chat mới gồm 3 bảng chính:

```text
chat_conversations
chat_conversation_participants
chat_messages
```

Quan hệ:

```text
                 ChatConversation
                       |
             +---------+---------+
             |                   |
             | 1                 | 1
             v                   v
            N                   N
ChatConversationParticipant   ChatMessage
             |
             | N
             v
             1
            User
```

Không hard-code Tenant/Landlord trong conversation.

---

# 5. CHAT_CONVERSATIONS

Entity:

```text
com.tamdaptit.rentalmanagement.chat.entity.ChatConversation
```

Fields:

```text
id
listing
rentalRequest
lastMessageAt
createdAt
updatedAt
participants
messages
```

KHÔNG còn:

```java
User tenant;
User landlord;
```

Concept entity:

```java
@Entity
@Table(name = "chat_conversations")
public class ChatConversation {

    String id;

    Listing listing;

    RentalRequest rentalRequest;

    LocalDateTime lastMessageAt;

    LocalDateTime createdAt;

    LocalDateTime updatedAt;

    List<ChatConversationParticipant> participants;

    List<ChatMessage> messages;
}
```

Giữ các relationship LAZY hợp lý.

Không cần tự ý thêm `CascadeType.ALL` hoặc `orphanRemoval = true` nếu không thực sự cần.

---

# 6. CHAT_CONVERSATION_PARTICIPANTS

Tạo entity mới:

```text
com.tamdaptit.rentalmanagement.chat.entity.ChatConversationParticipant
```

Table:

```text
chat_conversation_participants
```

Fields:

```text
id
conversation
user
joinedAt
lastReadAt
```

Mapping concept:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "conversation_id", nullable = false)
ChatConversation conversation;

@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
User user;
```

Database phải có unique:

```text
UNIQUE(conversation_id, user_id)
```

Một User không được xuất hiện hai lần trong cùng conversation.

`lastReadAt` được sử dụng để quản lý unread message.

Lưu ý:

Mặc dù project có convention các bảng mapping đơn giản không cần timestamp, bảng này KHÔNG phải mapping thuần túy vì nó chứa trạng thái nghiệp vụ:

```text
joinedAt
lastReadAt
```

nên việc có các field này là chủ đích.

---

# 7. CHAT_MESSAGES

Entity:

```text
com.tamdaptit.rentalmanagement.chat.entity.ChatMessage
```

Fields:

```text
id
conversation
sender
content
messageType
createdAt
```

Không còn:

```text
isRead
```

Read state được quản lý thông qua:

```text
ChatConversationParticipant.lastReadAt
```

Enum giữ tại:

```text
com.tamdaptit.rentalmanagement.chat.entity.enums.MessageType
```

với:

```java
public enum MessageType {
    TEXT,
    IMAGE,
    SYSTEM
}
```

---

# 8. DATABASE / FLYWAY

Project sử dụng:

```text
ddl-auto=validate
```

Do đó Entity và database phải khớp tuyệt đối.

Database chat cần chuyển từ:

```text
chat_conversations
    tenant_id
    landlord_id

chat_messages
    is_read
```

sang:

```text
chat_conversations
    id
    listing_id
    rental_request_id
    last_message_at
    created_at
    updated_at
```

Tạo:

```text
chat_conversation_participants
```

với:

```text
id
conversation_id
user_id
joined_at
last_read_at
```

và:

```text
UNIQUE(conversation_id, user_id)
```

`chat_messages`:

```text
id
conversation_id
sender_id
content
message_type
created_at
```

Không còn:

```text
is_read
```

Trước khi thay đổi Flyway:

1. Inspect migrations hiện tại.
2. Xác định migration nào đã tồn tại.
3. Không đoán version tiếp theo.
4. Nếu migration cũ đã apply thì tạo migration mới.
5. Không sửa migration đã apply.
6. Nếu project hiện vẫn đang phát triển trên schema gốc chưa apply thì follow convention thực tế của repository.

Sau khi thay đổi phải đảm bảo Hibernate `ddl-auto=validate` pass.

---

# 9. BUSINESS RULES

Cho phép:

```text
TENANT   -> LANDLORD
LANDLORD -> TENANT

TENANT   -> ADMIN
ADMIN    -> TENANT

LANDLORD -> ADMIN
ADMIN    -> LANDLORD
```

Không cho phép:

```text
TENANT   -> TENANT

LANDLORD -> LANDLORD

SELF     -> SELF
```

ADMIN -> ADMIN không cần implement.

User có thể có nhiều Role.

Do đó phải kiểm tra tập Role thực tế của User.

Không được giả định mỗi User chỉ có một Role.

---

# 10. CHAT TỪ LISTING

Khi Tenant đang xem chi tiết Listing:

```text
Listing Detail
      |
      v
"Nhắn tin với chủ trọ"
```

Frontend gọi:

```http
POST /api/chat/conversations/listing/{listingId}
```

Frontend CHỈ gửi:

```text
listingId
```

KHÔNG gửi:

```text
landlordId
tenantId
```

Backend phải xác định current user từ JWT.

Backend xác định landlord server-side:

```text
listingId
    ↓
Listing
    ↓
Room
    ↓
Landlord
    ↓
User
```

Sử dụng relationship thực tế đang tồn tại trong project.

Không thêm `landlord_id` vào `listings`.

Không tin tưởng landlordId từ frontend.

---

# 11. GET OR CREATE LISTING CONVERSATION

Flow:

```text
Authenticated User
       ↓
Find Listing
       ↓
Listing -> Room -> Landlord
       ↓
Check currentUser != landlord
       ↓
Check role/business rule
       ↓
Find existing conversation
       ↓
       +----------------+
       |                |
     FOUND           NOT FOUND
       |                |
       v                v
    Return          Create Conversation
                         |
                         v
                   Create Participant
                    current user
                         |
                         v
                   Create Participant
                      landlord
                         |
                         v
                      Return
```

Conversation từ Listing phải được xác định bởi:

```text
listing
+
participant A
+
participant B
```

Ví dụ:

```text
Tenant U01
Landlord U02
Listing L01
```

=> Conversation:

```text
C01
```

Nếu U01 click lại L01:

```text
return C01
```

KHÔNG tạo C02.

Nhưng nếu cùng Tenant và Landlord nhưng Listing khác:

```text
U01
U02
L02
```

thì được tạo conversation khác.

---

# 12. CHỐNG DUPLICATE CONVERSATION

Phải xử lý trường hợp:

```text
double click
multiple requests
concurrent requests
```

không tạo ra nhiều conversation giống nhau.

Đối với Listing Conversation:

```text
listing + exact 2 participants
```

phải xác định duy nhất một logical conversation.

Đối với Direct Conversation:

```text
listing IS NULL
rentalRequest IS NULL
exact 2 participants
```

phải xác định duy nhất một logical conversation.

Thứ tự:

```text
A + B
```

và:

```text
B + A
```

là cùng một Direct Conversation.

Nếu schema hiện tại cần thêm cơ chế database-level để chống race condition thực sự:

- phân tích trước;
- chọn giải pháp tối thiểu;
- cập nhật Flyway;
- cập nhật Entity nếu cần;
- giải thích thay đổi trong final report.

Không âm thầm thay đổi schema ngoài scope.

---

# 13. DIRECT CHAT

Ở giao diện chat chính có chức năng:

```text
Tìm người để nhắn tin
```

Search API:

```http
GET /api/chat/users/search?keyword={keyword}
```

Tìm theo field thực tế đang tồn tại trong User.

Ví dụ nếu User có:

```text
fullName
email
```

thì có thể search theo các field đó.

Không tự thêm field mới vào User chỉ để search.

Không trả current user.

Chỉ trả user có status phù hợp để chat, ưu tiên:

```text
ACTIVE
```

theo enum/status hiện tại.

Không trả:

```text
password
citizenId
authVersion
refreshToken
```

hoặc dữ liệu nhạy cảm.

---

# 14. CHAT USER RESPONSE

Response tối thiểu:

```json
{
  "id": "user-id",
  "fullName": "Nguyen Van A",
  "avatarUrl": "...",
  "roles": [
    "LANDLORD"
  ]
}
```

Field cụ thể phải map theo User hiện tại.

Không expose User entity trực tiếp.

---

# 15. CREATE DIRECT CONVERSATION

Endpoint:

```http
POST /api/chat/conversations/direct/{userId}
```

Flow:

```text
Authenticated User
       ↓
Target User
       ↓
Check target exists
       ↓
Check target ACTIVE
       ↓
Check target != current user
       ↓
Validate Role Rules
       ↓
Find existing DIRECT conversation
       ↓
       +----------------+
       |                |
     FOUND           NOT FOUND
       |                |
       v                v
    Return          Create Conversation
                         |
                         v
                   Create 2 participants
                         |
                         v
                       Return
```

Direct conversation phải có:

```text
listing = NULL
rentalRequest = NULL
```

Không được nhầm Direct Conversation với Listing Conversation.

---

# 16. REST API PHẢI IMPLEMENT

Implement:

```http
POST /api/chat/conversations/listing/{listingId}
```

```http
POST /api/chat/conversations/direct/{userId}
```

```http
GET /api/chat/conversations
```

```http
GET /api/chat/conversations/{conversationId}
```

```http
GET /api/chat/conversations/{conversationId}/messages?page=0&size=20
```

```http
PUT /api/chat/conversations/{conversationId}/read
```

```http
GET /api/chat/users/search?keyword={keyword}
```

Follow `ApiResponse` convention hiện tại của project.

---

# 17. GET MY CONVERSATIONS

Endpoint:

```http
GET /api/chat/conversations
```

Chỉ lấy conversation mà current authenticated user là participant.

Sort ưu tiên:

```text
lastMessageAt DESC
```

Conversation chưa có message thì xử lý bằng `createdAt` hợp lý.

Không load conversation của user khác.

Response nên chứa:

```json
{
  "id": "conversation-id",

  "otherUser": {
    "id": "user-id",
    "fullName": "Nguyen Van A",
    "avatarUrl": "...",
    "roles": [
      "LANDLORD"
    ]
  },

  "listing": {
    "id": "listing-id",
    "title": "Phòng trọ Mễ Trì"
  },

  "lastMessage": {
    "id": "message-id",
    "content": "Phòng này còn không anh?",
    "senderId": "user-id",
    "messageType": "TEXT",
    "createdAt": "..."
  },

  "lastMessageAt": "...",

  "unreadCount": 2
}
```

Đối với Direct Conversation:

```text
listing = null
```

Không trả toàn bộ Listing entity.

Không trả toàn bộ User entity.

---

# 18. GET CONVERSATION DETAIL

Endpoint:

```http
GET /api/chat/conversations/{conversationId}
```

Backend phải:

```text
Find conversation
       ↓
Find current user
       ↓
Check current user is participant
       ↓
Return DTO
```

Nếu current user không thuộc conversation:

```text
403 / business exception phù hợp
```

Không được cho phép user đọc conversation chỉ vì họ biết UUID.

---

# 19. MESSAGE HISTORY

Endpoint:

```http
GET /api/chat/conversations/{conversationId}/messages?page=0&size=20
```

Phải hỗ trợ pagination.

Follow pagination convention hiện tại.

Trước khi query/return:

```text
Current User
       ↓
Check participant
       ↓
Allowed?
```

Nếu không:

```text
reject
```

Message response:

```json
{
  "id": "message-id",
  "conversationId": "conversation-id",

  "sender": {
    "id": "user-id",
    "fullName": "Nguyen Van A",
    "avatarUrl": "..."
  },

  "content": "Phòng này còn không anh?",
  "messageType": "TEXT",
  "createdAt": "..."
}
```

Không trả User entity.

Không trả ChatConversation entity.

---

# 20. MARK AS READ

Endpoint:

```http
PUT /api/chat/conversations/{conversationId}/read
```

Flow:

```text
Current User
       ↓
Find ChatConversationParticipant
       ↓
Check membership
       ↓
lastReadAt = now
       ↓
Save
```

Không update từng `ChatMessage`.

---

# 21. UNREAD COUNT

Không dùng:

```text
ChatMessage.isRead
```

Unread được tính từ:

```text
ChatConversationParticipant.lastReadAt
```

Logic:

```text
message.conversation = conversation

AND

message.sender != currentUser

AND

message.createdAt > participant.lastReadAt
```

Nếu:

```text
lastReadAt == null
```

thì dùng:

```text
joinedAt
```

làm lower bound phù hợp.

Ví dụ:

```text
Tenant lastReadAt = 10:00

Landlord gửi:
10:01
10:02
10:03
```

=> Tenant:

```text
unreadCount = 3
```

Nếu Tenant gửi message lúc:

```text
10:04
```

message của Tenant không được tính unread cho chính Tenant.

Không load toàn bộ message list về Java chỉ để `.size()`.

Dùng query count trực tiếp.

---

# 22. REPOSITORIES

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.repository.ChatConversationRepository
```

```text
com.tamdaptit.rentalmanagement.chat.repository.ChatConversationParticipantRepository
```

```text
com.tamdaptit.rentalmanagement.chat.repository.ChatMessageRepository
```

Repository phải hỗ trợ:

```text
find conversations of current user

find participant by conversation + user

exists participant by conversation + user

find listing conversation by listing + exact participants

find direct conversation by exact participants

get paginated messages

get latest message

count unread messages
```

Không load toàn bộ history chỉ để:

```text
find latest message
count unread
find conversation
```

Tránh N+1 query rõ ràng khi lấy conversation list.

Nếu cần JPQL:

```java
@Query(...)
```

thì dùng JPQL rõ ràng và parameter binding.

Không concatenate input trực tiếp vào query.

---

# 23. DTO STRUCTURE

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.dto.request
```

và:

```text
com.tamdaptit.rentalmanagement.chat.dto.response
```

Tối thiểu:

```text
ChatMessageRequest
ChatConversationResponse
ChatMessageResponse
ChatUserResponse
ChatListingResponse
```

Có thể tạo DTO bổ sung nếu thực sự cần.

Không trả JPA Entity trực tiếp từ Controller.

---

# 24. CHAT SERVICE

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.service.ChatService
```

và:

```text
com.tamdaptit.rentalmanagement.chat.service.impl.ChatServiceImpl
```

Service tối thiểu cover:

```text
getOrCreateListingConversation(...)

getOrCreateDirectConversation(...)

getMyConversations(...)

getConversation(...)

getMessages(...)

markAsRead(...)

searchUsers(...)

sendMessage(...)
```

Tên method có thể điều chỉnh nhẹ theo convention project.

KHÔNG tạo:

```text
TenantChatService
LandlordChatService
AdminChatService
```

Toàn bộ dùng chung ChatService.

---

# 25. TRANSACTION

Các operation sau phải transaction:

```text
create conversation + create participants
```

```text
save message + update lastMessageAt
```

```text
mark as read
```

Dùng:

```java
@Transactional
```

theo convention project.

Không để trạng thái:

```text
Conversation created
Participants failed
```

hoặc:

```text
Message saved
Conversation update failed
```

---

# 26. WEBSOCKET + STOMP

Sử dụng:

```text
Spring WebSocket
STOMP
```

WebSocket endpoint:

```text
/ws
```

Application destination prefix:

```text
/app
```

User destination prefix:

```text
/user
```

Broker destination:

```text
/queue
```

Frontend connect:

```text
/ws
```

Frontend subscribe:

```text
/user/queue/messages
```

Frontend send:

```text
/app/chat.send
```

Không sử dụng public:

```text
/topic/conversation/{id}
```

cho private chat nếu không cần thiết.

Private message phải sử dụng user destination.

---

# 27. WEBSOCKET CONFIG

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.config.WebSocketConfig
```

Concept:

```java
@EnableWebSocketMessageBroker
@Configuration
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {
}
```

Configure:

```text
/ws
/app
/user
/queue
```

Đọc CORS config hiện tại.

Không hard-code wildcard origin không an toàn nếu project đã có frontend origin configuration.

---

# 28. WEBSOCKET JWT AUTHENTICATION

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.config.WebSocketAuthInterceptor
```

hoặc tên phù hợp với convention project.

Frontend STOMP CONNECT gửi:

```text
Authorization: Bearer <access_token>
```

Interceptor phải xử lý STOMP CONNECT.

Flow:

```text
STOMP CONNECT
      ↓
Authorization
      ↓
Bearer token
      ↓
Reuse JWT validation hiện tại
      ↓
Build/reuse Authentication
      ↓
accessor.setUser(authentication)
```

TUYỆT ĐỐI:

- không tạo JWT service mới;
- không duplicate secret;
- không duplicate token parser;
- không implement authentication riêng biệt với REST.

Phải reuse authentication/JWT infrastructure trong:

```text
auth
```

hoặc package security hiện tại.

---

# 29. PRINCIPAL IDENTITY

Phải inspect authentication hiện tại để xác định:

```java
principal.getName()
```

trả về:

```text
email
```

hay:

```text
userId
```

hay:

```text
username
```

Không tự giả định.

Điều này rất quan trọng vì:

```java
messagingTemplate.convertAndSendToUser(...)
```

phải sử dụng đúng cùng identifier với WebSocket Principal.

Ví dụ nếu Principal name là email thì phải dùng:

```java
messagingTemplate.convertAndSendToUser(
    receiver.getEmail(),
    "/queue/messages",
    response
);
```

Không được dùng receiver ID trong trường hợp Principal name là email.

---

# 30. CHAT MESSAGE REQUEST

Frontend gửi:

```json
{
  "conversationId": "conversation-id",
  "content": "Phòng này còn không anh?",
  "messageType": "TEXT"
}
```

DTO:

```text
ChatMessageRequest
```

KHÔNG nhận:

```text
senderId
receiverId
```

SAI:

```json
{
  "conversationId": "C01",
  "senderId": "U01",
  "receiverId": "U02",
  "content": "Hello"
}
```

ĐÚNG:

```json
{
  "conversationId": "C01",
  "content": "Hello",
  "messageType": "TEXT"
}
```

Sender:

```text
authenticated Principal
```

Receiver:

```text
other conversation participant
```

---

# 31. WEBSOCKET CONTROLLER

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.controller.ChatWebSocketController
```

Endpoint:

```java
@MessageMapping("/chat.send")
```

Concept:

```java
@MessageMapping("/chat.send")
public void sendMessage(
        ChatMessageRequest request,
        Principal principal
) {
    chatService.sendMessage(request, principal);
}
```

Có thể điều chỉnh signature để phù hợp authentication architecture hiện tại.

Không lấy sender từ request.

---

# 32. SEND MESSAGE BUSINESS FLOW

Flow bắt buộc:

```text
STOMP SEND
/app/chat.send
      ↓
ChatMessageRequest
      +
Principal
      ↓
Resolve current User
      ↓
Find ChatConversation
      ↓
Check current User is participant
      ↓
Validate Message
      ↓
Save ChatMessage
      ↓
Update lastMessageAt
      ↓
Find other participant
      ↓
Build ChatMessageResponse
      ↓
Push realtime
```

Phải:

```text
SAVE DATABASE FIRST
```

sau đó mới:

```text
PUSH WEBSOCKET
```

Database là source of truth.

---

# 33. MESSAGE VALIDATION

Trước khi save:

- conversation phải tồn tại;
- sender phải là participant;
- current user phải được phép sử dụng chat;
- `content` không null;
- TEXT không blank;
- `messageType` hợp lệ;
- giới hạn content hợp lý theo convention project.

Không cho:

```text
User A
```

gửi vào:

```text
Conversation(User B, User C)
```

chỉ bằng cách biết conversation UUID.

---

# 34. SAVE MESSAGE

Concept:

```java
ChatMessage message = ChatMessage.builder()
        .conversation(conversation)
        .sender(currentUser)
        .content(request.getContent())
        .messageType(request.getMessageType())
        .build();
```

Save:

```text
ChatMessageRepository
```

Sau đó update:

```text
ChatConversation.lastMessageAt
```

Cẩn thận với `@CreationTimestamp`.

Không được assume `createdAt` đã có giá trị trước persist nếu Hibernate chưa populate.

Có thể sử dụng một:

```java
LocalDateTime now = LocalDateTime.now();
```

phù hợp cho operation hoặc save/flush đúng cách.

---

# 35. REALTIME DELIVERY

Sử dụng:

```text
SimpMessagingTemplate
```

Receiver:

```java
messagingTemplate.convertAndSendToUser(
        receiverPrincipalName,
        "/queue/messages",
        response
);
```

Frontend receiver subscribe:

```text
/user/queue/messages
```

Nên push authoritative saved response về sender nữa để sender nhận:

```text
messageId
createdAt
```

do backend tạo.

Không để frontend tự generate authoritative message ID.

---

# 36. OFFLINE USER

Nếu receiver offline:

```text
WebSocket push có thể không được nhận
```

nhưng message đã được lưu MySQL.

Khi receiver login:

```http
GET /api/chat/conversations
```

phải thấy:

```text
unreadCount > 0
```

Sau đó:

```http
GET /api/chat/conversations/{id}/messages
```

để load history.

Không cần message queue riêng chỉ để xử lý offline chat ở scope hiện tại.

---

# 37. IMAGE MESSAGE

Enum hiện có:

```text
IMAGE
```

Nếu project đã có Cloudinary/storage service:

```text
com.tamdaptit.rentalmanagement.storage
```

thì reuse khi cần.

Không duplicate Cloudinary implementation.

Trong scope hiện tại, TEXT message phải hoạt động hoàn chỉnh.

Nếu image upload chưa cần implement ngay:

- giữ `MessageType.IMAGE`;
- không phá khả năng mở rộng;
- không tự xây hệ thống upload mới ngoài scope.

---

# 38. REST CONTROLLER

Tạo:

```text
com.tamdaptit.rentalmanagement.chat.controller.ChatController
```

Controller chỉ chịu trách nhiệm:

```text
receive request
validate request annotation nếu cần
call ChatService
wrap ApiResponse theo convention
```

Không đặt business logic lớn trong Controller.

Không query Repository trực tiếp từ Controller nếu architecture hiện tại dùng Service layer.

---

# 39. AUTHORIZATION

REST và WebSocket đều phải authorization.

Các operation:

```text
GET conversation
GET messages
PUT read
SEND message
```

đều phải verify:

```text
current user is participant
```

Không dựa vào việc UUID khó đoán.

---

# 40. SECURITY CONFIG

Đọc SecurityConfig hiện tại trước khi sửa.

REST:

```text
/api/chat/**
```

phải authenticated.

WebSocket handshake:

```text
/ws/**
```

cấu hình phù hợp với STOMP JWT authentication.

Nếu HTTP handshake `/ws` cần `permitAll` thì authentication thực tế vẫn phải được thực hiện ở STOMP CONNECT.

Không làm:

```java
requestMatchers("/**").permitAll();
```

Không làm yếu Security toàn hệ thống để WebSocket chạy.

---

# 41. ERROR HANDLING

Reuse error architecture hiện tại.

Ví dụ project có:

```text
AppException
ErrorCode
GlobalExceptionHandler
ApiResponse
```

thì dùng lại.

Các lỗi cần cover:

```text
LISTING_NOT_FOUND
USER_NOT_FOUND
CONVERSATION_NOT_FOUND
NOT_CONVERSATION_PARTICIPANT
CANNOT_CHAT_WITH_SELF
CHAT_NOT_ALLOWED
INVALID_CHAT_MESSAGE
```

Không bắt buộc dùng chính xác tên trên nếu project có naming convention khác.

Không tạo exception architecture thứ hai.

---

# 42. ROLE VALIDATION

User có thể có nhiều roles.

Ví dụ:

```text
User A:
TENANT
LANDLORD
```

Do đó validation phải dựa trên role set.

Không code cứng:

```java
user.getRole()
```

nếu model hiện tại là:

```text
User -> UserRole -> Role
```

Phải inspect entity thực tế.

Role rules:

```text
TENANT + LANDLORD -> allowed
TENANT + ADMIN    -> allowed
LANDLORD + ADMIN  -> allowed
```

Không cho nếu hai bên chỉ tạo thành:

```text
TENANT + TENANT
```

hoặc:

```text
LANDLORD + LANDLORD
```

Self chat luôn bị chặn.

---

# 43. LISTING CONVERSATION EXAMPLE

Ví dụ:

```text
Tenant:
U01

Listing:
L01

L01.room.landlord:
U02
```

Request:

```http
POST /api/chat/conversations/listing/L01
```

Backend:

```text
JWT
 ↓
U01
 ↓
L01
 ↓
L01.room
 ↓
U02
 ↓
Find conversation:
L01 + U01 + U02
 ↓
Not found
 ↓
Create C01
```

Database:

```text
chat_conversations

C01
listing_id = L01
rental_request_id = NULL
```

Participants:

```text
chat_conversation_participants

P01 | C01 | U01
P02 | C01 | U02
```

Không lưu:

```text
tenant_id = U01
landlord_id = U02
```

trong `chat_conversations`.

---

# 44. DIRECT CHAT EXAMPLE

Tenant:

```text
U01
```

Admin:

```text
U99
```

Request:

```http
POST /api/chat/conversations/direct/U99
```

Database:

```text
chat_conversations

C02
listing_id = NULL
rental_request_id = NULL
```

Participants:

```text
P03 | C02 | U01
P04 | C02 | U99
```

Cùng model này cũng dùng cho:

```text
LANDLORD <-> ADMIN
```

Không cần tạo loại Conversation riêng.

---

# 45. MESSAGE EXAMPLE

Frontend:

```text
SEND /app/chat.send
```

Payload:

```json
{
  "conversationId": "C01",
  "content": "Phòng này còn không anh?",
  "messageType": "TEXT"
}
```

Backend xác định:

```text
sender = U01
```

từ JWT.

Database:

```text
chat_messages

id              = M01
conversation_id = C01
sender_id       = U01
content         = "Phòng này còn không anh?"
message_type    = TEXT
created_at      = ...
```

Sau đó push tới U02.

---

# 46. DO NOT RETURN ENTITIES

Không return:

```java
ChatConversation
ChatMessage
User
Listing
```

trực tiếp từ Controller.

Lý do:

- Lazy loading
- circular relationship
- expose dữ liệu không cần thiết
- khó kiểm soát response
- security

Luôn map sang DTO.

---

# 47. PERFORMANCE

Không tạo implementation kiểu:

```text
load all conversations
→ load all participants từng conversation
→ load all messages từng conversation
→ Java filter
```

Phải query hợp lý.

Đặc biệt:

```text
GET /api/chat/conversations
```

không được gây N+1 nghiêm trọng.

Unread count không được load toàn bộ message.

Latest message không được load toàn bộ history.

Messages phải pagination.

---

# 48. INDEXES

Kiểm tra và tạo index cần thiết cho chat nếu schema chưa có.

Tối thiểu cân nhắc:

```text
chat_conversation_participants(user_id)
chat_conversation_participants(conversation_id)

chat_messages(conversation_id, created_at)
chat_messages(sender_id)
```

Unique:

```text
chat_conversation_participants(
    conversation_id,
    user_id
)
```

Không thêm index dư thừa nếu unique/index hiện tại đã cover.

Flyway và Entity metadata nếu khai báo index phải đồng bộ.

---

# 49. DEPENDENCIES

Kiểm tra `pom.xml`.

Nếu chưa có Spring WebSocket thì thêm dependency phù hợp với Spring Boot version hiện tại.

Không hard-code version dependency nếu Spring Boot dependency management đã quản lý.

Không thêm library WebSocket bên ngoài nếu Spring WebSocket đã đủ.

---

# 50. CODE QUALITY

Code phải:

- compile;
- không unused import;
- không duplicate class;
- không duplicate JWT logic;
- không expose password;
- không expose entity;
- không circular JSON;
- transaction đúng;
- authorization đúng;
- repository query hợp lý;
- follow Lombok convention hiện tại;
- follow naming convention hiện tại;
- follow package structure hiện tại.

Không tự ý refactor code không liên quan.

---

# 51. TEST / VERIFY

Sau khi implement phải compile project.

Sử dụng command phù hợp với project, ví dụ:

```text
mvnw compile
```

hoặc:

```text
./mvnw compile
```

Sau đó chạy tests hiện tại nếu có.

Nếu environment cho phép thì startup application để kiểm tra:

```text
Hibernate ddl-auto=validate
```

---

# 52. CASES PHẢI VERIFY

Tối thiểu kiểm tra:

### Case 1

```text
TENANT -> LANDLORD từ Listing
```

Expected:

```text
conversation được tạo
2 participants
listing context đúng
```

### Case 2

Click lại cùng Listing.

Expected:

```text
return conversation cũ
không duplicate
```

### Case 3

```text
TENANT -> ADMIN
```

Expected:

```text
direct conversation
```

### Case 4

```text
LANDLORD -> ADMIN
```

Expected:

```text
direct conversation
```

### Case 5

```text
TENANT -> TENANT
```

Expected:

```text
reject
```

### Case 6

```text
LANDLORD -> LANDLORD
```

Expected:

```text
reject
```

### Case 7

```text
User -> chính mình
```

Expected:

```text
reject
```

### Case 8

User A truy cập messages của conversation B-C.

Expected:

```text
reject
```

### Case 9

User A gửi WebSocket message vào conversation B-C.

Expected:

```text
reject
```

### Case 10

Receiver online.

Expected:

```text
save DB
+
receiver nhận realtime
```

### Case 11

Receiver offline.

Expected:

```text
message vẫn lưu DB
+
unreadCount tăng
```

### Case 12

Mark read.

Expected:

```text
lastReadAt update
+
unreadCount = 0
```

---

# 53. KHÔNG ĐƯỢC LÀM

KHÔNG:

```text
- Di chuyển Chat entity sang package khác
- Refactor toàn bộ project structure
- Tạo JWT service mới
- Tạo Security system mới
- Hard-code ADMIN id
- Hard-code LANDLORD id
- Nhận senderId từ client
- Nhận receiverId khi gửi message
- Tin landlordId từ frontend
- Return Entity trực tiếp
- Tạo conversation mới mỗi lần click
- Dùng WebSocket làm persistent storage
- Cho user đọc conversation chỉ vì biết UUID
- Tạo TenantChatService
- Tạo LandlordChatService
- Tạo AdminChatService
- Sửa module không liên quan nếu không cần
```

---

# 54. IMPLEMENTATION ORDER

Thực hiện đúng thứ tự:

```text
1. Inspect project structure
        ↓
2. Inspect pom.xml
        ↓
3. Inspect JWT / Security
        ↓
4. Inspect User / Role
        ↓
5. Inspect Listing / Room
        ↓
6. Inspect current Chat entities
        ↓
7. Inspect Flyway migrations
        ↓
8. Update ChatConversation
        ↓
9. Create ChatConversationParticipant
        ↓
10. Update ChatMessage
        ↓
11. Update/create Flyway migration
        ↓
12. Create repositories
        ↓
13. Create DTOs
        ↓
14. Implement ChatService
        ↓
15. Implement ChatServiceImpl
        ↓
16. Implement REST ChatController
        ↓
17. Implement WebSocketConfig
        ↓
18. Implement WebSocketAuthInterceptor
        ↓
19. Implement ChatWebSocketController
        ↓
20. Implement sendMessage
        ↓
21. Implement unread / mark read
        ↓
22. Verify authorization
        ↓
23. Compile
        ↓
24. Run tests
        ↓
25. Fix errors introduced by this task
        ↓
26. Final report
```

Không dừng sau khi tạo skeleton.

Implement end-to-end.

---

# 55. EXPECTED FINAL STRUCTURE

Sau khi hoàn thành, phần Chat phải tương tự:

```text
src/main/java/com/tamdaptit/rentalmanagement
│
├── chat
│   │
│   ├── config
│   │   ├── WebSocketConfig.java
│   │   └── WebSocketAuthInterceptor.java
│   │
│   ├── controller
│   │   ├── ChatController.java
│   │   └── ChatWebSocketController.java
│   │
│   ├── dto
│   │   ├── request
│   │   │   └── ChatMessageRequest.java
│   │   │
│   │   └── response
│   │       ├── ChatConversationResponse.java
│   │       ├── ChatMessageResponse.java
│   │       ├── ChatUserResponse.java
│   │       └── ChatListingResponse.java
│   │
│   ├── repository
│   │   ├── ChatConversationRepository.java
│   │   ├── ChatConversationParticipantRepository.java
│   │   └── ChatMessageRepository.java
│   │
│   └── service
│       ├── ChatService.java
│       └── impl
│           └── ChatServiceImpl.java
│
├── chat.entity
│   ├── ChatConversation.java
│   ├── ChatConversationParticipant.java
│   ├── ChatMessage.java
│   └── enums
│       └── MessageType.java
│
├── address
├── auth
├── common
├── identity
├── listing.entity
├── notification.entity
├── payment.entity
├── rental.entity
├── report.entity
├── review.entity
├── room
└── storage
```

Entity PHẢI tiếp tục nằm trong:

```text
com.tamdaptit.rentalmanagement.chat.entity
```

Logic chat nằm trong:

```text
com.tamdaptit.rentalmanagement.chat
```

---

# 56. FINAL REPORT

Sau khi code xong, trả report ngắn gồm:

```text
1. Files created
2. Files modified
3. Flyway/database changes
4. REST APIs implemented
5. WebSocket destinations implemented
6. JWT WebSocket authentication approach
7. Conversation duplicate prevention
8. Authorization rules
9. Unread/read implementation
10. Build result
11. Test result
12. Remaining issues
```

Nếu build có lỗi tồn tại từ trước phải ghi rõ:

```text
PRE-EXISTING ISSUE
```

Nếu lỗi do implementation vừa tạo:

```text
ISSUE INTRODUCED BY THIS TASK
```

Không được bỏ qua compile errors do code vừa implement.

Mục tiêu cuối cùng là feature Chat phải hoạt động end-to-end:

```text
REST
+
MySQL persistence
+
WebSocket realtime
+
JWT authentication
+
Conversation authorization
+
Unread messages
```

và phải tuân thủ chính xác package structure hiện tại của project.