#!/usr/bin/env bash

set -u
set -o pipefail

ROOT_DIR=$(cd "$(dirname "$0")/.." && pwd)
GATEWAY_URL="http://127.0.0.1:8080"
USER_URL="http://127.0.0.1:8081"
ORDER_URL="http://127.0.0.1:8082"
STARTUP_TIMEOUT=120
INITIAL_POINT=10000
E2E_SECRET="msa-e2e-only-shared-secret-$(date +%s)-$$-do-not-use-in-production"
TMP_DIR=$(mktemp -d "${TMPDIR:-/tmp}/shopping-msa-e2e.XXXXXX")
USER_LOG="$TMP_DIR/user-service.log"
ORDER_LOG="$TMP_DIR/order-service.log"
GATEWAY_LOG="$TMP_DIR/gateway-service.log"
USER_PID=""
ORDER_PID=""
GATEWAY_PID=""
TEST_PASSED=0
RESPONSE_BODY="$TMP_DIR/response.json"
HTTP_STATUS=""
LAST_REQUEST_METHOD="N/A"
LAST_REQUEST_URL="N/A"

log_tail() {
  local label=$1 file=$2
  echo "----- $label 로그 마지막 80줄 -----" >&2
  if [ -f "$file" ]; then tail -n 80 "$file" >&2; else echo "로그 파일 없음" >&2; fi
}

terminate_tree() {
  local pid=$1 child
  [ -n "$pid" ] || return 0
  kill -0 "$pid" 2>/dev/null || return 0
  for child in $(pgrep -P "$pid" 2>/dev/null || true); do terminate_tree "$child"; done
  kill "$pid" 2>/dev/null || true
}

cleanup() {
  terminate_tree "$GATEWAY_PID"
  terminate_tree "$ORDER_PID"
  terminate_tree "$USER_PID"
  sleep 1
  terminate_tree "$GATEWAY_PID"
  terminate_tree "$ORDER_PID"
  terminate_tree "$USER_PID"
  if [ "$TEST_PASSED" -eq 1 ]; then
    rm -rf "$TMP_DIR"
  else
    echo "E2E 실패 로그: $TMP_DIR" >&2
    log_tail "user-service" "$USER_LOG"
    log_tail "order-service" "$ORDER_LOG"
    log_tail "gateway-service" "$GATEWAY_LOG"
  fi
}
trap cleanup EXIT INT TERM

fail() {
  local step=$1 expected=$2 actual=${3:-"HTTP $HTTP_STATUS"}
  echo "E2E TEST FAILED: $step" >&2
  echo "요청: $LAST_REQUEST_METHOD $LAST_REQUEST_URL" >&2
  echo "기대값: $expected" >&2
  echo "실제: $actual" >&2
  echo "실제 HTTP 상태: ${HTTP_STATUS:-N/A}" >&2
  echo "실제 응답 Body:" >&2
  if [ -s "$RESPONSE_BODY" ]; then cat "$RESPONSE_BODY" >&2; else echo "<empty>" >&2; fi
  echo >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || { echo "필수 도구 '$1'이 없습니다. 설치 후 다시 실행하세요." >&2; exit 1; }
}

for cmd in bash curl java; do require_command "$cmd"; done
if command -v jq >/dev/null 2>&1; then JSON_TOOL=jq
elif command -v python3 >/dev/null 2>&1; then JSON_TOOL=python3
else
  echo "JSON 파싱 도구가 없습니다. jq 또는 Python 3를 설치한 뒤 다시 실행하세요." >&2
  exit 1
fi

[ -x "$ROOT_DIR/user-service/gradlew" ] || { echo "user-service/gradlew를 실행할 수 없습니다." >&2; exit 1; }
[ -x "$ROOT_DIR/order-service/gradlew" ] || { echo "order-service/gradlew를 실행할 수 없습니다." >&2; exit 1; }
[ -x "$ROOT_DIR/gateway-service/gradlew" ] || { echo "gateway-service/gradlew를 실행할 수 없습니다." >&2; exit 1; }

port_in_use() {
  local port=$1 rc
  curl --silent --show-error --output /dev/null --connect-timeout 1 --max-time 2 "http://127.0.0.1:$port/" 2>/dev/null
  rc=$?
  [ "$rc" -ne 7 ]
}

