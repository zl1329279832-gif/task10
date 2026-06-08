# Campus Trade - 校园二手交易履约系统

一套基于 Spring Boot 的校园二手交易全链路履约系统，覆盖从商品发布、下单锁库存、支付、发货、确认收货、退款、争议举证、平台仲裁到结算与审计的完整交易闭环。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| 安全 | Spring Security + JWT (jjwt 0.12.5) |
| ORM | MyBatis 3.0.3 |
| 数据库 | MySQL 8.x (utf8mb4) |
| 连接池 | Druid 1.2.21 |
| 缓存/锁 | Redis + Lettuce |
| 支付 | 支付宝沙箱 SDK 4.39.79 |
| API文档 | SpringDoc OpenAPI 2.5.0 |
| 工具库 | Hutool 5.8.27 |
| 构建 | Maven |

---

## 项目结构

```
src/main/java/com/campus/trade/
├── CampusTradeApplication.java          # 启动类
├── common/
│   ├── annotation/                      # 自定义注解 (@Idempotent, @RequireRole)
│   ├── aspect/                          # AOP 切面 (幂等切面)
│   ├── config/                          # 配置类 (Security, Redis, Alipay, Trade)
│   ├── exception/                       # 异常体系 (BizException, ErrorCode, GlobalHandler)
│   ├── filter/                          # 过滤器 (JWT认证过滤器)
│   ├── result/                          # 统一响应 (Result, PageResult)
│   └── util/                            # 工具类 (JWT, 分布式锁, 业务单号生成器)
├── controller/                          # 9个控制器 (Auth, Product, Order, Payment, Refund, Dispute, Arbitration, Settlement, Admin)
├── domain/
│   ├── entity/                          # 15个实体类
│   └── enums/                           # 7个枚举 (OrderStatus, OrderStateTransition, PaymentStatus...)
├── dto/
│   ├── request/                         # 请求DTO
│   └── response/                        # 响应DTO
├── mapper/                              # 15个Mapper接口
└── service/
    ├── impl/                            # 10个Service实现
    └── (interfaces)                     # 10个Service接口

src/main/resources/
├── application.yml                      # 主配置
├── application-dev.yml                  # 开发环境配置
├── mapper/                              # 14个MyBatis XML映射文件
└── sql/
    ├── schema.sql                       # 建表DDL (15张表)
    └── data.sql                         # 测试数据

scripts/
├── simulate_pay_callback.sh             # 模拟支付宝回调脚本
└── e2e_test_flow.sh                     # 端到端交易流程测试脚本

src/test/java/com/campus/trade/
├── service/
│   ├── OrderServiceTest.java            # 订单服务单元测试
│   └── InventoryServiceTest.java        # 库存服务单元测试
└── integration/
    └── OrderTransitionTest.java         # 订单状态流转集成测试
```

---

## 订单状态机

系统核心是一个编译期约束的订单状态机，通过 `OrderStateTransition` 枚举定义所有合法的状态迁移路径：

```
                    ┌─────────────┐
                    │   CREATED   │  (下单，锁库存)
                    └──────┬──────┘
                     ┌─────┴─────┐
                     │           │
                     ▼           ▼
               ┌───────┐   ┌──────────┐
               │ PAID  │   │CANCELLED │  (超时/手动取消，释放库存)
               └───┬───┘   └──────────┘
                   │
            ┌──────┼──────────┐
            │      │          │
            ▼      ▼          ▼
     ┌─────────┐ ┌────────┐ ┌──────────┐
     │SHIPPED  │ │REFUNDING│ │DISPUTED │
     └────┬────┘ └───┬────┘ └────┬─────┘
          │      ┌───┴───┐       │
          │      │       │       ▼
          │      ▼       ▼   ┌──────────┐
          │  ┌────────┐ ┌──┐ │ARBITRATION│
          │  │REFUNDED│ │PAID│ └─────┬────┘
          │  └────────┘ └──┘    ┌────┴────┐
          │                     ▼         ▼
          │               ┌────────┐ ┌────────┐
          │               │REFUNDED│ │  PAID  │
          │               └────────┘ └────────┘
          ▼
     ┌──────────┐
     │RECEIVED  │  (买家确认收货 / 7天自动确认)
     └────┬─────┘
          ▼
     ┌──────────┐
     │ SETTLED  │  (结算完成，扣除平台手续费)
     └──────────┘
```

**关键设计**: `OrderStateTransition` 使用 `EnumSet` 在编译期约束合法迁移，运行时 `transitionOrder()` 方法校验迁移合法性，配合乐观锁 (`WHERE status=#{fromStatus}`) 和 Redis 分布式锁防止并发冲坏状态。

---

## 核心策略

### 库存管理

采用 **下单锁库存** 策略，`t_sku` 表有 `stock`（总库存）和 `lock_stock`（已锁定库存）两列：

