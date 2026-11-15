ALTER TABLE community_comments
  ADD COLUMN reply_to_id varchar(36) NULL,
  ADD INDEX ix_community_reply_target (reply_to_id),
  ADD CONSTRAINT fk_community_reply_target FOREIGN KEY (reply_to_id)
    REFERENCES community_comments(id) ON DELETE SET NULL;