for port in 8080 8081 8082; do
  if port_in_use "$port"; then
    echo "포트 $port가 이미 사용 중입니다. 해당 프로세스를 종료한 뒤 다시 실행하세요." >&2
    exit 1
  fi
done

json_get() {
  local file=$1 path=$2
  if [ "$JSON_TOOL" = jq ]; then
    jq -r --arg path "$path" \
      'getpath($path | split(".") | map(if test("^[0-9]+$") then tonumber else . end)) // empty' "$file"
  else
    python3 - "$file" "$path" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    value = json.load(f)
for part in sys.argv[2].split('.'):
    value = value[int(part)] if isinstance(value, list) else value.get(part)
if value is not None:
    print(str(value).lower() if isinstance(value, bool) else value)
PY
  fi
}

item_field() {
  local file=$1 product_id=$2 field=$3
  if [ "$JSON_TOOL" = jq ]; then
    jq -r --argjson id "$product_id" ".items[] | select(.productId == \$id) | .$field" "$file"
  else
    python3 - "$file" "$product_id" "$field" <<'PY'
import json, sys
with open(sys.argv[1], encoding="utf-8") as f:
    data = json.load(f)
for item in data.get("items", []):
    if str(item.get("productId")) == sys.argv[2]:
        print(item.get(sys.argv[3], "")); break
PY
  fi
}

request() {
  local method=$1 url=$2 body=${3:-} token=${4:-}
  LAST_REQUEST_METHOD=$method
  LAST_REQUEST_URL=$url
  local args=(--silent --show-error --connect-timeout 3 --max-time 15 --output "$RESPONSE_BODY" --write-out "%{http_code}" --request "$method" "$url")
  [ -z "$body" ] || args+=(--header "Content-Type: application/json" --data "$body")
  [ -z "$token" ] || args+=(--header "Authorization: Bearer $token")
  HTTP_STATUS=$(curl "${args[@]}") || fail "HTTP 요청: $method $url" "요청 성공" "curl 실행 실패"
}

assert_status() { [ "$HTTP_STATUS" = "$2" ] || fail "$1" "HTTP $2" "HTTP $HTTP_STATUS"; }
values_equal() {
  local actual=$1 expected=$2
  if [[ "$actual" =~ ^-?[0-9]+([.][0-9]+)?$ ]] && [[ "$expected" =~ ^-?[0-9]+([.][0-9]+)?$ ]]; then
    awk -v actual="$actual" -v expected="$expected" 'BEGIN { exit !(actual + 0 == expected + 0) }'
  else
    [ "$actual" = "$expected" ]
  fi
}
assert_field() {
  local step=$1 file=$2 path=$3 expected=$4 actual
  actual=$(json_get "$file" "$path") || fail "$step" "$path=$expected" "JSON 파싱 실패"
  values_equal "$actual" "$expected" || fail "$step" "$path=$expected" "$path=$actual"
}
assert_item() {
  local step=$1 file=$2 product=$3 field=$4 expected=$5 actual
  actual=$(item_field "$file" "$product" "$field") || fail "$step" "productId=${product}의 $field=$expected" "JSON 파싱 실패"
  values_equal "$actual" "$expected" || fail "$step" "productId=${product}의 $field=$expected" "$field=$actual"
}

wait_ready() {
  local name=$1 url=$2 pid=$3 elapsed=0 status
  while [ "$elapsed" -lt "$STARTUP_TIMEOUT" ]; do
    kill -0 "$pid" 2>/dev/null || fail "$name 실행" "프로세스가 준비 상태까지 실행" "프로세스 조기 종료"
    status=$(curl --silent --output /dev/null --connect-timeout 1 --max-time 2 --write-out "%{http_code}" "$url" 2>/dev/null || true)
    [ "$status" = 200 ] && return 0
    sleep 1
    elapsed=$((elapsed + 1))
  done
  fail "$name 준비 대기" "${STARTUP_TIMEOUT}초 안에 HTTP 200" "timeout"
}

(cd "$ROOT_DIR/user-service" && JWT_SECRET="$E2E_SECRET" ./gradlew bootRun) >"$USER_LOG" 2>&1 &
USER_PID=$!
echo "$USER_PID" >"$TMP_DIR/user-service.pid"
wait_ready "user-service" "$USER_URL/api/customers?page=0&size=1" "$USER_PID"

