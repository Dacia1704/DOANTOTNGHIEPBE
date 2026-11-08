-- Keep notification intent in the business transaction; RabbitMQ delivery is retried independently.
CREATE TABLE notification_outbox (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    type VARCHAR(30) NOT NULL,
    title VARCHAR(255) NOT NULL,
    body TEXT NOT NULL,
    reference_type VARCHAR(50) NULL,
    reference_id VARCHAR(36) NULL,
    created_at DATETIME NOT NULL,
    published_at DATETIME NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME NOT NULL,
    INDEX idx_notification_outbox_pending (published_at, next_attempt_at, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

UPDATE notifications SET is_read = false WHERE is_read IS NULL;
CREATE INDEX idx_notification_user_read_created ON notifications(user_id, is_read, created_at, id);
