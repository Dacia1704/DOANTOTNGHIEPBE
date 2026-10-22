package com.tamdaptit.rentalmanagement.common.exception;

import jakarta.validation.constraints.Email;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    CURRENT_PASSWORD_INCORRECT(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng"),
    PASSWORD_CONFIRMATION_MISMATCH(HttpStatus.BAD_REQUEST, "Xác nhận mật khẩu mới không khớp"),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại"),
    USER_IN_USE(HttpStatus.CONFLICT, "Người dùng đang có dữ liệu liên quan, hãy khóa tài khoản thay vì xóa"),
    PROTECTED_ADMIN(HttpStatus.CONFLICT, "Không thể xóa hoặc vô hiệu hóa tài khoản admin"),
    CLOUDINARY_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "Chưa cấu hình Cloudinary"),
    IMAGE_UPLOAD_FAILED(HttpStatus.BAD_GATEWAY, "Không thể tải tệp lên bộ lưu trữ ảnh"),
    IMAGE_INVALID(HttpStatus.BAD_REQUEST, "Ảnh phải là tệp JPEG/PNG hợp lệ, tối đa 5MB và 4096x4096 pixel"),
    IMAGE_TOO_MANY(HttpStatus.BAD_REQUEST, "Số lượng ảnh vượt quá giới hạn cho phép"),
    IMAGE_DELETE_FAILED(HttpStatus.BAD_GATEWAY, "Không thể xóa ảnh khỏi bộ lưu trữ"),
    ADDRESS_CODE_EXISTS(HttpStatus.CONFLICT, "Mã đơn vị hành chính đã tồn tại"),
    ADDRESS_IN_USE(HttpStatus.CONFLICT, "Phường/xã đang được tham chiếu bởi phòng hoặc hồ sơ người thuê"),
    ADDRESS_SYNC_FAILED(HttpStatus.BAD_GATEWAY, "Không thể tải dữ liệu đơn vị hành chính từ nguồn"),
    // ===== AUTHENTICATION / AUTHORIZATION =====
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Xác minh người dùng thất bại"),
    UNAUTHORIZED(HttpStatus.FORBIDDEN, "Bạn không có quyền truy cập tài nguyên này"),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng"),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "Refresh token không hợp lệ hoặc đã hết hạn"),
    TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token đã hết hạn"),

    // ===== USER =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "Người dùng không tồn tại"),
    USER_DELETED(HttpStatus.BAD_REQUEST, "Tài khoản người dùng đã bị xóa"),
    USER_INACTIVE(HttpStatus.BAD_REQUEST, "Tài khoản người dùng không hoạt động"),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "Tài khoản người dùng đã bị khóa"),
    EMAIL_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Email đã tồn tại"),
    PHONE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Số điện thoại đã tồn tại"),
    CITIZEN_ID_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Số CCCD/CMND đã tồn tại"),
    INVALID_REGISTER_ROLE(HttpStatus.BAD_REQUEST, "Không thể đăng ký với vai trò này"),

    // ===== ROLE & PERMISSION =====
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Vai trò không tồn tại"),
    PERMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Quyền không tồn tại"),
    ROLE_ALREADY_ASSIGNED(HttpStatus.BAD_REQUEST, "Người dùng đã có vai trò này"),

    // ===== ADDRESS (Tỉnh / Quận / Phường) =====
    PROVINCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Tỉnh/Thành phố không tồn tại"),
    DISTRICT_NOT_FOUND(HttpStatus.NOT_FOUND, "Quận/Huyện không tồn tại"),
    WARD_NOT_FOUND(HttpStatus.NOT_FOUND, "Phường/Xã không tồn tại"),

    // ===== ROOM =====
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "Phòng không tồn tại"),
    ROOM_TYPE_NOT_FOUND(HttpStatus.NOT_FOUND, "Loại phòng không tồn tại"),
    ROOM_TYPE_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Tên loại phòng đã tồn tại"),
    ROOM_TYPE_HAS_CHILDREN(HttpStatus.BAD_REQUEST, "Không thể xóa loại phòng đang có loại con"),
    ROOM_TYPE_IN_USE(HttpStatus.BAD_REQUEST, "Không thể xóa loại phòng đang được sử dụng bởi phòng"),
    INVALID_ROOM_TYPE_PARENT(HttpStatus.BAD_REQUEST, "Loại phòng cha không hợp lệ"),
    AMENITY_NOT_FOUND(HttpStatus.NOT_FOUND, "Tiện ích không tồn tại"),
    AMENITY_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Tên tiện ích đã tồn tại"),
    AMENITY_IN_USE(HttpStatus.CONFLICT, "Tiện ích đang được gán cho phòng, không thể xóa"),
    ROOM_IMAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Hình ảnh phòng không tồn tại"),
    ROOM_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Phòng không còn trống"),
    ROOM_ALREADY_RENTED(HttpStatus.BAD_REQUEST, "Phòng đã có người thuê"),
    ROOM_MAINTENANCE(HttpStatus.BAD_REQUEST, "Phòng đang bảo trì"),
    NOT_ROOM_OWNER(HttpStatus.FORBIDDEN, "Bạn không phải chủ trọ của phòng này"),
    INVALID_ROOM_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái phòng không hợp lệ"),

    // ===== LISTING =====
    LISTING_NOT_FOUND(HttpStatus.NOT_FOUND, "Tin đăng không tồn tại"),
    LISTING_SLUG_EXISTED(HttpStatus.BAD_REQUEST, "Slug tin đăng đã tồn tại"),
    LISTING_ALREADY_APPROVED(HttpStatus.BAD_REQUEST, "Tin đăng đã được duyệt"),
    LISTING_NOT_PENDING(HttpStatus.BAD_REQUEST, "Tin đăng không ở trạng thái chờ duyệt"),
    INVALID_LISTING_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái tin đăng không hợp lệ"),
    ACTIVE_LISTING_EXISTED(HttpStatus.BAD_REQUEST, "Phòng đã có tin đăng đang hoạt động"),

    // ===== TENANT PROFILE =====
    TENANT_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Hồ sơ nhu cầu thuê không tồn tại"),
    TENANT_PROFILE_EXISTED(HttpStatus.BAD_REQUEST, "Người dùng đã có hồ sơ nhu cầu thuê"),

    // ===== SAVED LISTING =====
    LISTING_ALREADY_SAVED(HttpStatus.BAD_REQUEST, "Tin đăng đã được lưu"),
    LISTING_NOT_SAVED(HttpStatus.BAD_REQUEST, "Tin đăng chưa được lưu"),

    // ===== RENTAL REQUEST =====
    RENTAL_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Yêu cầu thuê không tồn tại"),
    RENTAL_REQUEST_ALREADY_PENDING(HttpStatus.BAD_REQUEST, "Đã có yêu cầu thuê đang chờ xử lý"),
    RENTAL_REQUEST_NOT_PENDING(HttpStatus.BAD_REQUEST, "Yêu cầu thuê không ở trạng thái chờ"),
    RENTAL_REQUEST_EXPIRED(HttpStatus.BAD_REQUEST, "Yêu cầu thuê đã hết hạn"),
    CANNOT_REQUEST_OWN_LISTING(HttpStatus.BAD_REQUEST, "Không thể gửi yêu cầu thuê phòng của chính mình"),

    // ===== RENTAL AGREEMENT =====
    RENTAL_AGREEMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Hợp đồng thuê không tồn tại"),
    RENTAL_AGREEMENT_CODE_EXISTED(HttpStatus.BAD_REQUEST, "Mã hợp đồng đã tồn tại"),
    RENTAL_AGREEMENT_ALREADY_EXISTS(HttpStatus.BAD_REQUEST, "Yêu cầu thuê này đã tạo hợp đồng"),
    RENTAL_AGREEMENT_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái hợp đồng không hợp lệ"),
    RENTAL_AGREEMENT_DATE_INVALID(HttpStatus.BAD_REQUEST, "Ngày kết thúc phải sau ngày bắt đầu"),
    NOT_AGREEMENT_PARTY(HttpStatus.FORBIDDEN, "Bạn không phải bên liên quan của hợp đồng"),

    // ===== SERVICE PACKAGE / BOOST =====
    SERVICE_PACKAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Gói dịch vụ không tồn tại"),
    BOOST_PACKAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Gói đẩy tin không tồn tại"),
    SUBSCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Gói đăng ký không tồn tại"),
    SUBSCRIPTION_EXPIRED(HttpStatus.BAD_REQUEST, "Gói đăng ký đã hết hạn"),
    NO_ACTIVE_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "Bạn chưa có gói dịch vụ nào đang hoạt động"),
    QUOTA_EXCEEDED(HttpStatus.BAD_REQUEST, "Đã vượt quá số lượng tin cho phép của gói"),
    BOOST_LISTING_NOT_OWNED(HttpStatus.FORBIDDEN, "Bạn không sở hữu tin đăng này"),

    // ===== PAYMENT / INVOICE =====
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Giao dịch thanh toán không tồn tại"),
    PAYMENT_NOT_PENDING(HttpStatus.BAD_REQUEST, "Giao dịch thanh toán không ở trạng thái chờ"),
    PAYMENT_ALREADY_SUCCESS(HttpStatus.BAD_REQUEST, "Giao dịch thanh toán đã thành công"),
    PAYMENT_AMOUNT_NOT_ENOUGH(HttpStatus.BAD_REQUEST, "Số tiền thanh toán không đủ"),
    INVALID_PAYMENT_REFERENCE(HttpStatus.BAD_REQUEST, "Đối tượng thanh toán không hợp lệ"),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Hóa đơn không tồn tại"),
    INVOICE_NO_EXISTED(HttpStatus.BAD_REQUEST, "Số hóa đơn đã tồn tại"),

    // ===== REVIEW =====
    ROOM_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Đánh giá phòng không tồn tại"),
    ROOM_REVIEW_EXISTED(HttpStatus.BAD_REQUEST, "Hợp đồng này đã được đánh giá phòng"),
    LANDLORD_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Đánh giá chủ trọ không tồn tại"),
    LANDLORD_REVIEW_EXISTED(HttpStatus.BAD_REQUEST, "Hợp đồng này đã được đánh giá chủ trọ"),
    TENANT_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "Đánh giá người thuê không tồn tại"),
    TENANT_REVIEW_EXISTED(HttpStatus.BAD_REQUEST, "Hợp đồng này đã được đánh giá người thuê"),
    INVALID_RATING(HttpStatus.BAD_REQUEST, "Điểm đánh giá phải từ 1 đến 5"),
    CANNOT_REVIEW_SELF(HttpStatus.BAD_REQUEST, "Không thể tự đánh giá chính mình"),

    // ===== CHAT =====
    CHAT_CONVERSATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Cuộc trò chuyện không tồn tại"),
    CHAT_MESSAGE_NOT_FOUND(HttpStatus.NOT_FOUND, "Tin nhắn không tồn tại"),
    NOT_CONVERSATION_PARTY(HttpStatus.FORBIDDEN, "Bạn không thuộc cuộc trò chuyện này"),

    // ===== NOTIFICATION =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Thông báo không tồn tại"),

    // ===== REPORT =====
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Báo cáo không tồn tại"),
    REPORT_NOT_PENDING(HttpStatus.BAD_REQUEST, "Báo cáo không ở trạng thái chờ xử lý"),
    REPORT_EVIDENCE_NOT_FOUND(HttpStatus.NOT_FOUND, "Ảnh bằng chứng không tồn tại"),
    CANNOT_REPORT_SELF(HttpStatus.BAD_REQUEST, "Không thể báo cáo chính mình"),

    // ===== FILE / CLOUDINARY =====
    FILE_UPLOAD_FAILED(HttpStatus.BAD_REQUEST, "Không thể tải lên file"),
    FILE_EMPTY(HttpStatus.BAD_REQUEST, "File không được để trống"),
    INVALID_FILE_TYPE(HttpStatus.BAD_REQUEST, "Loại file không được hỗ trợ"),

    // ===== COMMON =====
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài nguyên"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Phương thức không được phép"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống");

    private final HttpStatus code; // Ma HTTP Status
    private final String message; // Loi nhan hien thi cho user

    ErrorCode(HttpStatus code, String message) {
        this.code = code;
        this.message = message;
    }

    // Ho tro lay ma so nguyen (VD: 400, 404)
    // Dung cho truong hop Frontend can 1 truong "statusCode" la so trong JSON tra ve
    public int getStatusCodeValue() {
        return this.code.value();
    }
}
