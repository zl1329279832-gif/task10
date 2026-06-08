# 校园二手交易履约系统

基于 Spring Boot 的校园二手商品交易平台，实现从商品发布到交易结算的完整履约闭环。

## 技术栈

| 组件 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 运行环境 |
| Spring Boot | 3.2.5 | 应用框架 |
| Spring Security | 6.x | 认证授权 |
| JWT (jjwt) | 0.12.5 | Token认证 |
| MyBatis | 3.0.3 | ORM |
| MySQL | 8.0+ | 主数据库 |
| Redis | 6.0+ | 缓存/库存/分布式锁 |
| Druid | 1.2.21 | 连接池 |

## 核心功能

### 交易闭环
```
下单(锁库存) → 支付(支付宝沙箱) → 发货 → 确认收货 → 结算
         ↓                              ↓
     超时关闭(释放库存)            申请退款 → 卖家审批
                                          ↓
                                   争议 → 平台仲裁
```

### 订单状态机
```
PENDING_PAYMENT ──PAY──→ PAID ──SHIP──→ SHIPPED ──CONFIRM_RECEIVE──→ RECEIVED ──COMPLETE──→ COMPLETED
       │                   │               │                              │
   PAY_TIMEOUT         REQUEST_REFUND  REQUEST_REFUND              REQUEST_REFUND
       │                   │               │                              │
       ↓                   └───────────────┴──────────────────────────────┘
    CLOSED                                 ↓
                               REFUND_REQUESTED
                                ↙            ↘
                     REFUND_APPROVED    REFUND_REJECTED
                                              │
                                        OPEN_DISPUTE
                                              ↓
                                          DISPUTE
                                              │
                                          ARBITRATE
                                              ↓
                                         ARBITRATED
```

### 角色权限
| 角色 | 能力 |
|------|------|
| BUYER | 下单、支付、确认收货、申请退款、发起争议 |
| SELLER | 发布商品、发货、处理退款、提交举证 |
| ADMIN | 仲裁、审计日志、用户管理、强制关闭订单 |

### 幂等与并发控制
- **订单创建**: Header `X-Idempotent-Key` + Redis SET NX + DB UNIQUE 约束
- **支付回调**: `trade_no` UNIQUE 约束 + Redis 分布式锁 + 乐观锁版本号
- **状态流转**: 白名单状态机 + `UPDATE WHERE version = ?` 乐观锁
- **库存扣减**: Redis DECRBY 原子操作 + DB 乐观锁双层保障

### 支付回调防护
| 场景 | 处理策略 |
|------|----------|
| 正常回调 | 验签 → 幂等检查 → 状态机转换 → 更新订单+支付记录(事务) |
| 重复回调 | `trade_no` 查到已成功记录，直接返回 `success` |
| 乱序回调 | 订单已非 `PENDING_PAYMENT`，忽略并返回 `success` |
| 关闭后回调 | 记录日志 + 保存回调记录(CLOSED_IGNORED) + 返回 `success` |
| 签名错误 | 返回 `failure` |
| 金额不匹配 | 记录审计日志 + 返回 `failure` |

## 快速开始

### 前置条件
- JDK 17+
- MySQL 8.0+
- Redis 6.0+
- Maven 3.8+

### 1. 初始化数据库
```bash
mysql -u root -p < doc/sql/schema.sql
mysql -u root -p < doc/sql/test-data.sql
```

### 2. 修改配置
编辑 `src/main/resources/application.yml`，修改数据库和 Redis 连接信息。

### 3. 编译运行
```bash
mvn clean package -DskipTests
java -jar target/campus-trade-1.0.0.jar
```

### 4. 测试账号
| 用户名 | 密码 | 角色 |
|--------|------|------|
| admin | password123 | ADMIN |
| buyer1 | password123 | BUYER |
| seller1 | password123 | SELLER, BUYER |

### 5. 运行测试
```bash
# 单元测试
mvn test

# 完整交易流程测试
bash doc/scripts/test-flow.sh

# 支付回调模拟
bash doc/scripts/pay-callback-mock.sh <订单号> success
bash doc/scripts/pay-callback-mock.sh <订单号> duplicate
bash doc/scripts/pay-callback-mock.sh <订单号> closed
bash doc/scripts/pay-callback-mock.sh <订单号> invalid_sign
```

## API 接口概览

### 认证 `/api/auth`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /register | 注册 |
| POST | /login | 登录(返回JWT) |
| POST | /refresh | 刷新Token |
| POST | /logout | 登出(Token加黑名单) |
| POST | /role/switch | 添加角色 |

