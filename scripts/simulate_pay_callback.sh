#!/bin/bash
set -e
BASE_URL="http://localhost:8080"
ORDER_NO="${1:?Usage: $0 <order_no> [trade_status]}"
TRADE_STATUS="${2:-TRADE_SUCCESS}"
TOTAL_AMOUNT="${3:-100.00}"
TRADE_NO="MOCK_$(date +%s%N)"
echo "=== Simulating Alipay Callback ==="
echo "Order: ${ORDER_NO}, Status: ${TRADE_STATUS}, Amount: ${TOTAL_AMOUNT}"
echo ""
echo "[1] Using simulation endpoint..."
curl -s -X POST "${BASE_URL}/api/v1/pay/simulate/${ORDER_NO}?tradeNo=${TRADE_NO}" -H "Content-Type: application/json"
echo ""
echo "[2] Notify endpoint (will fail without valid signature)..."
curl -s -X POST "${BASE_URL}/api/v1/pay/notify" \
  -d "out_trade_no=PAY_TEST&trade_no=${TRADE_NO}&trade_status=${TRADE_STATUS}&total_amount=${TOTAL_AMOUNT}&sign_type=RSA2&sign=mock"
echo ""
echo "Done. Check: curl ${BASE_URL}/api/v1/orders/no/${ORDER_NO}"