(cd "$ROOT_DIR/order-service" && \
  JWT_SECRET="$E2E_SECRET" \
  USER_SERVICE_BASE_URL="http://localhost:8081" \
  ./gradlew bootRun) >"$ORDER_LOG" 2>&1 &
ORDER_PID=$!
echo "$ORDER_PID" >"$TMP_DIR/order-service.pid"
wait_ready "order-service" "$ORDER_URL/api/products?page=0&size=1" "$ORDER_PID"

(cd "$ROOT_DIR/gateway-service" && \
  USER_SERVICE_URL="http://localhost:8081" \
  ORDER_SERVICE_URL="http://localhost:8082" \
  ./gradlew bootRun) >"$GATEWAY_LOG" 2>&1 &
GATEWAY_PID=$!
echo "$GATEWAY_PID" >"$TMP_DIR/gateway-service.pid"
wait_ready "gateway-service" "$GATEWAY_URL/actuator/health" "$GATEWAY_PID"
request GET "$GATEWAY_URL/actuator/health"
assert_status "Gateway health" 200
assert_field "Gateway health" "$RESPONSE_BODY" status UP
echo "[1/11] 세 서비스 실행 및 Gateway health 확인"

RUN_ID="$(date +%s)-$$"
LOGIN_A="e2e-a-$RUN_ID"
LOGIN_B="e2e-b-$RUN_ID"
PASSWORD="E2e-pass-$RUN_ID"

request POST "$GATEWAY_URL/api/customers" "{\"loginId\":\"$LOGIN_A\",\"password\":\"$PASSWORD\",\"name\":\"E2E Customer A\"}"
assert_status "고객 A 회원가입" 201
assert_field "고객 A 초기 포인트" "$RESPONSE_BODY" point "$INITIAL_POINT"
CUSTOMER_A=$(json_get "$RESPONSE_BODY" id)
[ -n "$CUSTOMER_A" ] || fail "고객 A 회원가입" "비어 있지 않은 id" "id가 비어 있음"

request GET "$GATEWAY_URL/internal/customers/$CUSTOMER_A"
assert_status "Gateway 내부 고객 API 미노출" 404
request POST "$GATEWAY_URL/internal/customers/$CUSTOMER_A/points/refund" \
  "{\"amount\":1,\"requestId\":\"gateway-route-check-$RUN_ID\"}"
assert_status "Gateway 내부 포인트 API 미노출" 404
# /internal/**는 Gateway Route가 없으므로, 포인트 불변 여부만 테스트 검증 목적으로 user-service에 직접 확인한다.
request GET "$USER_URL/internal/customers/$CUSTOMER_A"
assert_status "Gateway 내부 API 미노출 후 고객 직접 확인" 200
assert_field "Gateway 내부 API 미노출 후 포인트 불변" "$RESPONSE_BODY" point "$INITIAL_POINT"
echo "[2/11] Gateway 내부 API 미노출 확인"

request POST "$GATEWAY_URL/api/customers/login" "{\"loginId\":\"$LOGIN_A\",\"password\":\"$PASSWORD\"}"
assert_status "고객 A 로그인" 200
TOKEN_A=$(json_get "$RESPONSE_BODY" accessToken)
[ -n "$TOKEN_A" ] || fail "고객 A 로그인" "비어 있지 않은 accessToken" "accessToken이 비어 있음"
assert_field "고객 A JWT 타입" "$RESPONSE_BODY" tokenType Bearer
echo "[3/11] 고객 A 회원가입 및 로그인 성공"

request POST "$GATEWAY_URL/api/products" "{\"name\":\"E2E Product A $RUN_ID\",\"price\":1000,\"stockQuantity\":10}"
assert_status "상품 A 등록" 201
PRODUCT_A=$(json_get "$RESPONSE_BODY" id)
assert_field "상품 A 가격" "$RESPONSE_BODY" price 1000
assert_field "상품 A 재고" "$RESPONSE_BODY" stockQuantity 10
request POST "$GATEWAY_URL/api/products" "{\"name\":\"E2E Product B $RUN_ID\",\"price\":2000,\"stockQuantity\":10}"
assert_status "상품 B 등록" 201
PRODUCT_B=$(json_get "$RESPONSE_BODY" id)
assert_field "상품 B 가격" "$RESPONSE_BODY" price 2000
assert_field "상품 B 재고" "$RESPONSE_BODY" stockQuantity 10
echo "[4/11] 상품 등록 성공"

