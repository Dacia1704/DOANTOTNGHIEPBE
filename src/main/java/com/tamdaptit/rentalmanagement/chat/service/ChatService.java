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
     * Tenant/Landlord tạo hoặc lấy cuộc hỗ trợ với duy nhất tài khoản Admin được seed trong Flyway.
     * Không nhận receiverId từ client, không tìm một Admin bất kỳ và không tạo cuộc tự chat.
     */
    ChatConversationResponse getOrCreateSupportConversation(String currentUserId);

    /**
     * Chỉ lấy cuộc trò chuyện của current user thuộc hai luồng: bài đăng hoặc hỗ trợ Admin cố định.
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
     * Lưu/flush vào DB và chỉ push realtime sau khi commit, bao gồm xác nhận về queue của người gửi.
     */
    void sendMessage(ChatMessageRequest request, Principal principal);
}
