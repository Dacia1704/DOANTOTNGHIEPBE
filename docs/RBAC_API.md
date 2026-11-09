# Quản trị phân quyền

Bản đầu giữ ADMIN, LANDLORD, TENANT. Ma trận quản lý 8 quyền đã được backend thực thi: USER_READ, USER_MANAGE, REVIEW_READ, REVIEW_MODERATE, REPORT_READ, REPORT_HANDLE, ROLE_PERMISSION_READ, ROLE_PERMISSION_MANAGE.

Các quyền cũ ngoài danh sách này chưa đưa vào ma trận, tránh hiển thị checkbox chưa có tác dụng trên endpoint. Các module nghiệp vụ khác vẫn áp dụng phân quyền hiện tại.

## Đọc ma trận

GET /api/admin/role-permissions, cần ADMIN và ROLE_PERMISSION_READ.

ApiResponse.data có:

- permissions: các dòng {id,code,name,module}.
- roles: các cột {id,code,name,version,permissionIds,lockedPermissionIds}.

FE dựng checkbox từ permissionIds; disable các ô thuộc lockedPermissionIds. Số quyền mỗi cột là permissionIds.length. Tìm kiếm theo code/name có thể làm ngay trên dữ liệu này.

Hai quyền ROLE_PERMISSION_* khóa ở mọi vai trò: chỉ ADMIN có sẵn theo migration. USER_MANAGE chỉ chỉnh được trên ADMIN để không cấp đường tạo/sửa tài khoản quản trị cho người dùng nghiệp vụ.

## Bật/tắt

PATCH /api/admin/roles/{roleId}/permissions/{permissionId}, cần ADMIN và ROLE_PERMISSION_MANAGE.

Body: {"enabled":true,"expectedVersion":0}. expectedVersion lấy từ cột role của lần đọc gần nhất.

Response data: {roleId,permissionId,enabled,version}. Lưu version mới sau mỗi lần cập nhật và tuần tự hóa thao tác cùng một role; khi 409 PERMISSION_VERSION_CONFLICT thì tải lại ma trận. API khóa role trong transaction, đối chiếu phiên bản mới nhất, cập nhật quan hệ và ghi audit atomically. Gửi trạng thái hiện có không tạo audit thừa.

409 PERMISSION_PROTECTED nếu chỉnh ô bị khóa hoặc permission chưa được quản lý. Sai quyền 403, ID không tồn tại 404. Không có API tạo role hoặc permission tùy ý.

## Quyền có tác dụng ở đâu

- USER_READ: danh sách/chi tiết người dùng quản trị.
- USER_MANAGE: tạo/sửa/xóa tài khoản quản trị, vẫn yêu cầu vai trò ADMIN và giữ các bảo vệ tài khoản ADMIN hiện có.
- REVIEW_READ: danh sách quản trị review.
- REVIEW_MODERATE: ẩn/hiện review.
- REPORT_READ: danh sách, thống kê, chi tiết report và đọc bằng chứng của hồ sơ khác.
- REPORT_HANDLE: nhận và đóng report, vẫn kiểm tra đúng người được giao xử lý và trạng thái. Muốn xem hồ sơ xử lý cần cấp thêm REPORT_READ.

Các kiểm tra sở hữu, hợp đồng, tác giả review và tài khoản ACTIVE tiếp tục áp dụng. API đọc review người thuê của landlord và API báo cáo của chính tenant vẫn theo nghiệp vụ hiện có.

## Đồng bộ FE và audit

GET /api/users/me/authorities: tài khoản đăng nhập nhận danh sách authority hiện tại từ database, gồm ROLE_* và permission code. JWT filter vốn đã nạp quyền từ database mỗi request, nên grant/revoke không chờ JWT hết hạn. FE nên đọc lại danh sách khi tải trang/quay lại ứng dụng hoặc sau thao tác phân quyền; không dùng scope cũ trong token làm nguồn duy nhất để dựng menu.

GET /api/admin/role-permissions/audit?page=0&size=20: ADMIN có ROLE_PERMISSION_READ. Trả ApiResponse.data là mảng tối đa size bản ghi, mới nhất trước, gồm actor_id/actor_name, role_id/role_code, permission_id/permission_code, old_enabled, new_enabled, role_version, created_at. page từ 0, size 1–50. Chưa có API sửa/xóa audit.

Migration V7 bổ sung roles.permission_version, bảng permission_audit và seed các quyền còn thiếu/cấp mặc định cho ADMIN. Không sửa migration đã áp dụng. Audit giữ ID gốc khi đối tượng bị xóa; tên hiển thị nối từ dữ liệu hiện tại, có thể null khi đối tượng không còn.

Frontend ma trận và xác thực hai lớp chưa nằm trong thay đổi backend này.

Kiểm chứng: 33 ca ReviewReportWorkflowTest qua trên MySQL riêng với migration V1–V7 và cấu hình Security thực. Bao gồm cấp/thu hồi có hiệu lực bằng JWT cũ, khóa quyền hệ thống, từ chối phiên bản cũ, hai cập nhật đồng thời chỉ một thành công và lỗi ghi audit rollback cả quyền lẫn phiên bản.
