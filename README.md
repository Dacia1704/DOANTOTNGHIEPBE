# Rental Management backend

Java 21 / Spring Boot / MySQL 8. Run infrastructure with the existing Docker configuration, then start the backend with `./mvnw.cmd spring-boot:run`. Flyway applies versioned migrations; Hibernate uses `ddl-auto=validate`.

- [Đặt phòng và hợp đồng PDF: flow, API, cấu hình, migration, kiểm thử](docs/RENTAL_BOOKING.md)
- [Property/Room API](docs/PROPERTY_API.md)
- [Dữ liệu demo V2](docs/SEED_V15.md)

Frontend Angular lives in `../DOANTOTNGHIEPFE`. Do not use the listing/boost payment module for room booking.