ORDER_BODY="{\"items\":[{\"productId\":$PRODUCT_A,\"quantity\":2},{\"productId\":$PRODUCT_B,\"quantity\":1}]}"
request POST "$GATEWAY_URL/api/orders" "$ORDER_BODY"
assert_status "미인증 주문" 401
assert_field "미인증 주문 오류 코드" "$RESPONSE_BODY" code UNAUTHORIZED
echo "[5/11] 미인증 주문 차단 확인"

request POST "$GATEWAY_URL/api/orders" "$ORDER_BODY" "$TOKEN_A"
assert_status "신규 주문" 201
ORDER_ID=$(json_get "$RESPONSE_BODY" id)
assert_field "신규 주문 고객" "$RESPONSE_BODY" customerId "$CUSTOMER_A"
assert_field "신규 주문 상태" "$RESPONSE_BODY" status CREATED
assert_field "신규 주문 총액" "$RESPONSE_BODY" totalAmount 4000
assert_item "신규 주문 상품 A 수량" "$RESPONSE_BODY" "$PRODUCT_A" quantity 2
assert_item "신규 주문 상품 B 수량" "$RESPONSE_BODY" "$PRODUCT_B" quantity 1
request GET "$USER_URL/internal/customers/$CUSTOMER_A"
assert_status "신규 주문 후 고객 조회" 200
assert_field "신규 주문 후 포인트" "$RESPONSE_BODY" point 6000
request GET "$GATEWAY_URL/api/products/$PRODUCT_A"
assert_status "신규 주문 후 상품 A 조회" 200
assert_field "신규 주문 후 상품 A 재고" "$RESPONSE_BODY" stockQuantity 8
request GET "$GATEWAY_URL/api/products/$PRODUCT_B"
assert_status "신규 주문 후 상품 B 조회" 200
assert_field "신규 주문 후 상품 B 재고" "$RESPONSE_BODY" stockQuantity 9
echo "[6/11] 신규 주문 및 포인트 차감 확인"

request POST "$GATEWAY_URL/api/orders" "{\"items\":[{\"productId\":$PRODUCT_A,\"quantity\":1}]}" "$TOKEN_A"
assert_status "기존 주문 수량 누적" 200
assert_field "누적 주문 ID" "$RESPONSE_BODY" id "$ORDER_ID"
assert_field "누적 주문 총액" "$RESPONSE_BODY" totalAmount 5000
assert_item "누적 주문 상품 A 수량" "$RESPONSE_BODY" "$PRODUCT_A" quantity 3
request GET "$USER_URL/internal/customers/$CUSTOMER_A"
assert_status "누적 주문 후 고객 조회" 200
assert_field "누적 주문 후 포인트" "$RESPONSE_BODY" point 5000
request GET "$GATEWAY_URL/api/products/$PRODUCT_A"
assert_status "누적 주문 후 상품 A 조회" 200
assert_field "누적 주문 후 상품 A 재고" "$RESPONSE_BODY" stockQuantity 7
request GET "$GATEWAY_URL/api/orders" "" "$TOKEN_A"
assert_status "고객별 주문 목록" 200
assert_field "고객별 주문 목록 ID" "$RESPONSE_BODY" 0.id "$ORDER_ID"
echo "[7/11] 기존 주문 누적 및 증가분 차감 확인"

