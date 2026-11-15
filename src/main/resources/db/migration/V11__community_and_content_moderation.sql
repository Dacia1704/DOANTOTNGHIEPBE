CREATE TABLE community_posts (
 id varchar(36) PRIMARY KEY, author_id varchar(36) NOT NULL, content text NOT NULL,
 area varchar(150) NOT NULL DEFAULT '', status varchar(24) NOT NULL DEFAULT 'VISIBLE',
 version bigint NOT NULL DEFAULT 1, pinned boolean NOT NULL DEFAULT false,
 created_at datetime(6) NOT NULL, edited_at datetime(6) NULL, deleted_at datetime(6) NULL,
 FOREIGN KEY (author_id) REFERENCES users(id),
 INDEX ix_community_feed (status,created_at,id), INDEX ix_community_author(author_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_media (
 id varchar(36) PRIMARY KEY, owner_id varchar(36) NOT NULL, post_id varchar(36) NULL,
 url varchar(1000) NOT NULL, public_id varchar(255) NOT NULL, created_at datetime(6) NOT NULL,
 FOREIGN KEY(owner_id) REFERENCES users(id), FOREIGN KEY(post_id) REFERENCES community_posts(id), INDEX(post_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_post_listings (
 post_id varchar(36) NOT NULL, listing_id varchar(36) NOT NULL,
 PRIMARY KEY(post_id,listing_id), FOREIGN KEY(post_id) REFERENCES community_posts(id), FOREIGN KEY(listing_id) REFERENCES listings(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_comments (
 id varchar(36) PRIMARY KEY, post_id varchar(36) NOT NULL, author_id varchar(36) NOT NULL,
 parent_id varchar(36) NULL, content text NOT NULL, status varchar(24) NOT NULL DEFAULT 'VISIBLE',
 version bigint NOT NULL DEFAULT 1, created_at datetime(6) NOT NULL, edited_at datetime(6) NULL, deleted_at datetime(6) NULL,
 FOREIGN KEY(post_id) REFERENCES community_posts(id), FOREIGN KEY(author_id) REFERENCES users(id), FOREIGN KEY(parent_id) REFERENCES community_comments(id),
 INDEX ix_community_comments(post_id,parent_id,created_at,id), INDEX(author_id,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_reactions (
 target_type varchar(24) NOT NULL, target_id varchar(36) NOT NULL, user_id varchar(36) NOT NULL,
 type varchar(12) NOT NULL, PRIMARY KEY(target_type,target_id,user_id), FOREIGN KEY(user_id) REFERENCES users(id),
 CHECK(target_type IN ('POST','POST_COMMENT')), CHECK(type IN ('LIKE','LOVE','WOW'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_saved_posts (
 user_id varchar(36) NOT NULL, post_id varchar(36) NOT NULL, created_at datetime(6) NOT NULL,
 PRIMARY KEY(user_id,post_id), FOREIGN KEY(user_id) REFERENCES users(id), FOREIGN KEY(post_id) REFERENCES community_posts(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE community_follows (
 follower_id varchar(36) NOT NULL, followee_id varchar(36) NOT NULL,
 PRIMARY KEY(follower_id,followee_id), FOREIGN KEY(follower_id) REFERENCES users(id), FOREIGN KEY(followee_id) REFERENCES users(id), CHECK(follower_id<>followee_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE moderation_rules (
 id varchar(36) PRIMARY KEY, pattern varchar(500) NOT NULL, match_type varchar(24) NOT NULL,
 action varchar(24) NOT NULL, accent_insensitive boolean NOT NULL DEFAULT false,
 apply_posts boolean NOT NULL DEFAULT true, apply_comments boolean NOT NULL DEFAULT true,
 apply_reviews boolean NOT NULL DEFAULT true, apply_replies boolean NOT NULL DEFAULT true,
 enabled boolean NOT NULL DEFAULT true, reason varchar(255) NOT NULL, version bigint NOT NULL DEFAULT 1,
 CHECK(match_type IN ('TOKEN','PHRASE','DOMAIN')), CHECK(action IN ('RESTRICT','REQUIRE_REVIEW','HIDE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE content_moderation_cases (
 id varchar(36) PRIMARY KEY, target_type varchar(32) NOT NULL, target_id varchar(36) NOT NULL,
 author_id varchar(36) NOT NULL, content_version bigint NOT NULL, snapshot text NOT NULL,
 reason varchar(1000) NOT NULL, status varchar(24) NOT NULL, source varchar(24) NOT NULL,
 resolved boolean NOT NULL DEFAULT false, handled_by varchar(36) NULL, resolution_note varchar(1000) NULL,
 created_at datetime(6) NOT NULL, FOREIGN KEY(author_id) REFERENCES users(id), FOREIGN KEY(handled_by) REFERENCES users(id),
 INDEX(target_type,target_id), INDEX(resolved,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE content_reports (
 id varchar(36) PRIMARY KEY, target_type varchar(32) NOT NULL, target_id varchar(36) NOT NULL,
 content_version bigint NOT NULL, reporter_id varchar(36) NOT NULL, reason varchar(1000) NOT NULL, created_at datetime(6) NOT NULL,
 UNIQUE(target_type,target_id,content_version,reporter_id), FOREIGN KEY(reporter_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE moderation_actions (
 id varchar(36) PRIMARY KEY, actor_id varchar(36) NOT NULL, target_id varchar(36) NOT NULL,
 action varchar(50) NOT NULL, reason varchar(1000) NOT NULL, created_at datetime(6) NOT NULL, FOREIGN KEY(actor_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
ALTER TABLE room_reviews ADD text_status varchar(24) NOT NULL DEFAULT 'VISIBLE', ADD reply_status varchar(24) NOT NULL DEFAULT 'VISIBLE';
ALTER TABLE landlord_reviews ADD text_status varchar(24) NOT NULL DEFAULT 'VISIBLE', ADD reply_status varchar(24) NOT NULL DEFAULT 'VISIBLE';
INSERT INTO moderation_rules(id,pattern,match_type,action,accent_insensitive,reason) VALUES
 (uuid(),'vcl','TOKEN','RESTRICT',false,'Nội dung có cách diễn đạt không phù hợp.'),
 (uuid(),'đm','TOKEN','RESTRICT',false,'Nội dung có cách diễn đạt không phù hợp.'),
 (uuid(),'địt mẹ','PHRASE','HIDE',true,'Nội dung có ngôn từ xúc phạm.'),
 (uuid(),'đụ má','PHRASE','HIDE',true,'Nội dung có ngôn từ xúc phạm.'),
 (uuid(),'tao sẽ giết mày','PHRASE','HIDE',true,'Nội dung có dấu hiệu đe dọa.'),
 (uuid(),'việc nhẹ lương cao','PHRASE','REQUIRE_REVIEW',true,'Nội dung cần được xem xét về quảng cáo.'),
 (uuid(),'xac-minh-tien-coc.invalid','DOMAIN','HIDE',false,'Liên kết nằm trong danh sách bị chặn.');
INSERT INTO permissions(id,code,name) SELECT uuid(),'MODERATION_READ','Xem kiểm duyệt cộng đồng' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='MODERATION_READ');
INSERT INTO permissions(id,code,name) SELECT uuid(),'MODERATION_HANDLE','Xử lý kiểm duyệt cộng đồng' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='MODERATION_HANDLE');
INSERT INTO permissions(id,code,name) SELECT uuid(),'MODERATION_RULE_MANAGE','Quản lý từ khóa kiểm duyệt' WHERE NOT EXISTS(SELECT 1 FROM permissions WHERE code='MODERATION_RULE_MANAGE');
INSERT INTO role_permissions(id,role_id,permission_id) SELECT uuid(),r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN' AND p.code IN ('MODERATION_READ','MODERATION_HANDLE','MODERATION_RULE_MANAGE') AND NOT EXISTS(SELECT 1 FROM role_permissions rp WHERE rp.role_id=r.id AND rp.permission_id=p.id);
