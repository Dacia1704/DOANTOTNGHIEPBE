# RentalManagement — backend

Java/Spring Boot backend cho hệ thống cho thuê trọ. Khởi chạy cả FE/BE/AI, cấu hình môi trường và seed: [hướng dẫn hệ thống](../AIService/README.md).

| Tài liệu | Nội dung |
| --- | --- |
| [API chung BE/FE](docs/api.md) | Request/response, phân quyền, Property, Listing, gói/Boost, thuê/PDF, review/report, chat và thông báo |
| [Nghiệp vụ và dữ liệu](docs/BUSINESS.md) | Quan hệ domain, lượt đăng, snapshot gói, transaction/khóa, worker PDF |
| [Vận hành và kiểm thử](docs/OPERATIONS.md) | Migration hiện tại, VNPay/IPN, Redis/RabbitMQ và integration tests |
| [Quy tắc phát triển](docs/RULE.md) | Controller/service, database và phạm vi thay đổi |
| [Giao diện FE](../DOANTOTNGHIEPFE/docs/UI.md) | Màn hình, bản đồ, contract PDF và smoke test |

API chỉ duy trì tại docs/api.md của backend; frontend liên kết tới đây. Cập nhật nội dung vào tài liệu theo chủ đề, không tạo thêm báo cáo task/kế hoạch đã triển khai trong src.
