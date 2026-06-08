#!/bin/bash
set -e
BASE="http://localhost:8080"
echo "=== E2E Test Flow ==="
echo "[1] Login buyer..."
BT=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"buyer01","password":"123456"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "[1] Login seller..."
ST=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"seller01","password":"123456"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "[1] Login admin..."
AT=$(curl -s -X POST "$BASE/api/v1/auth/login" -H "Content-Type: application/json" -d '{"username":"admin01","password":"123456"}' | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo "[2] List products..." && curl -s "$BASE/api/v1/products?status=1" | python3 -m json.tool
echo "[3] Create order..." && OR=$(curl -s -X POST "$BASE/api/v1/orders" -H "Authorization: Bearer $BT" -H "Content-Type: application/json" -d '{"skuId":2,"quantity":1,"address":"Dorm 5-301"}')
echo "$OR" | python3 -m json.tool
ONO=$(echo "$OR" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['orderNo'])")
OID=$(echo "$OR" | python3 -c "import sys,json; print(json.load(sys.stdin)['data']['id'])")
echo "[4] Simulate payment..." && curl -s -X POST "$BASE/api/v1/pay/simulate/$ONO" | python3 -m json.tool
echo "[5] Check status..." && curl -s "$BASE/api/v1/orders/$OID" -H "Authorization: Bearer $BT" | python3 -c "import sys,json; d=json.load(sys.stdin)['data']; print(f'  Status: {d[\"status\"]}')"
echo "[6] Ship..." && curl -s -X POST "$BASE/api/v1/orders/ship" -H "Authorization: Bearer $ST" -H "Content-Type: application/json" -d "{\"orderId\":$OID,\"logisticsNo\":\"SF123\",\"logisticsCompany\":\"SF Express\"}" | python3 -m json.tool
echo "[7] Confirm receipt..." && curl -s -X POST "$BASE/api/v1/orders/$OID/receive" -H "Authorization: Bearer $BT" | python3 -m json.tool
echo "[8] Create settlement..." && curl -s -X POST "$BASE/api/v1/settlements/order/$OID" -H "Authorization: Bearer $AT" | python3 -m json.tool
echo "[9] Status log..." && curl -s "$BASE/api/v1/admin/orders/$OID/status-log" -H "Authorization: Bearer $AT" | python3 -m json.tool
echo "=== E2E Complete ==="
