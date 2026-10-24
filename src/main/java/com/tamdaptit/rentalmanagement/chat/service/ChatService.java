package com.tamdaptit.rentalmanagement.chat.service;

import com.tamdaptit.rentalmanagement.chat.dto.request.ChatMessageRequest;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatConversationResponse;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatMessageResponse;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatUserResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;

import java.security.Principal;
import java.util.List;

public interface ChatService {

    /**
     * Tạo hoặc lấy conversation từ listing.
     * Tenant nhìn thấy listing -> nhấn nhắn tin -> gọi API này.
     */
    ChatConversationResponse getOrCreateListingConversation(String listingId, String currentUserId);

    /**
     * Tạo hoặc lấy support conversation giữa current user và Admin hệ thống.
     */
    ChatConversationResponse getOrCreateSupportConversation(String currentUserId);

    /**
     * Lấy danh sách conversations của current user.
     */
    List<ChatConversationResponse> getMyConversations(String currentUserId);

    /**
     * Lấy chi tiết một conversation (kiểm tra current user là participant).
     */
    ChatConversationResponse getConversation(String conversationId, String currentUserId);

    /**
     * Lấy lịch sử tin nhắn của conversation (phân trang).
     */
    PageResponse<ChatMessageResponse> getMessages(String conversationId, String currentUserId, int page, int size);

    /**
     * Đánh dấu đã đọc tất cả tin nhắn trong conversation.
     */
    void markAsRead(String conversationId, String currentUserId);

    /**
     * Xử lý gửi tin nhắn qua WebSocket.
     * Lưu vào DB rồi push realtime tới topic /topic/conversations/{conversationId}.
     */
    void sendMessage(ChatMessageRequest request, Principal principal);
}