request POST "$GATEWAY_URL/api/customers" "{\"loginId\":\"$LOGIN_B\",\"password\":\"$PASSWORD\",\"name\":\"E2E Customer B\"}"
assert_status "고객 B 회원가입" 201
request POST "$GATEWAY_URL/api/customers/login" "{\"loginId\":\"$LOGIN_B\",\"password\":\"$PASSWORD\"}"
assert_status "고객 B 로그인" 200
TOKEN_B=$(json_get "$RESPONSE_BODY" accessToken)
[ -n "$TOKEN_B" ] || fail "고객 B 로그인" "비어 있지 않은 accessToken" "accessToken이 비어 있음"
request GET "$GATEWAY_URL/api/orders/$ORDER_ID" "" "$TOKEN_B"
assert_status "타 고객 주문 조회" 403
assert_field "타 고객 주문 조회 오류" "$RESPONSE_BODY" code FORBIDDEN_ORDER_ACCESS
request PATCH "$GATEWAY_URL/api/orders/$ORDER_ID/items/$PRODUCT_A/cancel" '{"quantity":1}' "$TOKEN_B"
assert_status "타 고객 부분 취소" 403
assert_field "타 고객 부분 취소 오류" "$RESPONSE_BODY" code FORBIDDEN_ORDER_ACCESS
request PATCH "$GATEWAY_URL/api/orders/$ORDER_ID/cancel" "" "$TOKEN_B"
assert_status "타 고객 전체 취소" 403
assert_field "타 고객 전체 취소 오류" "$RESPONSE_BODY" code FORBIDDEN_ORDER_ACCESS
request GET "$GATEWAY_URL/api/orders/$ORDER_ID" "" "$TOKEN_A"
assert_status "소유권 차단 후 주문 조회" 200
assert_field "소유권 차단 후 상태" "$RESPONSE_BODY" status CREATED
assert_field "소유권 차단 후 총액" "$RESPONSE_BODY" totalAmount 5000
assert_item "소유권 차단 후 상품 A 수량" "$RESPONSE_BODY" "$PRODUCT_A" quantity 3
assert_item "소유권 차단 후 상품 B 수량" "$RESPONSE_BODY" "$PRODUCT_B" quantity 1
request GET "$USER_URL/internal/customers/$CUSTOMER_A"
assert_field "소유권 차단 후 포인트" "$RESPONSE_BODY" point 5000
request GET "$GATEWAY_URL/api/products/$PRODUCT_A"
assert_field "소유권 차단 후 상품 A 재고" "$RESPONSE_BODY" stockQuantity 7
request GET "$GATEWAY_URL/api/products/$PRODUCT_B"
assert_field "소유권 차단 후 상품 B 재고" "$RESPONSE_BODY" stockQuantity 9
echo "[8/11] 타 고객 주문 접근 차단 확인"

request PATCH "$GATEWAY_URL/api/orders/$ORDER_ID/items/$PRODUCT_A/cancel" '{"quantity":1}' "$TOKEN_A"
assert_status "부분 취소" 200
assert_field "부분 취소 상태" "$RESPONSE_BODY" status CREATED
assert_field "부분 취소 총액" "$RESPONSE_BODY" totalAmount 4000
assert_item "부분 취소 상품 A 수량" "$RESPONSE_BODY" "$PRODUCT_A" quantity 2
request GET "$USER_URL/internal/customers/$CUSTOMER_A"
assert_field "부분 취소 후 포인트" "$RESPONSE_BODY" point 6000
request GET "$GATEWAY_URL/api/products/$PRODUCT_A"
assert_field "부분 취소 후 상품 A 재고" "$RESPONSE_BODY" stockQuantity 8
echo "[9/11] 부분 취소 및 일부 환불 확인"

request PATCH "$GATEWAY_URL/api/orders/$ORDER_ID/cancel" "" "$TOKEN_A"
assert_status "전체 취소" 204
request GET "$GATEWAY_URL/api/orders/$ORDER_ID" "" "$TOKEN_A"
assert_status "전체 취소 후 주문 조회" 200
assert_field "전체 취소 후 상태" "$RESPONSE_BODY" status CANCELLED
assert_field "전체 취소 후 총액" "$RESPONSE_BODY" totalAmount 0
assert_field "전체 취소 후 주문상품 제거" "$RESPONSE_BODY" items '[]'
request GET "$USER_URL/internal/customers/$CUSTOMER_A"
assert_field "전체 취소 후 포인트" "$RESPONSE_BODY" point "$INITIAL_POINT"
request GET "$GATEWAY_URL/api/products/$PRODUCT_A"
assert_field "전체 취소 후 상품 A 재고" "$RESPONSE_BODY" stockQuantity 10
request GET "$GATEWAY_URL/api/products/$PRODUCT_B"
assert_field "전체 취소 후 상품 B 재고" "$RESPONSE_BODY" stockQuantity 10
echo "[10/11] 전체 취소 및 최종 원상복구 확인"

request GET "$GATEWAY_URL/unknown"
assert_status "미등록 Gateway 경로" 404
echo "[11/11] 미등록 Gateway 경로 차단 확인"

TEST_PASSED=1
echo "MSA E2E TEST PASSED"
echo "GATEWAY MSA E2E TEST PASSED"