### 商品 `/api/products`
| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | / | SELLER | 发布商品 |
| PUT | /{id} | SELLER | 更新商品 |
| PUT | /{id}/status | SELLER | 上下架 |
| GET | /{id} | 公开 | 商品详情 |
| GET | / | 公开 | 商品列表 |
| GET | /mine | SELLER | 我的商品 |
| DELETE | /{id} | SELLER/ADMIN | 删除商品 |

### 订单 `/api/orders`
| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | / | BUYER | 创建订单 |
| GET | /{orderNo} | 当事人 | 订单详情 |
| GET | /buyer | BUYER | 买家订单 |
| GET | /seller | SELLER | 卖家订单 |
| POST | /{orderNo}/cancel | BUYER | 取消订单 |
| POST | /{orderNo}/ship | SELLER | 发货 |
| POST | /{orderNo}/receive | BUYER | 确认收货 |

### 支付 `/api/payment`
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /pay/{orderNo} | 发起支付 |
| POST | /callback/alipay | 支付宝异步回调 |
| GET | /return/alipay | 支付宝同步返回 |
| GET | /status/{orderNo} | 查询支付状态 |

### 退款 `/api/refunds`
| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | / | BUYER | 申请退款 |
| GET | /{refundNo} | 当事人 | 退款详情 |
| POST | /{refundNo}/approve | SELLER | 同意退款 |
| POST | /{refundNo}/reject | SELLER | 拒绝退款 |
| GET | /order/{orderNo} | 当事人 | 订单退款记录 |

### 争议 `/api/disputes`
| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| POST | / | BUYER | 发起争议 |
| GET | /{disputeNo} | 当事人/ADMIN | 争议详情 |
| POST | /{disputeNo}/evidence | 当事人 | 提交举证 |
| POST | /{disputeNo}/arbitrate | ADMIN | 仲裁 |
| GET | / | ADMIN | 争议列表 |

### 结算 `/api/settlements`
| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | /seller | SELLER | 我的结算 |
| GET | /{settlementNo} | SELLER/ADMIN | 结算详情 |
| GET | / | ADMIN | 全部结算 |

### 管理 `/api/admin`
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /orders | 全部订单 |
| POST | /orders/{orderNo}/close | 强制关闭订单 |
| GET | /audit-logs | 审计日志查询 |

## 项目结构
```
campus-trade/
├── pom.xml
├── doc/
│   ├── sql/
│   │   ├── schema.sql          # 建表脚本(10张表)
│   │   └── test-data.sql       # 测试数据
│   └── scripts/
│       ├── pay-callback-mock.sh # 支付回调模拟
│       └── test-flow.sh         # 完整流程测试
├── src/main/java/com/campus/trade/
│   ├── CampusTradeApplication.java
│   ├── annotation/              # 自定义注解(@AuditLog, @Idempotent)
│   ├── aspect/                  # AOP切面(审计日志, 幂等)
│   ├── common/                  # 统一返回(Result, ResultCode, BusinessException)
│   ├── config/                  # 配置(Security, Redis, Alipay, WebMvc)
│   ├── controller/              # REST控制器(9个)
│   ├── handler/                 # 全局异常处理
│   ├── mapper/                  # MyBatis Mapper接口(10个)
│   ├── model/
│   │   ├── entity/             # 实体类(10个)
│   │   ├── dto/                # 请求/响应DTO
│   │   └── enums/              # 枚举(订单状态, 角色等)
│   ├── scheduler/              # 定时任务(超时订单扫描)
│   ├── security/               # JWT认证(TokenProvider, Filter)
│   ├── service/                # 业务服务(10个接口+实现)
│   ├── statemachine/           # 订单状态机
│   └── util/                   # 工具类(订单号生成, Redis Key, 支付宝)
├── src/main/resources/
│   ├── application.yml
│   ├── application-dev.yml
│   ├── application-test.yml
│   └── mapper/                 # MyBatis XML(10个)
└── src/test/java/              # 单元测试(状态机, 库存, 支付, 订单, 退款)
```

## 数据库表
| 表名 | 说明 |
|------|------|
| t_user | 用户 |
| t_user_role | 用户角色 |
| t_product | 商品 |
| t_order | 订单 |
| t_payment_record | 支付记录 |
| t_refund_record | 退款记录 |
| t_dispute | 争议仲裁 |
| t_settlement | 结算记录 |
| t_audit_log | 审计日志 |
| t_idempotent_key | 幂等键 |
