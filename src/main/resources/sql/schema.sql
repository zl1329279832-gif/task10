SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

CREATE TABLE `t_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `username` VARCHAR(64) NOT NULL, `password` VARCHAR(255) NOT NULL,
    `nickname` VARCHAR(64) DEFAULT NULL, `avatar` VARCHAR(512) DEFAULT NULL, `phone` VARCHAR(20) DEFAULT NULL,
    `email` VARCHAR(128) DEFAULT NULL, `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=disabled,1=active',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user table';

CREATE TABLE `t_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `role_code` VARCHAR(32) NOT NULL, `role_name` VARCHAR(64) NOT NULL,
    `description` VARCHAR(256) DEFAULT NULL, `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='role table';

CREATE TABLE `t_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT NOT NULL, `role_id` BIGINT NOT NULL,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_user_role` (`user_id`,`role_id`), KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='user-role mapping';

CREATE TABLE `t_product` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `seller_id` BIGINT NOT NULL, `title` VARCHAR(200) NOT NULL,
    `description` TEXT DEFAULT NULL, `category` VARCHAR(64) DEFAULT NULL, `images` VARCHAR(2048) DEFAULT NULL,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=off-shelf,1=on-sale,2=sold-out',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_seller` (`seller_id`), KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='product table';

CREATE TABLE `t_sku` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `product_id` BIGINT NOT NULL, `sku_name` VARCHAR(200) DEFAULT NULL,
    `price` DECIMAL(10,2) NOT NULL, `stock` INT NOT NULL DEFAULT 0, `lock_stock` INT NOT NULL DEFAULT 0,
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '0=disabled,1=active',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_product` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='sku / inventory table';

CREATE TABLE `t_order` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `order_no` VARCHAR(32) NOT NULL, `buyer_id` BIGINT NOT NULL,
    `seller_id` BIGINT NOT NULL, `product_id` BIGINT NOT NULL, `sku_id` BIGINT NOT NULL, `sku_name` VARCHAR(200) DEFAULT NULL,
    `quantity` INT NOT NULL DEFAULT 1, `unit_price` DECIMAL(10,2) NOT NULL, `total_amount` DECIMAL(10,2) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'CREATED', `pay_time` DATETIME DEFAULT NULL, `ship_time` DATETIME DEFAULT NULL,
    `receive_time` DATETIME DEFAULT NULL, `close_time` DATETIME DEFAULT NULL, `close_reason` VARCHAR(512) DEFAULT NULL,
    `logistics_no` VARCHAR(128) DEFAULT NULL, `logistics_company` VARCHAR(128) DEFAULT NULL,
    `address` VARCHAR(512) DEFAULT NULL, `remark` VARCHAR(512) DEFAULT NULL,
    `pay_expire_at` DATETIME NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_order_no` (`order_no`),
    KEY `idx_buyer` (`buyer_id`,`status`), KEY `idx_seller` (`seller_id`,`status`),
    KEY `idx_status` (`status`), KEY `idx_pay_expire` (`status`,`pay_expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order table';

CREATE TABLE `t_order_status_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `order_id` BIGINT NOT NULL, `from_status` VARCHAR(32) NOT NULL,
    `to_status` VARCHAR(32) NOT NULL, `operator_id` BIGINT DEFAULT NULL, `remark` VARCHAR(512) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_order` (`order_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='order state transition audit log';

CREATE TABLE `t_payment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `payment_no` VARCHAR(64) NOT NULL, `order_id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL, `trade_no` VARCHAR(128) DEFAULT NULL, `amount` DECIMAL(10,2) NOT NULL,
    `pay_channel` VARCHAR(32) NOT NULL DEFAULT 'ALIPAY', `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `callback_content` TEXT DEFAULT NULL, `notify_count` INT NOT NULL DEFAULT 0,
    `paid_at` DATETIME DEFAULT NULL,
    `frozen_amount` DECIMAL(10,2) DEFAULT NULL, `freeze_reason` VARCHAR(256) DEFAULT NULL,
    `frozen_at` DATETIME DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_payment_no` (`payment_no`),
    KEY `idx_order` (`order_id`), KEY `idx_order_no` (`order_no`), KEY `idx_trade_no` (`trade_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='payment record';

CREATE TABLE `t_refund` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `refund_no` VARCHAR(64) NOT NULL, `order_id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL, `buyer_id` BIGINT NOT NULL, `seller_id` BIGINT NOT NULL,
    `refund_amount` DECIMAL(10,2) NOT NULL, `reason` VARCHAR(512) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING', `evidence_urls` VARCHAR(2048) DEFAULT NULL,
    `reject_reason` VARCHAR(512) DEFAULT NULL, `approved_by` BIGINT DEFAULT NULL, `approved_at` DATETIME DEFAULT NULL,
    `refund_trade_no` VARCHAR(128) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_refund_no` (`refund_no`),
    KEY `idx_order` (`order_id`), KEY `idx_buyer` (`buyer_id`), KEY `idx_seller` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='refund application';

CREATE TABLE `t_dispute` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `dispute_no` VARCHAR(64) NOT NULL, `order_id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL, `initiator_id` BIGINT NOT NULL, `respondent_id` BIGINT NOT NULL,
    `reason` VARCHAR(1024) NOT NULL, `evidence_urls` VARCHAR(2048) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'OPEN',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_dispute_no` (`dispute_no`), KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dispute record';

CREATE TABLE `t_dispute_evidence` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `dispute_id` BIGINT NOT NULL, `submitter_id` BIGINT NOT NULL,
    `content` VARCHAR(2048) NOT NULL, `image_urls` VARCHAR(2048) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_dispute` (`dispute_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='dispute evidence submissions';

CREATE TABLE `t_arbitration` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `arbitration_no` VARCHAR(64) NOT NULL, `dispute_id` BIGINT NOT NULL,
    `order_id` BIGINT NOT NULL, `arbiter_id` BIGINT NOT NULL, `result` VARCHAR(32) NOT NULL,
    `decision` TEXT NOT NULL, `refund_amount` DECIMAL(10,2) DEFAULT NULL,
    `seller_amount` DECIMAL(10,2) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_arbitration_no` (`arbitration_no`),
    KEY `idx_dispute` (`dispute_id`), KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='arbitration record';

CREATE TABLE `t_fund_split` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `split_no` VARCHAR(64) NOT NULL,
    `arbitration_id` BIGINT NOT NULL, `payment_id` BIGINT NOT NULL, `order_id` BIGINT NOT NULL,
    `total_amount` DECIMAL(10,2) NOT NULL,
    `buyer_refund_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `seller_settle_amount` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `platform_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00,
    `buyer_refund_no` VARCHAR(64) DEFAULT NULL, `seller_settlement_no` VARCHAR(64) DEFAULT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_split_no` (`split_no`),
    KEY `idx_arbitration` (`arbitration_id`), KEY `idx_payment` (`payment_id`), KEY `idx_order` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='fund split record for arbitration rulings';

CREATE TABLE `t_settlement` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `settlement_no` VARCHAR(64) NOT NULL, `order_id` BIGINT NOT NULL,
    `order_no` VARCHAR(32) NOT NULL, `seller_id` BIGINT NOT NULL, `order_amount` DECIMAL(10,2) NOT NULL,
    `platform_fee` DECIMAL(10,2) NOT NULL DEFAULT 0.00, `settle_amount` DECIMAL(10,2) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING', `settled_at` DATETIME DEFAULT NULL,
    `frozen_amount` DECIMAL(10,2) DEFAULT NULL, `freeze_reason` VARCHAR(256) DEFAULT NULL,
    `frozen_at` DATETIME DEFAULT NULL, `escrow_amount` DECIMAL(10,2) DEFAULT NULL,
    `retry_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_settlement_no` (`settlement_no`),
    KEY `idx_order` (`order_id`), KEY `idx_seller` (`seller_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='settlement record';

CREATE TABLE `t_audit_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `user_id` BIGINT DEFAULT NULL, `username` VARCHAR(64) DEFAULT NULL,
    `module` VARCHAR(64) NOT NULL, `action` VARCHAR(64) NOT NULL, `target_type` VARCHAR(64) DEFAULT NULL,
    `target_id` BIGINT DEFAULT NULL, `detail` TEXT DEFAULT NULL, `ip` VARCHAR(64) DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), KEY `idx_user` (`user_id`,`created_at`),
    KEY `idx_module` (`module`,`created_at`), KEY `idx_target` (`target_type`,`target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='operation audit log';

CREATE TABLE `t_idempotent` (
    `id` BIGINT NOT NULL AUTO_INCREMENT, `idempotent_key` VARCHAR(128) NOT NULL, `biz_type` VARCHAR(64) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'PROCESSING', `result` TEXT DEFAULT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`), UNIQUE KEY `uk_idempotent` (`biz_type`,`idempotent_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='idempotent key store for dedup';

SET FOREIGN_KEY_CHECKS = 1;
