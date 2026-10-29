package com.tamdaptit.rentalmanagement.chat.controller;

import com.tamdaptit.rentalmanagement.chat.dto.response.ChatConversationResponse;
import com.tamdaptit.rentalmanagement.chat.dto.response.ChatMessageResponse;
import com.tamdaptit.rentalmanagement.chat.service.ChatService;
import com.tamdaptit.rentalmanagement.common.dto.ApiResponse;
import com.tamdaptit.rentalmanagement.common.dto.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Tag(name = "Chat", description = "Quản lý trò chuyện và hỗ trợ trực tuyến")
public class ChatController {

    ChatService chatService;

    @PostMapping("/conversations/listing/{listingId}")
    @Operation(summary = "Tạo hoặc lấy cuộc trò chuyện với chủ trọ từ tin phòng")
    public ApiResponse<ChatConversationResponse> getOrCreateListingConversation(
            @PathVariable String listingId,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        return ApiResponse.success("Lấy cuộc trò chuyện thành công",
                chatService.getOrCreateListingConversation(listingId, currentUserId));
    }

    @PostMapping("/conversations/support")
    @Operation(summary = "Tenant/Landlord mở hỗ trợ với tài khoản Admin cố định trong Flyway")
    public ApiResponse<ChatConversationResponse> getOrCreateSupportConversation(
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        return ApiResponse.success("Lấy cuộc trò chuyện hỗ trợ thành công",
                chatService.getOrCreateSupportConversation(currentUserId));
    }

    @GetMapping("/conversations")
    @Operation(summary = "Lấy danh sách cuộc trò chuyện của tôi")
    public ApiResponse<List<ChatConversationResponse>> getMyConversations(
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        return ApiResponse.success("Lấy danh sách cuộc trò chuyện thành công",
                chatService.getMyConversations(currentUserId));
    }

    @GetMapping("/conversations/{conversationId}")
    @Operation(summary = "Lấy chi tiết một cuộc trò chuyện")
    public ApiResponse<ChatConversationResponse> getConversation(
            @PathVariable String conversationId,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        return ApiResponse.success("Lấy chi tiết cuộc trò chuyện thành công",
                chatService.getConversation(conversationId, currentUserId));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(summary = "Lấy lịch sử tin nhắn của cuộc trò chuyện (phân trang)")
    public ApiResponse<PageResponse<ChatMessageResponse>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        return ApiResponse.success("Lấy lịch sử tin nhắn thành công",
                chatService.getMessages(conversationId, currentUserId, page, size));
    }

    @PutMapping("/conversations/{conversationId}/read")
    @Operation(summary = "Đánh dấu đã đọc tất cả tin nhắn trong cuộc trò chuyện")
    public ApiResponse<Void> markAsRead(
            @PathVariable String conversationId,
            Authentication authentication
    ) {
        String currentUserId = authentication.getName();
        chatService.markAsRead(conversationId, currentUserId);
        return ApiResponse.success("Đã đánh dấu đọc thành công", null);
    }
}