- **锁库存** (下单时): `UPDATE t_sku SET lock_stock = lock_stock + #{qty} WHERE id=#{skuId} AND (stock - lock_stock) >= #{qty}`
- **释放库存** (取消/超时): `UPDATE t_sku SET lock_stock = lock_stock - #{qty} WHERE id=#{skuId} AND lock_stock >= #{qty}`
- **扣减库存** (支付成功): `UPDATE t_sku SET stock = stock - #{qty}, lock_stock = lock_stock - #{qty} WHERE id=#{skuId} AND stock >= #{qty} AND lock_stock >= #{qty}`

所有操作均为原子 SQL，天然防超卖。

### 支付集成 (支付宝沙箱)

1. **创建支付**: 调用 `alipay.trade.page.pay` 生成支付表单
2. **回调验签**: `AlipaySignature.rsaCheckV1()` 校验支付宝签名
3. **幂等处理**: 先通过乐观锁 `UPDATE t_payment SET status='SUCCESS' WHERE status='PENDING'` 判断是否已处理
4. **乱序保护**: 处理前检查订单是否仍为 CREATED 状态，非 CREATED 则跳过
5. **关单保护**: 订单已关闭 (CLOSED/CANCELLED) 时回调成功，返回 "success" 停止支付宝重试
6. **金额校验**: 回调金额与订单金额不一致时记录审计日志并告警

### 幂等策略 (双层防护)

**第一层 - 注解 + AOP**:
```java
@Idempotent(key = "#request.skuId + ':' + #request.quantity", bizType = "CREATE_ORDER")
public Result<OrderResponse> createOrder(@RequestBody CreateOrderRequest request) { ... }
```
`IdempotentAspect` 通过 SpEL 解析 key，在 `t_idempotent` 表 INSERT 记录后才执行业务逻辑。

**第二层 - 数据库唯一约束**:
`t_idempotent` 表的 `UNIQUE KEY (biz_type, idempotent_key)` 兜底，即使 AOP 层并发穿透，数据库层也能拦截重复请求。

### 分布式锁

基于 Redis SETNX 实现，`trade:lock:{orderNo}` 为 key，10 秒自动过期防死锁。订单状态变更前必须获取分布式锁，防止同一订单的并发操作。

### 超时关单

`@Scheduled(fixedDelay = 60_000)` 定时扫描 `pay_expire_at < NOW()` 且状态为 CREATED 的订单，自动关闭并释放库存。超时时间默认 30 分钟（可配置）。

---

## 权限模型

| 角色 | 权限范围 |
|------|---------|
| **BUYER** (买家) | 浏览商品、下单、取消订单、确认收货、申请退款、发起争议、提交举证 |
| **SELLER** (卖家) | 发布商品、查看自己的订单、发货、审批/拒绝退款 |
| **ADMIN** (管理员) | 查看审计日志、查看状态流转记录、查看所有争议、执行仲裁 |

通过 Spring Security 的 `@PreAuthorize` 注解实现方法级权限控制：
```java
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@PostMapping("/arbitration")
public Result<?> arbitrate(@RequestBody ArbitrationRequest request) { ... }
```

---

## 数据库表结构 (15张表)

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `t_user` | 用户表 | username, password(BCrypt), status |
| `t_role` | 角色表 | role_code (BUYER/SELLER/ADMIN) |
| `t_user_role` | 用户-角色映射 | user_id, role_id |
| `t_product` | 商品表 | seller_id, title, status (on-sale/off-shelf/sold-out) |
| `t_sku` | SKU/库存表 | price, stock, lock_stock |
| `t_order` | 订单表 | order_no, status, pay_expire_at, logistics_no |
| `t_order_status_log` | 订单状态变更审计 | from_status, to_status, operator_id |
| `t_payment` | 支付记录 | payment_no, trade_no, callback_content |
| `t_refund` | 退款申请 | refund_amount, reason, status, reject_reason |
| `t_dispute` | 争议记录 | dispute_no, initiator_id, respondent_id |
| `t_dispute_evidence` | 争议举证 | content, image_urls |
| `t_arbitration` | 仲裁记录 | result (BUYER_WIN/SELLER_WIN/PARTIAL), decision |
| `t_settlement` | 结算记录 | order_amount, platform_fee, settle_amount |
| `t_audit_log` | 操作审计日志 | module, action, target_type, target_id, ip |
| `t_idempotent` | 幂等键存储 | biz_type + idempotent_key (UNIQUE) |

---

## 快速启动

### 环境要求
- JDK 17+
- MySQL 8.x
- Redis 6+
- Maven 3.8+

### 1. 初始化数据库
```sql
CREATE DATABASE campus_trade DEFAULT CHARACTER SET utf8mb4;
USE campus_trade;
SOURCE src/main/resources/sql/schema.sql;
SOURCE src/main/resources/sql/data.sql;
```

### 2. 配置环境
编辑 `src/main/resources/application-dev.yml`：
- 修改数据库连接信息 (username/password)
- 确认 Redis 连接地址
- 配置支付宝沙箱密钥 (或使用模拟支付接口)

### 3. 启动
```bash
mvn spring-boot:run
```
服务启动在 http://localhost:8080

### 4. API 文档
访问 http://localhost:8080/swagger-ui.html

---

## API 概览

