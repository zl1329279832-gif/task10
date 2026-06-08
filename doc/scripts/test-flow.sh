#!/bin/bash
# 校园二手交易系统 - 完整交易流程测试脚本
# 测试: 注册→登录→发布商品→下单→支付→发货→收货→查看结算

BASE_URL="${BASE_URL:-http://localhost:8080}"

echo "=========================================="
echo "  校园二手交易系统 - 交易流程集成测试"
echo "=========================================="

# 1. 注册卖家
echo ""
echo "[1] 注册卖家 seller_test"
SELLER_REG=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"seller_test","password":"pass123456","nickname":"测试卖家","role":"SELLER"}')
echo "  响应: ${SELLER_REG}"

# 2. 注册买家
echo ""
echo "[2] 注册买家 buyer_test"
BUYER_REG=$(curl -s -X POST "${BASE_URL}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"username":"buyer_test","password":"pass123456","nickname":"测试买家","role":"BUYER"}')
echo "  响应: ${BUYER_REG}"

# 3. 卖家登录
echo ""
echo "[3] 卖家登录"
SELLER_LOGIN=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"seller_test","password":"pass123456"}')
SELLER_TOKEN=$(echo "${SELLER_LOGIN}" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "  Token: ${SELLER_TOKEN:0:20}..."

# 4. 买家登录
echo ""
echo "[4] 买家登录"
BUYER_LOGIN=$(curl -s -X POST "${BASE_URL}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"buyer_test","password":"pass123456"}')
BUYER_TOKEN=$(echo "${BUYER_LOGIN}" | grep -o '"accessToken":"[^"]*"' | cut -d'"' -f4)
echo "  Token: ${BUYER_TOKEN:0:20}..."

# 5. 卖家发布商品
echo ""
echo "[5] 发布商品"
PRODUCT=$(curl -s -X POST "${BASE_URL}/api/products" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${SELLER_TOKEN}" \
    -d '{"title":"测试笔记本电脑","description":"九成新","price":3500.00,"stock":2,"category":"数码"}')
PRODUCT_ID=$(echo "${PRODUCT}" | grep -o '"productId":[0-9]*' | cut -d':' -f2)
echo "  商品ID: ${PRODUCT_ID}"

# 6. 买家下单
echo ""
echo "[6] 买家下单"
ORDER=$(curl -s -X POST "${BASE_URL}/api/orders" \
    -H "Content-Type: application/json" \
    -H "Authorization: Bearer ${BUYER_TOKEN}" \
    -H "X-Idempotent-Key: test-$(date +%s)" \
    -d "{\"productId\":${PRODUCT_ID},\"quantity\":1}")
ORDER_NO=$(echo "${ORDER}" | grep -o '"orderNo":"[^"]*"' | cut -d'"' -f4)
echo "  订单号: ${ORDER_NO}"

# 7. 模拟支付回调
echo ""
echo "[7] 模拟支付宝回调"
TRADE_NO="sandbox_test_$(date +%s)"
PAY_RESULT=$(curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
    -d "out_trade_no=${ORDER_NO}" \
    -d "trade_no=${TRADE_NO}" \
    -d "trade_status=TRADE_SUCCESS" \
    -d "total_amount=3500.00" \
    -d "sign=mock_sign_for_sandbox" \
    -d "sign_type=RSA2")
echo "  回调结果: ${PAY_RESULT}"

# 8. 卖家发货
echo ""
echo "[8] 卖家发货"
SHIP=$(curl -s -X POST "${BASE_URL}/api/orders/${ORDER_NO}/ship" \
    -H "Authorization: Bearer ${SELLER_TOKEN}")
echo "  响应: ${SHIP}"

# 9. 买家确认收货
echo ""
echo "[9] 买家确认收货"
RECEIVE=$(curl -s -X POST "${BASE_URL}/api/orders/${ORDER_NO}/receive" \
    -H "Authorization: Bearer ${BUYER_TOKEN}")
echo "  响应: ${RECEIVE}"

# 10. 查看订单详情
echo ""
echo "[10] 查看订单详情"
ORDER_DETAIL=$(curl -s -X GET "${BASE_URL}/api/orders/${ORDER_NO}" \
    -H "Authorization: Bearer ${BUYER_TOKEN}")
echo "  订单状态: $(echo ${ORDER_DETAIL} | grep -o '"status":"[^"]*"' | head -1)"

# 11. 查看卖家结算记录
echo ""
echo "[11] 查看卖家结算记录"
SETTLEMENTS=$(curl -s -X GET "${BASE_URL}/api/settlements/seller" \
    -H "Authorization: Bearer ${SELLER_TOKEN}")
echo "  结算记录: ${SETTLEMENTS}"

echo ""
echo "=========================================="
echo "  交易流程测试完成!"
echo "=========================================="
