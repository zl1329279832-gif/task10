#!/bin/bash
# 校园二手交易系统 - 支付宝沙箱回调模拟脚本
# 用法:
#   ./pay-callback-mock.sh <orderNo> [场景]
#   场景: success(默认) | duplicate | closed | wrong_amount | invalid_sign

BASE_URL="${BASE_URL:-http://localhost:8080}"
ORDER_NO="${1:?请提供订单号}"
SCENARIO="${2:-success}"
TRADE_NO="sandbox_$(date +%s%N | head -c 17)"
AMOUNT="${3:-99.00}"

echo "========================================"
echo "  支付回调模拟"
echo "  订单号: ${ORDER_NO}"
echo "  场景:   ${SCENARIO}"
echo "  交易号: ${TRADE_NO}"
echo "  金额:   ${AMOUNT}"
echo "========================================"

case "${SCENARIO}" in
    success)
        echo "[1] 正常支付成功回调"
        curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
            -d "out_trade_no=${ORDER_NO}" \
            -d "trade_no=${TRADE_NO}" \
            -d "trade_status=TRADE_SUCCESS" \
            -d "total_amount=${AMOUNT}" \
            -d "sign=mock_sign_for_sandbox" \
            -d "sign_type=RSA2"
        echo ""
        ;;

    duplicate)
        echo "[1] 第一次回调"
        curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
            -d "out_trade_no=${ORDER_NO}" \
            -d "trade_no=${TRADE_NO}" \
            -d "trade_status=TRADE_SUCCESS" \
            -d "total_amount=${AMOUNT}" \
            -d "sign=mock_sign_for_sandbox" \
            -d "sign_type=RSA2"
        echo ""

        echo "[2] 重复回调(相同trade_no) - 应该幂等返回success"
        sleep 1
        curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
            -d "out_trade_no=${ORDER_NO}" \
            -d "trade_no=${TRADE_NO}" \
            -d "trade_status=TRADE_SUCCESS" \
            -d "total_amount=${AMOUNT}" \
            -d "sign=mock_sign_for_sandbox" \
            -d "sign_type=RSA2"
        echo ""
        ;;

    closed)
        echo "[1] 模拟订单已关闭后收到支付成功回调"
        echo "    (请确保订单 ${ORDER_NO} 已被关闭/超时)"
        curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
            -d "out_trade_no=${ORDER_NO}" \
            -d "trade_no=${TRADE_NO}" \
            -d "trade_status=TRADE_SUCCESS" \
            -d "total_amount=${AMOUNT}" \
            -d "sign=mock_sign_for_sandbox" \
            -d "sign_type=RSA2"
        echo ""
        ;;

    wrong_amount)
        echo "[1] 金额不匹配回调"
        curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
            -d "out_trade_no=${ORDER_NO}" \
            -d "trade_no=${TRADE_NO}" \
            -d "trade_status=TRADE_SUCCESS" \
            -d "total_amount=0.01" \
            -d "sign=mock_sign_for_sandbox" \
            -d "sign_type=RSA2"
        echo ""
        ;;

    invalid_sign)
        echo "[1] 签名错误回调 - 应该返回failure"
        curl -s -X POST "${BASE_URL}/api/payment/callback/alipay" \
            -d "out_trade_no=${ORDER_NO}" \
            -d "trade_no=${TRADE_NO}" \
            -d "trade_status=TRADE_SUCCESS" \
            -d "total_amount=${AMOUNT}" \
            -d "sign=invalid_sign_value" \
            -d "sign_type=RSA2"
        echo ""
        ;;

    *)
        echo "未知场景: ${SCENARIO}"
        echo "支持: success | duplicate | closed | wrong_amount | invalid_sign"
        exit 1
        ;;
esac

echo ""
echo "======== 完成 ========"