### 认证
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/auth/login` | 登录 |
| POST | `/api/v1/auth/register` | 注册 |
| POST | `/api/v1/auth/refresh` | 刷新Token |

### 商品
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/products` | 商品列表 (分页) |
| GET | `/api/v1/products/{id}` | 商品详情 |
| POST | `/api/v1/products` | 发布商品 (SELLER) |

### 订单
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/orders` | 创建订单 (BUYER) |
| GET | `/api/v1/orders/buyer` | 我的订单-买家 (BUYER) |
| GET | `/api/v1/orders/seller` | 我的订单-卖家 (SELLER) |
| POST | `/api/v1/orders/{orderNo}/cancel` | 取消订单 (BUYER) |
| POST | `/api/v1/orders/{orderNo}/ship` | 发货 (SELLER) |
| POST | `/api/v1/orders/{orderNo}/receive` | 确认收货 (BUYER) |

### 支付
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/pay/{orderNo}` | 创建支付 (BUYER) |
| POST | `/api/v1/pay/notify` | 支付宝回调 (无需认证) |
| POST | `/api/v1/pay/simulate/{orderNo}` | 模拟支付成功 (测试用) |

### 退款
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/refunds` | 申请退款 (BUYER) |
| POST | `/api/v1/refunds/{id}/approve` | 同意退款 (SELLER) |
| POST | `/api/v1/refunds/{id}/reject` | 拒绝退款 (SELLER) |

### 争议
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/disputes` | 发起争议 (BUYER/SELLER) |
| POST | `/api/v1/disputes/{id}/evidence` | 提交举证 |
| POST | `/api/v1/disputes/{id}/escalate` | 升级为仲裁 |

### 仲裁 (ADMIN)
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/admin/arbitration` | 执行仲裁 |

### 结算
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/v1/settlements/order/{orderId}` | 创建结算记录 |
| POST | `/api/v1/settlements/{id}/execute` | 执行结算 |

### 管理后台 (ADMIN)
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/v1/admin/audit-logs` | 审计日志 |
| GET | `/api/v1/admin/orders/{id}/status-log` | 订单状态变更记录 |
| GET | `/api/v1/admin/disputes` | 所有争议列表 |

---

## 测试数据

| 用户名 | 密码 | 角色 | 说明 |
|--------|------|------|------|
| buyer01 | 123456 | BUYER | 买家 Alice |
| seller01 | 123456 | SELLER, BUYER | 卖家 Bob (也可购买) |
| admin01 | 123456 | ADMIN, BUYER, SELLER | 平台管理员 |
| buyer02 | 123456 | BUYER | 买家 Charlie |
| seller02 | 123456 | SELLER, BUYER | 卖家 Diana |

预置 5 个商品、6 个 SKU，涵盖数码、教材、生活三个品类。

---

## 错误码体系

| 模块 | 范围 | 示例 |
|------|------|------|
| 认证 | 1001-1004 | 1001=用户名已存在, 1002=密码错误, 1003=未认证, 1004=Token过期 |
| 商品 | 2001-2005 | 2001=商品不存在, 2002=商品已下架, 2003=无权限操作 |
| 订单 | 3001-3008 | 3001=订单不存在, 3002=状态不允许, 3003=库存不足, 3006=不能购买自己的商品 |
| 支付 | 4001-4005 | 4001=支付记录不存在, 4002=签名验证失败, 4003=金额不匹配 |
| 退款 | 5001-5004 | 5001=退款不存在, 5002=状态不允许 |
| 争议 | 6001-6004 | 6001=争议不存在, 6002=非争议参与方 |
| 仲裁 | 7001 | 争议未处于OPEN状态 |
| 结算 | 8001 | 订单状态不允许结算 |
| 幂等 | 9001-9002 | 9001=重复请求 |

统一响应格式：
```json
{
  "code": 0,
  "message": "success",
  "data": { ... }
}
```
错误时：
```json
{
  "code": 3002,
  "message": "订单状态不允许此操作: 当前状态=SHIPPED, 期望状态=CREATED",
  "data": null
}
```

---

## 端到端测试

```bash
# 赋予脚本执行权限
chmod +x scripts/*.sh

# 模拟支付回调
./scripts/simulate_pay_callback.sh <orderNo>

# 完整交易流程 (登录→下单→支付→发货→收货→结算→审计)
./scripts/e2e_test_flow.sh
```

---

## 单元测试

```bash
# 运行所有测试
mvn test

# 运行订单服务测试
mvn test -Dtest=OrderServiceTest

# 运行库存服务测试
mvn test -Dtest=InventoryServiceTest
```

---

## 配置说明

### 业务参数 (application-dev.yml)
```yaml
trade:
  order:
    pay-timeout-minutes: 30      # 支付超时时间
    platform-fee-rate: 0.02      # 平台手续费率 2%
    auto-receive-days: 7         # 自动确认收货天数
```

### 支付宝沙箱
系统支持两种支付模式：
1. **真实沙箱**: 配置 `alipay.*` 参数，调用支付宝 SDK
2. **模拟支付**: 调用 `/api/v1/pay/simulate/{orderNo}` 接口，跳过支付宝直接完成支付（开发测试推荐）
