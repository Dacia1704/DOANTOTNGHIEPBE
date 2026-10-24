-- =============================================================================
-- V3 - MIGRATE CHAT MODULE TO PARTICIPANT-BASED DESIGN
-- =============================================================================

-- ---------------------------------------------------------------------
-- 1. CHAT_CONVERSATIONS
--
-- V1 đã có:
-- tenant_id
-- landlord_id
-- listing_id
-- rental_request_id
-- last_message_at
-- created_at
-- updated_at
--
-- Thiết kế mới không lưu tenant/landlord trực tiếp trong conversation.
-- Thành viên được chuyển sang chat_conversation_participants.
-- ---------------------------------------------------------------------

ALTER TABLE chat_conversations
DROP FOREIGN KEY fk_cc_tenant,
    DROP FOREIGN KEY fk_cc_landlord;

ALTER TABLE chat_conversations
DROP COLUMN tenant_id,
    DROP COLUMN landlord_id;


-- ---------------------------------------------------------------------
-- 2. CHAT_CONVERSATION_PARTICIPANTS
-- Bảng mới hoàn toàn nên CREATE TABLE.
-- ---------------------------------------------------------------------

CREATE TABLE chat_conversation_participants (
                                                id VARCHAR(36) PRIMARY KEY DEFAULT (UUID()),

                                                conversation_id VARCHAR(36) NOT NULL,
                                                user_id VARCHAR(36) NOT NULL,

                                                joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                last_read_at DATETIME NULL,

                                                CONSTRAINT uk_ccp_conversation_user
                                                    UNIQUE (conversation_id, user_id),

                                                CONSTRAINT fk_ccp_conversation
                                                    FOREIGN KEY (conversation_id)
                                                        REFERENCES chat_conversations(id)
                                                        ON DELETE CASCADE,

                                                CONSTRAINT fk_ccp_user
                                                    FOREIGN KEY (user_id)
                                                        REFERENCES users(id)
                                                        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX ix_ccp_user_id
    ON chat_conversation_participants(user_id);

CREATE INDEX ix_ccp_conversation_id
    ON chat_conversation_participants(conversation_id);


-- ---------------------------------------------------------------------
-- 3. CHAT_MESSAGES
--
-- V1 đã có chat_messages.
-- Thiết kế mới bỏ is_read vì trạng thái đọc được quản lý bằng last_read_at.
-- ---------------------------------------------------------------------

ALTER TABLE chat_messages
DROP COLUMN is_read;

CREATE INDEX ix_cm_conversation_created
    ON chat_messages(conversation_id, created_at);

CREATE INDEX ix_cm_sender_id
    ON chat_messages(sender_id);