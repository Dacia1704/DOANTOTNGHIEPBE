-- V14: FULL SCHEMA FOR A NEW / EMPTY MYSQL 8.0.16+ DATABASE.
-- Consolidates the final state of V12 + V13 and all current JPA entities.
-- Standalone build: select/create your empty database before executing this file.
-- Do not place this file in classpath:db/migration or run it after V1-V13.
-- Do not execute V13 again: listings already references properties here.
-- No DROP/TRUNCATE, no application/sample users, rooms, listings or payment data.
-- All 44 tables are created first; all 85 foreign keys are added afterwards.
-- Changes from V12 + V13: UNIQUE listings.slug; remove four unused legacy_* columns.
-- Retain generated uniqueness guards, business checks, delete rules and query indexes.
-- SQL-only guards need not be mapped as writable JPA fields.
-- See docs/FULL_SCHEMA_V14.md for verification and Flyway startup instructions.

-- amenities
CREATE TABLE `amenities` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `name` varchar(100) NOT NULL,
  `icon` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- boost_allocations
CREATE TABLE `boost_allocations` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `boost_subscription_id` varchar(36) NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `priority_level` int NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_boost_allocation_listing` (`listing_id`),
  KEY `ix_boost_allocation_subscription_level` (`boost_subscription_id`,`priority_level`),
  CONSTRAINT `chk_boost_allocation_level` CHECK ((`priority_level` in (1,2)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- boost_service_package_subscriptions
CREATE TABLE `boost_service_package_subscriptions` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `landlord_id` varchar(36) NOT NULL,
  `boost_package_id` varchar(36) NOT NULL,
  `start_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `status` enum('PENDING_PAYMENT','ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING_PAYMENT',
  `price` decimal(12,2) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `package_name_snapshot` varchar(100) NOT NULL,
  `duration_months_snapshot` int NOT NULL,
  `payment_expires_at` datetime NOT NULL DEFAULT ((now() + interval 15 minute)),
  `high_priority_slots_snapshot` int NOT NULL DEFAULT '1',
  `low_priority_slots_snapshot` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `fk_bsps_package` (`boost_package_id`),
  KEY `ix_bsps_landlord_created` (`landlord_id`,`created_at`),
  KEY `ix_bsps_status_end` (`status`,`end_at`),
  KEY `ix_boost_payment_expiry` (`status`,`payment_expires_at`),
  CONSTRAINT `chk_bsps_active_dates` CHECK (((`status` not in (_utf8mb4'ACTIVE',_utf8mb4'EXPIRED')) or ((`start_at` is not null) and (`end_at` is not null)))),
  CONSTRAINT `chk_bsps_dates` CHECK ((((`start_at` is null) and (`end_at` is null)) or ((`start_at` is not null) and (`end_at` is not null) and (`end_at` > `start_at`)))),
  CONSTRAINT `chk_bsps_pending_dates` CHECK (((`status` <> _utf8mb4'PENDING_PAYMENT') or ((`start_at` is null) and (`end_at` is null)))),
  CONSTRAINT `chk_bsps_slots` CHECK (((`high_priority_slots_snapshot` >= 0) and (`low_priority_slots_snapshot` >= 0) and ((`high_priority_slots_snapshot` > 0) or (`low_priority_slots_snapshot` > 0)) and (`duration_months_snapshot` in (1,3,6)) and (`price` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- boost_service_packages
CREATE TABLE `boost_service_packages` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `duration_months` int DEFAULT NULL,
  `high_priority_slots` int NOT NULL DEFAULT '1',
  `low_priority_slots` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  CONSTRAINT `chk_bsp_active_term` CHECK (((`is_active` = false) or ((`duration_months` is not null) and (`duration_months` in (1,3,6))))),
  CONSTRAINT `chk_bsp_months` CHECK (((`duration_months` is null) or (`duration_months` in (1,3,6)))),
  CONSTRAINT `chk_bsp_price` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_bsp_slots` CHECK (((`high_priority_slots` >= 0) and (`low_priority_slots` >= 0) and ((`high_priority_slots` > 0) or (`low_priority_slots` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- chat_conversation_participants
CREATE TABLE `chat_conversation_participants` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `conversation_id` varchar(36) NOT NULL,
  `user_id` varchar(36) NOT NULL,
  `joined_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `last_read_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_ccp_conversation_user` (`conversation_id`,`user_id`),
  KEY `ix_ccp_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- chat_conversations
CREATE TABLE `chat_conversations` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `listing_id` varchar(36) DEFAULT NULL,
  `rental_request_id` varchar(36) DEFAULT NULL,
  `last_message_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_cc_listing` (`listing_id`),
  KEY `fk_cc_request` (`rental_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- chat_messages
CREATE TABLE `chat_messages` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `conversation_id` varchar(36) NOT NULL,
  `sender_id` varchar(36) NOT NULL,
  `content` text NOT NULL,
  `message_type` enum('TEXT','IMAGE','SYSTEM') NOT NULL DEFAULT 'TEXT',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `ix_cm_conversation_created` (`conversation_id`,`created_at`),
  KEY `ix_cm_sender_id` (`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- invoices
CREATE TABLE `invoices` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `payment_id` varchar(36) NOT NULL,
  `type` enum('SERVICE_PACKAGE','BOOST_PACKAGE') NOT NULL,
  `service_package_id` varchar(36) DEFAULT NULL,
  `boost_service_package_id` varchar(36) DEFAULT NULL,
  `invoice_no` varchar(100) NOT NULL,
  `tax_amount` decimal(12,2) DEFAULT '0.00',
  `total_amount` decimal(12,2) NOT NULL,
  `buyer_name` varchar(255) DEFAULT NULL,
  `buyer_tax_code` varchar(50) DEFAULT NULL,
  `issued_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `invoice_no` (`invoice_no`),
  UNIQUE KEY `uq_invoice_payment` (`payment_id`),
  KEY `fk_invoices_sp` (`service_package_id`),
  KEY `fk_invoices_bsp` (`boost_service_package_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- landlord_reviews
CREATE TABLE `landlord_reviews` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `rental_agreement_id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `landlord_id` varchar(36) NOT NULL,
  `rating` int NOT NULL,
  `comment` text,
  `landlord_reply` text,
  `is_hidden` tinyint(1) DEFAULT '0',
  `hidden_by` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `rental_agreement_id` (`rental_agreement_id`),
  KEY `fk_lrev_tenant` (`tenant_id`),
  KEY `fk_lrev_landlord` (`landlord_id`),
  KEY `fk_lrev_hidden_by` (`hidden_by`),
  CONSTRAINT `landlord_reviews_chk_1` CHECK (((`rating` >= 1) and (`rating` <= 5)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- listing_contact_events
CREATE TABLE `listing_contact_events` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `listing_id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_lce_listing` (`listing_id`),
  KEY `fk_lce_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- listing_daily_stats
CREATE TABLE `listing_daily_stats` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `listing_id` varchar(36) NOT NULL,
  `stat_date` date NOT NULL,
  `view_count` int DEFAULT '0',
  `contact_count` int DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_listing_daily_stats` (`listing_id`,`stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- listing_view_history
CREATE TABLE `listing_view_history` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `tenant_id` varchar(36) NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_lvh_tenant` (`tenant_id`),
  KEY `fk_lvh_listing` (`listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- listings
CREATE TABLE `listings` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `title` varchar(255) NOT NULL,
  `slug` varchar(255) DEFAULT NULL,
  `description` text,
  `status` enum('PENDING','PUBLISHED','REJECTED','HIDDEN','EXPIRED') NOT NULL DEFAULT 'PENDING',
  `rejection_reason` text,
  `approved_by` varchar(36) DEFAULT NULL,
  `approved_at` datetime DEFAULT NULL,
  `priority_level` int DEFAULT '0',
  `view_count` int DEFAULT '0',
  `contact_count` int DEFAULT '0',
  `expires_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `submitted_at` datetime DEFAULT NULL,
  `pending_expires_at` datetime DEFAULT NULL,
  `published_at` datetime DEFAULT NULL,
  `service_subscription_period_id` varchar(36) DEFAULT NULL,
  `package_priority_level` int NOT NULL DEFAULT '0',
  `property_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `fk_listings_approved_by` (`approved_by`),
  KEY `ix_listing_status_published` (`status`,`published_at`),
  KEY `ix_listing_period_priority` (`service_subscription_period_id`,`package_priority_level`,`status`,`expires_at`),
  KEY `ix_listing_property_status_pending` (`property_id`,`status`,`pending_expires_at`),
  KEY `ix_listing_property_status_expiry` (`property_id`,`status`,`expires_at`),
  CONSTRAINT `chk_listing_package_priority` CHECK ((((`package_priority_level` = 0) and (`service_subscription_period_id` is null)) or ((`package_priority_level` in (1,2)) and (`service_subscription_period_id` is not null)))),
  CONSTRAINT `chk_listing_pending` CHECK (((`status` <> _utf8mb4'PENDING') or ((`submitted_at` is not null) and (`pending_expires_at` is not null) and (`pending_expires_at` > `submitted_at`)))),
  CONSTRAINT `chk_listing_published` CHECK (((`status` <> _utf8mb4'PUBLISHED') or ((`published_at` is not null) and (`expires_at` is not null) and (`expires_at` > `published_at`)))),
  UNIQUE KEY `uk_listings_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- notifications
CREATE TABLE `notifications` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `user_id` varchar(36) NOT NULL,
  `type` enum('SYSTEM','RENTAL_REQUEST','RENTAL_AGREEMENT','REPORT','CHAT') NOT NULL,
  `title` varchar(255) NOT NULL,
  `body` text NOT NULL,
  `reference_type` varchar(50) DEFAULT NULL,
  `reference_id` varchar(36) DEFAULT NULL,
  `is_read` tinyint(1) DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_notif_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- payments
CREATE TABLE `payments` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `landlord_id` varchar(36) NOT NULL,
  `type` enum('SERVICE_PACKAGE','BOOST_PACKAGE') NOT NULL,
  `amount` decimal(12,2) NOT NULL,
  `payment_method` enum('VNPAY','MOMO','BANK_TRANSFER') NOT NULL,
  `status` enum('PENDING','SUCCESS','FAILED','EXPIRED','REFUNDED') NOT NULL DEFAULT 'PENDING',
  `provider_txn_id` varchar(255) DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `service_subscription_order_id` varchar(36) DEFAULT NULL,
  `boost_subscription_id` varchar(36) DEFAULT NULL,
  `merchant_order_id` varchar(100) NOT NULL,
  `expires_at` datetime NOT NULL,
  `requires_review` tinyint(1) NOT NULL DEFAULT '0',
  `review_reason` varchar(255) DEFAULT NULL,
  `vnp_create_date` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_payment_merchant` (`merchant_order_id`),
  UNIQUE KEY `uq_payment_provider` (`payment_method`,`provider_txn_id`),
  KEY `fk_payment_order` (`service_subscription_order_id`),
  KEY `fk_payment_boost` (`boost_subscription_id`),
  KEY `ix_payment_landlord_created` (`landlord_id`,`created_at`),
  KEY `ix_payment_status_expiry` (`status`,`expires_at`),
  KEY `ix_payment_review_created` (`requires_review`,`created_at`),
  CONSTRAINT `chk_payment_amount` CHECK ((`amount` >= 0)),
  CONSTRAINT `chk_payment_paid` CHECK (((`status` <> _utf8mb4'SUCCESS') or (`paid_at` is not null))),
  CONSTRAINT `chk_payment_target` CHECK ((((`type` = _utf8mb4'SERVICE_PACKAGE') and (`service_subscription_order_id` is not null) and (`boost_subscription_id` is null)) or ((`type` = _utf8mb4'BOOST_PACKAGE') and (`boost_subscription_id` is not null) and (`service_subscription_order_id` is null))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- permissions
CREATE TABLE `permissions` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `code` varchar(100) NOT NULL,
  `name` varchar(255) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- properties
CREATE TABLE `properties` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `landlord_id` varchar(36) NOT NULL,
  `property_type_id` varchar(36) NOT NULL,
  `name` varchar(100) NOT NULL,
  `rental_mode` varchar(20) NOT NULL,
  `total_floors` int DEFAULT NULL,
  `province_id` varchar(36) NOT NULL,
  `ward_id` varchar(36) NOT NULL,
  `address_line` varchar(255) NOT NULL,
  `latitude` decimal(10,8) DEFAULT NULL,
  `longitude` decimal(11,8) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_properties_landlord` (`landlord_id`),
  KEY `fk_properties_type` (`property_type_id`),
  KEY `fk_properties_province` (`province_id`),
  KEY `fk_properties_ward` (`ward_id`),
  CONSTRAINT `ck_properties_floors` CHECK (((`total_floors` is null) or (`total_floors` >= 0))),
  CONSTRAINT `ck_properties_mode` CHECK ((`rental_mode` in (_utf8mb4'WHOLE',_utf8mb4'BY_ROOM')))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- property_amenities
CREATE TABLE `property_amenities` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `property_id` varchar(36) NOT NULL,
  `amenity_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_property_amenity` (`property_id`,`amenity_id`),
  KEY `fk_pa_amenity` (`amenity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- property_images
CREATE TABLE `property_images` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `property_id` varchar(36) NOT NULL,
  `url` varchar(255) NOT NULL,
  `public_id` varchar(255) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_pi_property` (`property_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- property_types
CREATE TABLE `property_types` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `name` varchar(100) NOT NULL,
  `description` text,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- provinces
CREATE TABLE `provinces` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- refresh_tokens
CREATE TABLE `refresh_tokens` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `user_id` varchar(36) NOT NULL,
  `token` varchar(64) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL,
  `expires_at` datetime NOT NULL,
  `revoked` tinyint(1) NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `IX_refresh_tokens_token` (`token`),
  KEY `IX_refresh_tokens_user_id` (`user_id`),
  KEY `IX_refresh_tokens_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- rental_agreements
CREATE TABLE `rental_agreements` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `agreement_code` varchar(50) NOT NULL,
  `rental_request_id` varchar(36) DEFAULT NULL,
  `room_id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `landlord_id` varchar(36) NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `deposit` decimal(12,2) DEFAULT '0.00',
  `electricity_price` decimal(10,2) DEFAULT NULL,
  `water_price` decimal(10,2) DEFAULT NULL,
  `service_fee` decimal(10,2) DEFAULT NULL,
  `payment_cycle_months` int DEFAULT '1',
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `terms_text` text,
  `pdf_url` varchar(255) DEFAULT NULL,
  `pdf_public_id` varchar(255) DEFAULT NULL,
  `status` enum('DRAFT','PENDING_CONFIRMATION','ACTIVE','EXPIRED','TERMINATED','CANCELLED') NOT NULL DEFAULT 'DRAFT',
  `created_by_role` enum('LANDLORD','TENANT') NOT NULL,
  `landlord_confirmed_at` datetime DEFAULT NULL,
  `tenant_confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `agreement_code` (`agreement_code`),
  KEY `fk_rag_request` (`rental_request_id`),
  KEY `fk_rag_room` (`room_id`),
  KEY `fk_rag_tenant` (`tenant_id`),
  KEY `fk_rag_landlord` (`landlord_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- rental_requests
CREATE TABLE `rental_requests` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `listing_id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `landlord_id` varchar(36) NOT NULL,
  `message` text,
  `proposed_move_in_date` date DEFAULT NULL,
  `status` enum('PENDING','ACCEPTED','REJECTED','EXPIRED','CANCELLED') NOT NULL DEFAULT 'PENDING',
  `responded_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `pending_key` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_rr_listing` (`listing_id`),
  KEY `fk_rr_tenant` (`tenant_id`),
  KEY `fk_rr_landlord` (`landlord_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- report_evidences
CREATE TABLE `report_evidences` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `report_id` varchar(36) NOT NULL,
  `image_url` varchar(255) NOT NULL,
  `image_public_id` varchar(255) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_re_report` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- reports
CREATE TABLE `reports` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `tenant_id` varchar(36) NOT NULL,
  `landlord_id` varchar(36) DEFAULT NULL,
  `listing_id` varchar(36) DEFAULT NULL,
  `reason_code` varchar(100) NOT NULL,
  `description` text,
  `status` enum('PENDING','PROCESSING','RESOLVED','REJECTED') NOT NULL DEFAULT 'PENDING',
  `handled_by` varchar(36) DEFAULT NULL,
  `resolution_note` text,
  `resolved_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_rep_tenant` (`tenant_id`),
  KEY `fk_rep_landlord` (`landlord_id`),
  KEY `fk_rep_listing` (`listing_id`),
  KEY `fk_rep_handled_by` (`handled_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- role_permissions
CREATE TABLE `role_permissions` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `role_id` varchar(36) NOT NULL,
  `permission_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permissions` (`role_id`,`permission_id`),
  KEY `fk_rp_permission` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- roles
CREATE TABLE `roles` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `code` enum('TENANT','LANDLORD','ADMIN') NOT NULL,
  `name` varchar(100) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- room_amenities
CREATE TABLE `room_amenities` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `room_id` varchar(36) NOT NULL,
  `amenity_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_room_amenities` (`room_id`,`amenity_id`),
  KEY `fk_ra_amenity` (`amenity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- room_images
CREATE TABLE `room_images` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `room_id` varchar(36) NOT NULL,
  `url` varchar(255) NOT NULL,
  `public_id` varchar(255) DEFAULT NULL,
  `sort_order` int DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `fk_ri_room` (`room_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- room_reviews
CREATE TABLE `room_reviews` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `rental_agreement_id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `room_id` varchar(36) NOT NULL,
  `rating` int NOT NULL,
  `comment` text,
  `landlord_reply` text,
  `is_hidden` tinyint(1) DEFAULT '0',
  `hidden_by` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `rental_agreement_id` (`rental_agreement_id`),
  KEY `fk_rrev_tenant` (`tenant_id`),
  KEY `fk_rrev_room` (`room_id`),
  KEY `fk_rrev_hidden_by` (`hidden_by`),
  CONSTRAINT `room_reviews_chk_1` CHECK (((`rating` >= 1) and (`rating` <= 5)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- room_types
CREATE TABLE `room_types` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `name` varchar(100) NOT NULL,
  `description` text,
  `parent_id` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`),
  KEY `fk_rt_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- rooms
CREATE TABLE `rooms` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `room_type_id` varchar(36) NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `electricity_price` decimal(10,2) DEFAULT NULL,
  `water_price` decimal(10,2) DEFAULT NULL,
  `service_fee` decimal(10,2) DEFAULT NULL,
  `default_deposit` decimal(12,2) DEFAULT NULL,
  `area_m2` decimal(8,2) NOT NULL,
  `max_occupants` int DEFAULT '1',
  `rental_status` enum('AVAILABLE','RENTED','MAINTENANCE') NOT NULL DEFAULT 'AVAILABLE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `property_id` varchar(36) NOT NULL,
  `room_number` varchar(30) NOT NULL,
  `floor_number` int DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rooms_property_room_number` (`property_id`,`room_number`),
  KEY `fk_rooms_room_type` (`room_type_id`),
  KEY `idx_rooms_rental_status` (`rental_status`),
  CONSTRAINT `ck_rooms_floor` CHECK (((`floor_number` is null) or (`floor_number` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- saved_listings
CREATE TABLE `saved_listings` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `tenant_id` varchar(36) NOT NULL,
  `listing_id` varchar(36) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_saved_listings` (`tenant_id`,`listing_id`),
  KEY `fk_sl_listing` (`listing_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- service_package_prices
CREATE TABLE `service_package_prices` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `package_id` varchar(36) NOT NULL,
  `duration_months` int NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_spp_term` (`package_id`,`duration_months`),
  CONSTRAINT `chk_spp_price` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_spp_term` CHECK ((`duration_months` in (1,3,6,12)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- service_package_subscriptions
CREATE TABLE `service_package_subscriptions` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `landlord_id` varchar(36) NOT NULL,
  `package_id` varchar(36) NOT NULL,
  `start_at` datetime NOT NULL,
  `end_at` datetime NOT NULL,
  `status` enum('SCHEDULED','ACTIVE','EXPIRED','CANCELLED') NOT NULL DEFAULT 'ACTIVE',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `active_landlord_id` varchar(36) GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'ACTIVE') then `landlord_id` else NULL end)) STORED,
  `scheduled_landlord_id` varchar(36) GENERATED ALWAYS AS ((case when (`status` = _utf8mb4'SCHEDULED') then `landlord_id` else NULL end)) STORED,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_sps_active_landlord` (`active_landlord_id`),
  UNIQUE KEY `uq_sps_scheduled_landlord` (`scheduled_landlord_id`),
  KEY `fk_sps_package` (`package_id`),
  KEY `ix_sps_landlord_status_end` (`landlord_id`,`status`,`end_at`),
  KEY `ix_sps_status_start` (`status`,`start_at`),
  KEY `ix_sps_status_end` (`status`,`end_at`),
  CONSTRAINT `chk_sps_dates` CHECK ((`end_at` > `start_at`))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- service_packages
CREATE TABLE `service_packages` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  `max_listings` int DEFAULT '0',
  `high_priority_slots` int NOT NULL DEFAULT '0',
  `low_priority_slots` int NOT NULL DEFAULT '0',
  `has_stats` tinyint(1) NOT NULL DEFAULT '0',
  `is_active` tinyint(1) NOT NULL DEFAULT '1',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `default_priority_level` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  CONSTRAINT `chk_sp_limits` CHECK ((((`max_listings` is null) or (`max_listings` >= 0)) and (`high_priority_slots` >= 0) and (`low_priority_slots` >= 0) and (`default_priority_level` in (0,1))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- service_subscription_orders
CREATE TABLE `service_subscription_orders` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `landlord_id` varchar(36) NOT NULL,
  `package_id` varchar(36) NOT NULL,
  `service_package_price_id` varchar(36) NOT NULL,
  `subscription_id` varchar(36) DEFAULT NULL,
  `previous_subscription_id` varchar(36) DEFAULT NULL,
  `type` enum('PURCHASE','RENEW','CHANGE') NOT NULL,
  `status` enum('PENDING_PAYMENT','AWAITING_ADMIN_CONFIRMATION','SCHEDULED','APPLIED','CANCELLED','EXPIRED','REQUIRES_REVIEW') NOT NULL DEFAULT 'PENDING_PAYMENT',
  `duration_months` int NOT NULL,
  `price` decimal(12,2) NOT NULL,
  `package_code_snapshot` varchar(50) NOT NULL,
  `package_name_snapshot` varchar(100) NOT NULL,
  `max_listings_snapshot` int DEFAULT NULL,
  `high_priority_slots_snapshot` int NOT NULL,
  `low_priority_slots_snapshot` int NOT NULL,
  `default_priority_level_snapshot` int NOT NULL,
  `has_stats_snapshot` tinyint(1) NOT NULL,
  `expires_at` datetime NOT NULL,
  `effective_at` datetime DEFAULT NULL,
  `end_at` datetime DEFAULT NULL,
  `previous_end_at` datetime DEFAULT NULL,
  `applied_at` datetime DEFAULT NULL,
  `confirmed_by` varchar(36) DEFAULT NULL,
  `confirmed_at` datetime DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `open_change_landlord_id` varchar(36) GENERATED ALWAYS AS ((case when ((`type` = _utf8mb4'CHANGE') and (`status` in (_utf8mb4'PENDING_PAYMENT',_utf8mb4'AWAITING_ADMIN_CONFIRMATION',_utf8mb4'SCHEDULED'))) then `landlord_id` else NULL end)) STORED,
  `immediate_upgrade` tinyint(1) NOT NULL DEFAULT '0',
  `upgrade_value` decimal(12,2) DEFAULT NULL,
  `upgrade_months` decimal(20,12) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_sso_open_change` (`open_change_landlord_id`),
  KEY `fk_sso_package` (`package_id`),
  KEY `fk_sso_price` (`service_package_price_id`),
  KEY `fk_sso_subscription` (`subscription_id`),
  KEY `fk_sso_previous` (`previous_subscription_id`),
  KEY `fk_sso_confirmer` (`confirmed_by`),
  KEY `ix_sso_landlord_created` (`landlord_id`,`created_at`),
  KEY `ix_sso_status_expiry` (`status`,`expires_at`),
  CONSTRAINT `chk_sso_applied` CHECK (((`status` <> _utf8mb4'APPLIED') or ((`subscription_id` is not null) and (`applied_at` is not null) and (`effective_at` is not null) and (`end_at` is not null)))),
  CONSTRAINT `chk_sso_awaiting_admin` CHECK (((`status` <> _utf8mb4'AWAITING_ADMIN_CONFIRMATION') or (`type` = _utf8mb4'CHANGE'))),
  CONSTRAINT `chk_sso_change` CHECK (((`type` <> _utf8mb4'CHANGE') or (`previous_subscription_id` is not null))),
  CONSTRAINT `chk_sso_confirmation` CHECK ((((`confirmed_by` is null) and (`confirmed_at` is null)) or ((`confirmed_by` is not null) and (`confirmed_at` is not null)))),
  CONSTRAINT `chk_sso_dates` CHECK ((((`effective_at` is null) and (`end_at` is null)) or ((`effective_at` is not null) and (`end_at` is not null) and (`end_at` > `effective_at`)))),
  CONSTRAINT `chk_sso_price` CHECK ((`price` >= 0)),
  CONSTRAINT `chk_sso_rights` CHECK ((((`max_listings_snapshot` is null) or (`max_listings_snapshot` >= 0)) and (`high_priority_slots_snapshot` >= 0) and (`low_priority_slots_snapshot` >= 0) and (`default_priority_level_snapshot` in (0,1)) and (`has_stats_snapshot` in (0,1)))),
  CONSTRAINT `chk_sso_scheduled` CHECK (((`status` <> _utf8mb4'SCHEDULED') or ((`type` = _utf8mb4'CHANGE') and (`subscription_id` is not null) and (`effective_at` is not null) and (`end_at` is not null)))),
  CONSTRAINT `chk_sso_term` CHECK ((`duration_months` in (1,3,6,12))),
  CONSTRAINT `chk_sso_upgrade` CHECK (((`immediate_upgrade` = false) or ((`type` = _utf8mb4'CHANGE') and (`previous_subscription_id` is not null) and (`previous_end_at` is not null) and (`upgrade_value` is not null) and (`upgrade_value` >= 0) and (`upgrade_months` is not null) and (`upgrade_months` > 0))))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- service_subscription_periods
CREATE TABLE `service_subscription_periods` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `subscription_id` varchar(36) NOT NULL,
  `order_id` varchar(36) NOT NULL,
  `period_number` int NOT NULL,
  `start_at` datetime NOT NULL,
  `end_at` datetime NOT NULL,
  `package_code_snapshot` varchar(50) NOT NULL,
  `package_name_snapshot` varchar(100) NOT NULL,
  `max_listings_snapshot` int DEFAULT NULL,
  `high_priority_slots_snapshot` int NOT NULL,
  `low_priority_slots_snapshot` int NOT NULL,
  `default_priority_level_snapshot` int NOT NULL,
  `has_stats_snapshot` tinyint(1) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `entitlement_value` decimal(12,2) NOT NULL DEFAULT '0.00',
  `billing_months` decimal(20,12) NOT NULL DEFAULT '1.000000000000',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_ssp_order` (`order_id`),
  UNIQUE KEY `uq_ssp_number` (`subscription_id`,`period_number`),
  KEY `ix_ssp_subscription_dates` (`subscription_id`,`start_at`,`end_at`),
  CONSTRAINT `chk_ssp_dates` CHECK (((`end_at` > `start_at`) and (`period_number` > 0))),
  CONSTRAINT `chk_ssp_rights` CHECK ((((`max_listings_snapshot` is null) or (`max_listings_snapshot` >= 0)) and (`high_priority_slots_snapshot` >= 0) and (`low_priority_slots_snapshot` >= 0) and (`default_priority_level_snapshot` in (0,1)) and (`has_stats_snapshot` in (0,1)))),
  CONSTRAINT `chk_ssp_value` CHECK (((`entitlement_value` >= 0) and (`billing_months` > 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- tenant_profiles
CREATE TABLE `tenant_profiles` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `user_id` varchar(36) NOT NULL,
  `desired_province_id` varchar(36) DEFAULT NULL,
  `desired_ward_id` varchar(36) DEFAULT NULL,
  `price_min` decimal(12,2) DEFAULT NULL,
  `price_max` decimal(12,2) DEFAULT NULL,
  `area_min` decimal(8,2) DEFAULT NULL,
  `area_max` decimal(8,2) DEFAULT NULL,
  `desired_room_type_id` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`),
  KEY `fk_tp_province` (`desired_province_id`),
  KEY `fk_tp_ward` (`desired_ward_id`),
  KEY `fk_tp_room_type` (`desired_room_type_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- tenant_reviews
CREATE TABLE `tenant_reviews` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `rental_agreement_id` varchar(36) NOT NULL,
  `landlord_id` varchar(36) NOT NULL,
  `tenant_id` varchar(36) NOT NULL,
  `rating` int NOT NULL,
  `comment` text,
  `is_hidden` tinyint(1) DEFAULT '0',
  `hidden_by` varchar(36) DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `rental_agreement_id` (`rental_agreement_id`),
  KEY `fk_trev_landlord` (`landlord_id`),
  KEY `fk_trev_tenant` (`tenant_id`),
  KEY `fk_trev_hidden_by` (`hidden_by`),
  CONSTRAINT `tenant_reviews_chk_1` CHECK (((`rating` >= 1) and (`rating` <= 5)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- user_roles
CREATE TABLE `user_roles` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `user_id` varchar(36) NOT NULL,
  `role_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_roles` (`user_id`,`role_id`),
  KEY `fk_ur_role` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- users
CREATE TABLE `users` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `email` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `full_name` varchar(100) DEFAULT NULL,
  `avatar_url` varchar(255) DEFAULT NULL,
  `avatar_public_id` varchar(255) DEFAULT NULL,
  `citizen_id` varchar(20) DEFAULT NULL,
  `email_verified_at` datetime DEFAULT NULL,
  `phone_verified_at` datetime DEFAULT NULL,
  `status` enum('ACTIVE','INACTIVE','BLOCKED') NOT NULL DEFAULT 'ACTIVE',
  `auth_version` int NOT NULL DEFAULT '0',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`),
  UNIQUE KEY `phone` (`phone`),
  UNIQUE KEY `citizen_id` (`citizen_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- wards
CREATE TABLE `wards` (
  `id` varchar(36) NOT NULL DEFAULT (uuid()),
  `province_id` varchar(36) NOT NULL,
  `code` varchar(50) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `code` (`code`),
  KEY `fk_wards_province` (`province_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- FOREIGN KEY RELATIONSHIPS
ALTER TABLE `boost_allocations` ADD CONSTRAINT `fk_boost_allocation_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `boost_allocations` ADD CONSTRAINT `fk_boost_allocation_subscription` FOREIGN KEY (`boost_subscription_id`) REFERENCES `boost_service_package_subscriptions` (`id`) ON DELETE RESTRICT;
ALTER TABLE `boost_service_package_subscriptions` ADD CONSTRAINT `fk_bsps_landlord_history` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `boost_service_package_subscriptions` ADD CONSTRAINT `fk_bsps_package` FOREIGN KEY (`boost_package_id`) REFERENCES `boost_service_packages` (`id`) ON DELETE RESTRICT;
ALTER TABLE `chat_conversation_participants` ADD CONSTRAINT `fk_ccp_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `chat_conversations` (`id`) ON DELETE CASCADE;
ALTER TABLE `chat_conversation_participants` ADD CONSTRAINT `fk_ccp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `chat_conversations` ADD CONSTRAINT `fk_cc_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE SET NULL;
ALTER TABLE `chat_conversations` ADD CONSTRAINT `fk_cc_request` FOREIGN KEY (`rental_request_id`) REFERENCES `rental_requests` (`id`) ON DELETE SET NULL;
ALTER TABLE `chat_messages` ADD CONSTRAINT `fk_cm_conversation` FOREIGN KEY (`conversation_id`) REFERENCES `chat_conversations` (`id`) ON DELETE CASCADE;
ALTER TABLE `chat_messages` ADD CONSTRAINT `fk_cm_sender` FOREIGN KEY (`sender_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `invoices` ADD CONSTRAINT `fk_invoices_bsp` FOREIGN KEY (`boost_service_package_id`) REFERENCES `boost_service_packages` (`id`) ON DELETE SET NULL;
ALTER TABLE `invoices` ADD CONSTRAINT `fk_invoices_payment_history` FOREIGN KEY (`payment_id`) REFERENCES `payments` (`id`) ON DELETE RESTRICT;
ALTER TABLE `invoices` ADD CONSTRAINT `fk_invoices_sp` FOREIGN KEY (`service_package_id`) REFERENCES `service_packages` (`id`) ON DELETE SET NULL;
ALTER TABLE `landlord_reviews` ADD CONSTRAINT `fk_lrev_agreement` FOREIGN KEY (`rental_agreement_id`) REFERENCES `rental_agreements` (`id`) ON DELETE CASCADE;
ALTER TABLE `landlord_reviews` ADD CONSTRAINT `fk_lrev_hidden_by` FOREIGN KEY (`hidden_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;
ALTER TABLE `landlord_reviews` ADD CONSTRAINT `fk_lrev_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `landlord_reviews` ADD CONSTRAINT `fk_lrev_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `listing_contact_events` ADD CONSTRAINT `fk_lce_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `listing_contact_events` ADD CONSTRAINT `fk_lce_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `listing_daily_stats` ADD CONSTRAINT `fk_lds_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `listing_view_history` ADD CONSTRAINT `fk_lvh_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `listing_view_history` ADD CONSTRAINT `fk_lvh_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `listings` ADD CONSTRAINT `fk_listing_priority_period` FOREIGN KEY (`service_subscription_period_id`) REFERENCES `service_subscription_periods` (`id`) ON DELETE RESTRICT;
ALTER TABLE `listings` ADD CONSTRAINT `fk_listings_approved_by` FOREIGN KEY (`approved_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;
ALTER TABLE `listings` ADD CONSTRAINT `fk_listings_property` FOREIGN KEY (`property_id`) REFERENCES `properties` (`id`) ON DELETE RESTRICT;
ALTER TABLE `notifications` ADD CONSTRAINT `fk_notif_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `payments` ADD CONSTRAINT `fk_payment_boost` FOREIGN KEY (`boost_subscription_id`) REFERENCES `boost_service_package_subscriptions` (`id`) ON DELETE RESTRICT;
ALTER TABLE `payments` ADD CONSTRAINT `fk_payment_order` FOREIGN KEY (`service_subscription_order_id`) REFERENCES `service_subscription_orders` (`id`) ON DELETE RESTRICT;
ALTER TABLE `payments` ADD CONSTRAINT `fk_payments_landlord_history` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `properties` ADD CONSTRAINT `fk_properties_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `properties` ADD CONSTRAINT `fk_properties_province` FOREIGN KEY (`province_id`) REFERENCES `provinces` (`id`) ON DELETE RESTRICT;
ALTER TABLE `properties` ADD CONSTRAINT `fk_properties_type` FOREIGN KEY (`property_type_id`) REFERENCES `property_types` (`id`) ON DELETE RESTRICT;
ALTER TABLE `properties` ADD CONSTRAINT `fk_properties_ward` FOREIGN KEY (`ward_id`) REFERENCES `wards` (`id`) ON DELETE RESTRICT;
ALTER TABLE `property_amenities` ADD CONSTRAINT `fk_pa_amenity` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE RESTRICT;
ALTER TABLE `property_amenities` ADD CONSTRAINT `fk_pa_property` FOREIGN KEY (`property_id`) REFERENCES `properties` (`id`) ON DELETE CASCADE;
ALTER TABLE `property_images` ADD CONSTRAINT `fk_pi_property` FOREIGN KEY (`property_id`) REFERENCES `properties` (`id`) ON DELETE CASCADE;
ALTER TABLE `refresh_tokens` ADD CONSTRAINT `fk_rt_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `rental_agreements` ADD CONSTRAINT `fk_rag_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `rental_agreements` ADD CONSTRAINT `fk_rag_request` FOREIGN KEY (`rental_request_id`) REFERENCES `rental_requests` (`id`) ON DELETE SET NULL;
ALTER TABLE `rental_agreements` ADD CONSTRAINT `fk_rag_room` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE RESTRICT;
ALTER TABLE `rental_agreements` ADD CONSTRAINT `fk_rag_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `rental_requests` ADD CONSTRAINT `fk_rr_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `rental_requests` ADD CONSTRAINT `fk_rr_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `rental_requests` ADD CONSTRAINT `fk_rr_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `report_evidences` ADD CONSTRAINT `fk_re_report` FOREIGN KEY (`report_id`) REFERENCES `reports` (`id`) ON DELETE CASCADE;
ALTER TABLE `reports` ADD CONSTRAINT `fk_rep_handled_by` FOREIGN KEY (`handled_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;
ALTER TABLE `reports` ADD CONSTRAINT `fk_rep_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `reports` ADD CONSTRAINT `fk_rep_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `reports` ADD CONSTRAINT `fk_rep_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `role_permissions` ADD CONSTRAINT `fk_rp_permission` FOREIGN KEY (`permission_id`) REFERENCES `permissions` (`id`) ON DELETE CASCADE;
ALTER TABLE `role_permissions` ADD CONSTRAINT `fk_rp_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_amenities` ADD CONSTRAINT `fk_ra_amenity` FOREIGN KEY (`amenity_id`) REFERENCES `amenities` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_amenities` ADD CONSTRAINT `fk_ra_room` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_images` ADD CONSTRAINT `fk_ri_room` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_reviews` ADD CONSTRAINT `fk_rrev_agreement` FOREIGN KEY (`rental_agreement_id`) REFERENCES `rental_agreements` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_reviews` ADD CONSTRAINT `fk_rrev_hidden_by` FOREIGN KEY (`hidden_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;
ALTER TABLE `room_reviews` ADD CONSTRAINT `fk_rrev_room` FOREIGN KEY (`room_id`) REFERENCES `rooms` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_reviews` ADD CONSTRAINT `fk_rrev_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `room_types` ADD CONSTRAINT `fk_rt_parent` FOREIGN KEY (`parent_id`) REFERENCES `room_types` (`id`) ON DELETE SET NULL;
ALTER TABLE `rooms` ADD CONSTRAINT `fk_rooms_property` FOREIGN KEY (`property_id`) REFERENCES `properties` (`id`) ON DELETE RESTRICT;
ALTER TABLE `rooms` ADD CONSTRAINT `fk_rooms_room_type` FOREIGN KEY (`room_type_id`) REFERENCES `room_types` (`id`) ON DELETE RESTRICT;
ALTER TABLE `saved_listings` ADD CONSTRAINT `fk_sl_listing` FOREIGN KEY (`listing_id`) REFERENCES `listings` (`id`) ON DELETE CASCADE;
ALTER TABLE `saved_listings` ADD CONSTRAINT `fk_sl_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `service_package_prices` ADD CONSTRAINT `fk_spp_package` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_package_subscriptions` ADD CONSTRAINT `fk_sps_landlord_history` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_package_subscriptions` ADD CONSTRAINT `fk_sps_package` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_orders` ADD CONSTRAINT `fk_sso_confirmer` FOREIGN KEY (`confirmed_by`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_orders` ADD CONSTRAINT `fk_sso_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_orders` ADD CONSTRAINT `fk_sso_package` FOREIGN KEY (`package_id`) REFERENCES `service_packages` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_orders` ADD CONSTRAINT `fk_sso_previous` FOREIGN KEY (`previous_subscription_id`) REFERENCES `service_package_subscriptions` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_orders` ADD CONSTRAINT `fk_sso_price` FOREIGN KEY (`service_package_price_id`) REFERENCES `service_package_prices` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_orders` ADD CONSTRAINT `fk_sso_subscription` FOREIGN KEY (`subscription_id`) REFERENCES `service_package_subscriptions` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_periods` ADD CONSTRAINT `fk_ssp_order` FOREIGN KEY (`order_id`) REFERENCES `service_subscription_orders` (`id`) ON DELETE RESTRICT;
ALTER TABLE `service_subscription_periods` ADD CONSTRAINT `fk_ssp_subscription` FOREIGN KEY (`subscription_id`) REFERENCES `service_package_subscriptions` (`id`) ON DELETE RESTRICT;
ALTER TABLE `tenant_profiles` ADD CONSTRAINT `fk_tp_province` FOREIGN KEY (`desired_province_id`) REFERENCES `provinces` (`id`) ON DELETE SET NULL;
ALTER TABLE `tenant_profiles` ADD CONSTRAINT `fk_tp_room_type` FOREIGN KEY (`desired_room_type_id`) REFERENCES `room_types` (`id`) ON DELETE SET NULL;
ALTER TABLE `tenant_profiles` ADD CONSTRAINT `fk_tp_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `tenant_profiles` ADD CONSTRAINT `fk_tp_ward` FOREIGN KEY (`desired_ward_id`) REFERENCES `wards` (`id`) ON DELETE SET NULL;
ALTER TABLE `tenant_reviews` ADD CONSTRAINT `fk_trev_agreement` FOREIGN KEY (`rental_agreement_id`) REFERENCES `rental_agreements` (`id`) ON DELETE CASCADE;
ALTER TABLE `tenant_reviews` ADD CONSTRAINT `fk_trev_hidden_by` FOREIGN KEY (`hidden_by`) REFERENCES `users` (`id`) ON DELETE SET NULL;
ALTER TABLE `tenant_reviews` ADD CONSTRAINT `fk_trev_landlord` FOREIGN KEY (`landlord_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `tenant_reviews` ADD CONSTRAINT `fk_trev_tenant` FOREIGN KEY (`tenant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_ur_role` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`) ON DELETE CASCADE;
ALTER TABLE `user_roles` ADD CONSTRAINT `fk_ur_user` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE;
ALTER TABLE `wards` ADD CONSTRAINT `fk_wards_province` FOREIGN KEY (`province_id`) REFERENCES `provinces` (`id`) ON DELETE CASCADE;

-- Minimal system catalog required by the subscription flow; no demo data.
INSERT INTO service_packages
    (id, code, name, max_listings, high_priority_slots, low_priority_slots,
     default_priority_level, has_stats, is_active)
VALUES (UUID(), 'FREE', 'Free', 3, 0, 0, 0, FALSE, TRUE);
