USE campus_trade;

-- 3个测试用户（密码都是 password123 的BCrypt哈希）
-- BCrypt hash for "password123": $2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi

-- 管理员: admin
INSERT INTO t_user (username, password, nickname, phone, status) VALUES
('admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '平台管理员', '13800000001', 1),
('buyer1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '张三', '13800000002', 1),
('seller1', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', '李四', '13800000003', 1);

-- 角色分配
INSERT INTO t_user_role (user_id, role) VALUES
(1, 'ADMIN'),
(2, 'BUYER'),
(3, 'SELLER'),
(3, 'BUYER');  -- seller1同时也是买家

-- 测试商品（seller1发布）
INSERT INTO t_product (seller_id, title, description, price, stock, category, image_urls, status) VALUES
(3, '九成新高数课本', '同济版高等数学上下册，几乎全新，无笔记', 25.00, 5, '教材', 'https://example.com/img/math1.jpg', 1),
(3, 'iPad Air 4 2020款', '使用一年，无划痕，配原装充电器', 2800.00, 1, '数码', 'https://example.com/img/ipad1.jpg,https://example.com/img/ipad2.jpg', 1),
(3, '自行车 捷安特ATX660', '骑了两年，正常使用痕迹', 450.00, 1, '出行', 'https://example.com/img/bike1.jpg', 1),
(3, '四级词汇书 乱序版', '全新未拆封', 15.00, 10, '教材', 'https://example.com/img/cet4.jpg', 1),
(3, '小米台灯', '低价转让，正常使用', 35.00, 3, '生活', 'https://example.com/img/lamp1.jpg', 1);
