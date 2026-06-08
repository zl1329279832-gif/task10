CREATE DATABASE IF NOT EXISTS campus_trade DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE campus_trade;

-- ----------------------------
-- 用户表
-- ----------------------------
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    nickname VARCHAR(50),
    phone VARCHAR(20) UNIQUE,
    email VARCHAR(100),
    avatar_url VARCHAR(500),
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0禁用 1正常',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ----------------------------
-- 用户角色表
-- ----------------------------
DROP TABLE IF EXISTS t_user_role;
CREATE TABLE t_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'BUYER/SELLER/ADMIN',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_role (user_id, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色表';

-- ----------------------------
-- 商品表
-- ----------------------------
DROP TABLE IF EXISTS t_product;
CREATE TABLE t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock INT NOT NULL DEFAULT 0,
    category VARCHAR(50) NOT NULL,
    image_urls VARCHAR(2000) COMMENT '图片URL,逗号分隔',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '0下架 1上架',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_seller_id (seller_id),
    INDEX idx_category (category),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ----------------------------
-- 订单表
-- ----------------------------
DROP TABLE IF EXISTS t_order;
CREATE TABLE t_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL UNIQUE,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_title VARCHAR(200) NOT NULL COMMENT '商品标题快照',
    quantity INT NOT NULL,
    unit_price DECIMAL(10,2) NOT NULL COMMENT '下单时单价快照',
    total_amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(30) NOT NULL COMMENT '订单状态',
    payment_deadline DATETIME COMMENT '支付截止时间',
    paid_at DATETIME,
    shipped_at DATETIME,
    received_at DATETIME,
    closed_at DATETIME,
    close_reason VARCHAR(200),
    idempotent_key VARCHAR(64) UNIQUE COMMENT '创单幂等键',
    version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_buyer_id (buyer_id),
    INDEX idx_seller_id (seller_id),
    INDEX idx_order_no (order_no),
    INDEX idx_status (status),
    INDEX idx_payment_deadline (payment_deadline)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ----------------------------
-- 支付记录表
-- ----------------------------
DROP TABLE IF EXISTS t_payment_record;
CREATE TABLE t_payment_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(32) NOT NULL,
    trade_no VARCHAR(64) UNIQUE COMMENT '支付宝交易号',
    amount DECIMAL(10,2) NOT NULL,
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/SUCCESS/FAILED/CLOSED_IGNORED',
    pay_channel VARCHAR(20) NOT NULL DEFAULT 'ALIPAY',
    callback_raw TEXT COMMENT '回调原始报文',
    callback_time DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='支付记录表';

-- ----------------------------
-- 退款记录表
-- ----------------------------
DROP TABLE IF EXISTS t_refund_record;
CREATE TABLE t_refund_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(32) NOT NULL UNIQUE,
    order_no VARCHAR(32) NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    evidence_urls VARCHAR(2000) COMMENT '举证图片URL',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/APPROVED/REJECTED',
    handler_id BIGINT COMMENT '处理人ID',
    handle_remark VARCHAR(500),
    handled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no),
    INDEX idx_buyer_id (buyer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='退款记录表';

-- ----------------------------
-- 争议仲裁表
-- ----------------------------
DROP TABLE IF EXISTS t_dispute;
CREATE TABLE t_dispute (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    dispute_no VARCHAR(32) NOT NULL UNIQUE,
    order_no VARCHAR(32) NOT NULL,
    refund_no VARCHAR(32) COMMENT '关联退款编号',
    initiator_id BIGINT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    buyer_evidence VARCHAR(2000),
    seller_evidence VARCHAR(2000),
    status VARCHAR(20) NOT NULL COMMENT 'OPEN/ARBITRATING/CLOSED',
    arbitrator_id BIGINT,
    arbitrate_result VARCHAR(20) COMMENT 'BUYER_WIN/SELLER_WIN',
    arbitrate_remark VARCHAR(500),
    arbitrated_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='争议仲裁表';

-- ----------------------------
-- 结算记录表
-- ----------------------------
DROP TABLE IF EXISTS t_settlement;
CREATE TABLE t_settlement (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    settlement_no VARCHAR(32) NOT NULL UNIQUE,
    order_no VARCHAR(32) NOT NULL,
    seller_id BIGINT NOT NULL,
    amount DECIMAL(10,2) NOT NULL COMMENT '订单金额',
    platform_fee DECIMAL(10,2) NOT NULL DEFAULT 0 COMMENT '平台手续费',
    actual_amount DECIMAL(10,2) NOT NULL COMMENT '实际结算金额',
    status VARCHAR(20) NOT NULL COMMENT 'PENDING/SETTLED',
    settled_at DATETIME,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_no (order_no),
    INDEX idx_seller_id (seller_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='结算记录表';

-- ----------------------------
-- 审计日志表
-- ----------------------------
DROP TABLE IF EXISTS t_audit_log;
CREATE TABLE t_audit_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT,
    username VARCHAR(50),
    module VARCHAR(50) NOT NULL COMMENT '业务模块',
    action VARCHAR(50) NOT NULL COMMENT '操作动作',
    target_type VARCHAR(30) COMMENT '目标实体类型',
    target_id VARCHAR(64) COMMENT '目标实体ID',
    detail TEXT COMMENT '操作详情JSON',
    ip_address VARCHAR(45),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_id (user_id),
    INDEX idx_module_action (module, action),
    INDEX idx_target (target_type, target_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审计日志表';

-- ----------------------------
-- 幂等键表
-- ----------------------------
DROP TABLE IF EXISTS t_idempotent_key;
CREATE TABLE t_idempotent_key (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotent_key VARCHAR(64) NOT NULL UNIQUE,
    biz_type VARCHAR(30) NOT NULL COMMENT 'ORDER_CREATE/PAY_CALLBACK等',
    biz_id VARCHAR(64),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expire_at DATETIME NOT NULL COMMENT '过期时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等键表';
