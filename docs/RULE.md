# Quy tắc phát triển backend

## 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

Before implementing:
- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them - don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

## 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

## 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

When editing existing code:
- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it - don't delete it.

When your changes create orphans:
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

The test: Every changed line should trace directly to the user's request.

## 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan and the checks that establish completion.

## Kiến trúc controller/service

Theo mẫu module `room`, controller chỉ inject interface trong `service`; implementation đặt trong `service.impl`, có `@Service`, `implements` và `@Override`. Transaction và nghiệp vụ nằm ở implementation, controller tiếp nhận request, lấy danh tính từ authentication và tạo HTTP response.

Đã áp dụng cho `NotificationService`, `RentalService`, `ListingContactService`, `ListingReviewService`, `ReportEvidenceService`, `BoostAllocationService`. Các service đã theo mẫu ở room, auth, chat, property, payment, identity và các danh mục gói dịch vụ tiếp tục giữ cấu trúc đó.

`RentalContractService` tách phần kiểm tra quyền/trạng thái PDF, tải file và xác minh SHA-256 khỏi controller. Tải file diễn ra ngoài transaction; transaction đọc quyền/trạng thái kết thúc trước khi gọi storage. HTTP header, MIME type và lựa chọn inline/download vẫn thuộc controller.

Các worker/scheduler, bộ tạo PDF và adapter hạ tầng không phải entry point nghiệp vụ của controller. Không cần tạo interface cho từng helper chỉ để tăng số lớp.

## Database và ranh giới domain

Không thêm entity chỉ để số entity bằng số bảng. `rental_events`, `rental_pdf_assets`, `notification_outbox` dùng JdbcTemplate và tham gia transaction; lần lượt phục vụ audit, reconciliation PDF và giao thông báo. `notifications` là inbox JPA riêng, `flyway_schema_history` do Flyway quản lý. Giữ trách nhiệm và lịch sử của từng bảng.

Property giữ chủ sở hữu/địa chỉ; Room là đơn vị thuê, Listing thuộc Property. WHOLE có một Room đại diện, không thêm stock/quantity. Không thêm BaseEntity, soft delete hoặc cascade ALL khi chưa có yêu cầu. Tái sử dụng DTO/mapper, ApiResponse/PageResponse, JWT, exception handler và hạ tầng hiện có; controller không trả JPA entity.

Room phải khóa trước request/agreement; không gọi storage, render PDF hoặc giữ transaction khi chờ mạng dưới Room lock. Giữ unique guard chống thuê trùng và test concurrency. Không sửa migration đã chạy, không tự chuyển đổi dữ liệu tài chính chưa đối soát.

`ControllerServiceArchitectureTest` kiểm tra dependency controller/interface; `SchemaEntityCoverageTest` đối chiếu migration/entity và bảng JDBC. Luồng thông báo, thuê phòng và review/report có các integration suite riêng. Xem [nghiệp vụ](BUSINESS.md), [API](api.md), [vận hành và kiểm thử](OPERATIONS.md).
