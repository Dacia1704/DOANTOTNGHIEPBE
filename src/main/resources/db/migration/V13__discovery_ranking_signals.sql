ALTER TABLE community_reactions
 ADD COLUMN created_at datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6);

CREATE TABLE feed_impressions (
 user_id varchar(36) NOT NULL,
 surface varchar(16) NOT NULL,
 item_id varchar(36) NOT NULL,
 bucket_date date NOT NULL,
 seen_at datetime(6) NOT NULL,
 impression_count bigint NOT NULL DEFAULT 1,
 PRIMARY KEY(user_id,surface,item_id,bucket_date),
 INDEX ix_feed_impressions_item(surface,item_id),
 FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE CASCADE,
 CHECK(surface IN ('LISTING','COMMUNITY'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
