package com.tamdaptit.rentalmanagement.review.mapper;

import com.tamdaptit.rentalmanagement.review.dto.response.ReviewResponse;
import com.tamdaptit.rentalmanagement.review.entity.LandlordReview;
import com.tamdaptit.rentalmanagement.review.entity.RoomReview;
import com.tamdaptit.rentalmanagement.review.entity.TenantReview;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {
    @Mapping(target = "type", constant = "ROOM")
    @Mapping(target = "comment", expression = "java(visibleText(review.getComment(), review.getTextStatus()))")
    @Mapping(
        target = "landlordReply",
        expression = "java(visibleText(review.getLandlordReply(), review.getReplyStatus()))"
    )
    @Mapping(target = "verifiedStay", ignore = true)
    @Mapping(target = "authorName", source = "tenant.fullName")
    @Mapping(target = "authorAvatarUrl", source = "tenant.avatarUrl")
    ReviewResponse toResponse(RoomReview review);

    @Mapping(target = "type", constant = "LANDLORD")
    @Mapping(target = "comment", expression = "java(visibleText(review.getComment(), review.getTextStatus()))")
    @Mapping(
        target = "landlordReply",
        expression = "java(visibleText(review.getLandlordReply(), review.getReplyStatus()))"
    )
    @Mapping(target = "verifiedStay", ignore = true)
    @Mapping(target = "authorName", source = "tenant.fullName")
    @Mapping(target = "authorAvatarUrl", source = "tenant.avatarUrl")
    ReviewResponse toResponse(LandlordReview review);

    @Mapping(target = "type", constant = "TENANT")
    @Mapping(target = "textStatus", constant = "VISIBLE")
    @Mapping(target = "replyStatus", constant = "VISIBLE")
    @Mapping(target = "verifiedStay", ignore = true)
    @Mapping(target = "authorName", source = "landlord.fullName")
    @Mapping(target = "authorAvatarUrl", source = "landlord.avatarUrl")
    @Mapping(target = "landlordReply", ignore = true)
    ReviewResponse toResponse(TenantReview review);

    default String visibleText(String text, String status) {
        if (text == null || text.isBlank()) {
            return text;
        }

        return "VISIBLE".equals(status) ? text : "Nội dung đang được kiểm duyệt.";
    }
}
