INSERT INTO `t_role` (`id`, `role_code`, `role_name`, `description`) VALUES
(1, 'BUYER', 'Buyer', 'Can browse and purchase products'),
(2, 'SELLER', 'Seller', 'Can list products and manage orders'),
(3, 'ADMIN', 'Platform Admin', 'Full platform management access');

-- password: 123456 for all (BCrypt)
INSERT INTO `t_user` (`id`, `username`, `password`, `nickname`, `phone`, `status`) VALUES
(1, 'buyer01',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Alice',   '13800000001', 1),
(2, 'seller01', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Bob',     '13800000002', 1),
(3, 'admin01',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Admin',   '13800000003', 1),
(4, 'buyer02',  '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Charlie', '13800000004', 1),
(5, 'seller02', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'Diana',   '13800000005', 1);

INSERT INTO `t_user_role` (`user_id`, `role_id`) VALUES
(1,1),(2,2),(2,1),(3,3),(3,1),(3,2),(4,1),(5,2),(5,1);

INSERT INTO `t_product` (`id`, `seller_id`, `title`, `description`, `category`, `status`) VALUES
(1, 2, 'iPad Air 5 256G WiFi',   '9成新，无磕碰，带原装充电器和保护壳', '数码', 1),
(2, 2, '高等数学同济第七版上下册', '全新未拆封，多买了一套',             '教材', 1),
(3, 5, '罗技G502游戏鼠标',       '用了半年，手感很好，换了新鼠标出掉',  '数码', 1),
(4, 5, '考研英语真题+模拟套装',   '做过几套真题，模拟题全新',           '教材', 1),
(5, 2, '宜家台灯',               '毕业清仓，九成新',                   '生活', 1);

INSERT INTO `t_sku` (`id`, `product_id`, `sku_name`, `price`, `stock`, `lock_stock`) VALUES
(1, 1, '256G WiFi 星光色', 3200.00, 1, 0),
(2, 2, '上下册套装',       45.00,   5, 0),
(3, 3, '黑色 有线版',      180.00,  1, 0),
(4, 3, '黑色 无线版',      260.00,  1, 0),
(5, 4, '真题+模拟套装',    68.00,   3, 0),
(6, 5, '白色',             35.00,   2, 0);
