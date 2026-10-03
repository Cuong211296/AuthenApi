-- Schema for the tablet's MariaDB (13.0.2, Termux 32-bit). Same tables as Hibernate's ddl-auto=update creates, but
-- WITHOUT foreign key constraints: that MariaDB build cannot create a table with two foreign keys, and Hibernate's
-- ALTER TABLE ... ADD CONSTRAINT leaves broken, duplicated constraints that reject valid rows.
-- Generated 2026-10-03 from the entities at migration v5. After an entity change, add the column with a migration SQL.
SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS=0;
CREATE TABLE IF NOT EXISTS `category` (
  `id` varchar(255) NOT NULL,
  `name` varchar(100) NOT NULL,
  `slug` varchar(120) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_hqknmjh5423vchi4xkyhxlhg2` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `invalidated_token` (
  `id` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expiry_time` datetime(6) NOT NULL,
  `jti` varchar(64) NOT NULL,
  `reason` varchar(100) DEFAULT NULL,
  `user_id` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_5plti4ciex2avf58nlcmw8fov` (`jti`),
  KEY `idx_token_expiry` (`expiry_time`),
  KEY `idx_token_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `permission` (
  `id` varchar(255) NOT NULL,
  `category` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `status` varchar(50) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_permission_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product` (
  `id` varchar(255) NOT NULL,
  `active` bit(1) NOT NULL,
  `base_price` bigint(20) NOT NULL,
  `cost_price` bigint(20) DEFAULT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `description` varchar(4000) DEFAULT NULL,
  `image_url` varchar(500) DEFAULT NULL,
  `name` varchar(200) NOT NULL,
  `slug` varchar(220) NOT NULL,
  `weight_grams` int(11) DEFAULT NULL,
  `category_id` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_88yb4l9100epddqsrdvxerhq9` (`slug`),
  KEY `FK1mtsbur82frn64de7balymq9s` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `product_variant` (
  `id` varchar(255) NOT NULL,
  `active` bit(1) NOT NULL,
  `color_value` varchar(50) NOT NULL,
  `price` bigint(20) DEFAULT NULL,
  `size_value` varchar(20) NOT NULL,
  `sku` varchar(64) NOT NULL,
  `stock` int(11) NOT NULL,
  `product_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_variant_combo` (`product_id`,`size_value`,`color_value`),
  UNIQUE KEY `UK_ivtjmjnhkb977nvkx92oyujw8` (`sku`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `role` (
  `id` varchar(255) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `description` varchar(500) DEFAULT NULL,
  `name` varchar(100) NOT NULL,
  `status` varchar(50) NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_role_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `role_permission` (
  `role_id` varchar(255) NOT NULL,
  `permission_id` varchar(255) NOT NULL,
  PRIMARY KEY (`role_id`,`permission_id`),
  KEY `FKf8yllw1ecvwqy3ehyxawqa1qp` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shipping_rate` (
  `id` varchar(255) NOT NULL,
  `fee` bigint(20) NOT NULL,
  `province` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_jlkeexdnpefp2ms76d96yllbt` (`province`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `shop_settings` (
  `id` varchar(20) NOT NULL,
  `address` varchar(300) DEFAULT NULL,
  `district_id` int(11) DEFAULT NULL,
  `district_name` varchar(100) DEFAULT NULL,
  `ghn_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `ghtk_enabled` tinyint(1) NOT NULL DEFAULT 1,
  `phone` varchar(20) DEFAULT NULL,
  `province_id` int(11) DEFAULT NULL,
  `province_name` varchar(100) DEFAULT NULL,
  `shop_name` varchar(100) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` varchar(100) DEFAULT NULL,
  `ward_code` varchar(20) DEFAULT NULL,
  `ward_name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `user` (
  `id` varchar(255) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `dob` date NOT NULL,
  `email` varchar(150) DEFAULT NULL,
  `firstname` varchar(100) DEFAULT NULL,
  `last_login_at` datetime(6) DEFAULT NULL,
  `lastname` varchar(100) DEFAULT NULL,
  `locked_until` datetime(6) DEFAULT NULL,
  `login_attempts` int(11) DEFAULT 0,
  `login_method` varchar(50) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `status` varchar(50) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `username` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_username` (`username`),
  UNIQUE KEY `idx_email` (`email`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `user_role` (
  `user_id` varchar(255) NOT NULL,
  `role_id` varchar(255) NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FKa68196081fvovjhkek5m97n3y` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `cart` (
  `id` varchar(255) NOT NULL,
  `user_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `cart_item` (
  `id` varchar(255) NOT NULL,
  `quantity` int(11) NOT NULL,
  `cart_id` varchar(255) NOT NULL,
  `variant_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_variant` (`cart_id`,`variant_id`),
  KEY `FK3fx72yo9k5xauka8mlto7a8bf` (`variant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `orders` (
  `id` varchar(255) NOT NULL,
  `address` varchar(300) NOT NULL,
  `code` varchar(40) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `district` varchar(100) DEFAULT NULL,
  `email` varchar(150) NOT NULL,
  `expires_at` datetime(6) DEFAULT NULL,
  `note` varchar(500) DEFAULT NULL,
  `paid_at` datetime(6) DEFAULT NULL,
  `payment_method` enum('COD','MOMO') NOT NULL,
  `payment_status` enum('UNPAID','PAID','FAILED','EXPIRED') NOT NULL,
  `phone` varchar(20) NOT NULL,
  `province` varchar(100) NOT NULL,
  `receiver_name` varchar(100) NOT NULL,
  `shipping_fee` bigint(20) NOT NULL,
  `shipping_source` varchar(10) DEFAULT NULL,
  `status` enum('PENDING_PAYMENT','PENDING_CONFIRM','CONFIRMED','SHIPPING','COMPLETED','CANCELLED') NOT NULL,
  `subtotal` bigint(20) NOT NULL,
  `total` bigint(20) NOT NULL,
  `ward` varchar(100) DEFAULT NULL,
  `weight_grams` int(11) DEFAULT NULL,
  `user_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_gt3o4a5bqj59e9y6wakgk926t` (`code`),
  KEY `idx_order_status` (`status`),
  KEY `FKel9kyl84ego2otj2accfd8mr7` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `payment` (
  `id` varchar(255) NOT NULL,
  `amount` bigint(20) NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `provider` varchar(20) NOT NULL,
  `provider_order_id` varchar(80) NOT NULL,
  `raw_response` varchar(4000) DEFAULT NULL,
  `request_id` varchar(64) NOT NULL,
  `status` enum('PENDING','SUCCESS','FAILED') NOT NULL,
  `trans_id` bigint(20) DEFAULT NULL,
  `order_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_qhbnrpjyr0g05a2ikc0djpl7h` (`provider_order_id`),
  KEY `FKlouu98csyullos9k25tbpk4va` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS `order_item` (
  `id` varchar(255) NOT NULL,
  `color_value` varchar(50) NOT NULL,
  `product_name` varchar(200) NOT NULL,
  `quantity` int(11) NOT NULL,
  `size_value` varchar(20) NOT NULL,
  `unit_cost` bigint(20) DEFAULT NULL,
  `unit_price` bigint(20) NOT NULL,
  `variant_id` varchar(36) NOT NULL,
  `order_id` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKt4dc2r9nbvbujrljv3e23iibt` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
SET FOREIGN_KEY_CHECKS=1;
