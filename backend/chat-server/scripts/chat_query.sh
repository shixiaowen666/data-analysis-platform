#!/bin/bash

# 自动调用 /api/chat-server/chat 接口
# 用法: ./chat_query.sh <queries.txt>
# 每行一个 query，aicode 固定，chatSessionId 首次由服务端返回
# 脚本启动时自动登录获取 token，401 时自动重新登录

set -euo pipefail

# ---- 配置 ----
BASE_URL="http://ip:port"
LOGIN_URL="http://ip:port"
LOGIN_USERNAME="baiduadmin"
LOGIN_PASSWORD="123456"
AICODE="7ae7deb3-0bf5-4b5a-a65d-c6a19f28d1f8"
CHAT_API="/api/chat-server/chat"
LOGIN_API="/upc/user/login"
INTERVAL=120
GROUP_SIZE=10

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
TXT_FILE="${1:-$SCRIPT_DIR/queries.txt}"
LOG_FILE="$SCRIPT_DIR/chat_query_$(date +%Y%m%d_%H%M%S).log"

if [ ! -f "$TXT_FILE" ]; then
    echo "错误: 文件不存在: $TXT_FILE"
    echo "用法: $0 <queries.txt>"
    exit 1
fi

# ---- 日志函数 ----
log() {
    local msg
    msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
    echo "$msg" | tee -a "$LOG_FILE"
}

# ---- 登录函数 ----
do_login() {
    log "[login] 正在登录 $LOGIN_URL$LOGIN_API ..."
    local resp
    resp=$(curl -s -X POST "$LOGIN_URL$LOGIN_API" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"$LOGIN_USERNAME\",\"password\":\"$LOGIN_PASSWORD\"}" 2>&1) || true

    local token
    token=$(echo "$resp" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    token = data['data']['token']
    print(token)
except Exception:
    pass
" 2>/dev/null)

    if [ -z "$token" ]; then
        log "[login] 登录失败，响应: $resp"
        return 1
    fi

    TOKEN="$token"
    log "[login] 登录成功，token: ${token:0:20}..."
    return 0
}

# ---- 发送聊天请求 ----
do_chat() {
    local question="$1"
    local token="$2"
    local session_id="$3"

    local escaped_question
    escaped_question=$(echo "$question" | python3 -c "
import sys, json
print(json.dumps(sys.stdin.read()))
" 2>/dev/null || echo "\"$(echo "$question" | sed 's/\\/\\\\/g; s/"/\\"/g')\"")

    local request_body
    if [ -n "$session_id" ]; then
        request_body="{\"aicode\":\"$AICODE\",\"chatSessionId\":\"$session_id\",\"question\":$escaped_question}"
    else
        request_body="{\"aicode\":\"$AICODE\",\"question\":$escaped_question}"
    fi

    echo "$request_body" >> "$LOG_FILE"

    local response http_code body
    response=$(curl -s -w "\n%{http_code}" \
        -X POST "$BASE_URL$CHAT_API" \
        -H "Content-Type: application/json" \
        -H "Authorization: Bearer $token" \
        -H "tenantid: 1" \
        -d "$request_body" 2>&1) || true

    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')
    echo "$http_code|$body"
}

# ---- 从响应中提取 chatSessionId ----
extract_session_id() {
    local body="$1"
    echo "$body" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    sid = data['data']['chatSessionId']
    print(sid)
except Exception:
    pass
" 2>/dev/null
}

# ---- 主流程 ----
CHAT_SESSION_ID=""
TOKEN=""

log "========================================="
log "日志文件:      $LOG_FILE"
log "aicode:        $AICODE"
log "chat API:      $BASE_URL$CHAT_API"
log "login API:     $LOGIN_URL$LOGIN_API"
log "问题文件:      $TXT_FILE"
log "请求间隔:      ${INTERVAL}s"
log "会话组大小:    $GROUP_SIZE"
log "========================================="

# 首次登录
if ! do_login; then
    log "首次登录失败，退出"
    exit 1
fi

total=0
success=0
fail=0

while IFS= read -r line || [ -n "$line" ]; do
    line=$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')
    [ -z "$line" ] && continue
    [[ "$line" == \#* ]] && continue

    total=$((total + 1))

    # 每 GROUP_SIZE 个请求为一组：组内第一个请求不带 chatSessionId，后续复用
    if (( total % GROUP_SIZE == 1 )); then
        CHAT_SESSION_ID=""
        log "--- [$total] 问题: $line (新会话组) ---"
    else
        log "--- [$total] 问题: $line ---"
    fi

    result=$(do_chat "$line" "$TOKEN" "$CHAT_SESSION_ID")
    http_code=$(echo "$result" | cut -d'|' -f1)
    body=$(echo "$result" | cut -d'|' -f2-)

    # 401 则重新登录重试一次
    if [ "$http_code" = "401" ]; then
        log "状态: $http_code | token 过期，重新登录..."
        if do_login; then
            sleep 1
            result=$(do_chat "$line" "$TOKEN" "$CHAT_SESSION_ID")
            http_code=$(echo "$result" | cut -d'|' -f1)
            body=$(echo "$result" | cut -d'|' -f2-)
        else
            log "重新登录失败，跳过此问题"
            fail=$((fail + 1))
            continue
        fi
    fi

    # 组内第一个请求（chatSessionId 为空）：从响应中提取 chatSessionId
    if [ "$http_code" = "200" ] && [ -z "$CHAT_SESSION_ID" ]; then
        sid=$(extract_session_id "$body")
        if [ -n "$sid" ]; then
            CHAT_SESSION_ID="$sid"
            log "新会话组 chatSessionId: $CHAT_SESSION_ID"
        fi
    fi

    if [ "$http_code" = "200" ]; then
        success=$((success + 1))
        log "状态: $http_code | 响应: $body"
    else
        fail=$((fail + 1))
        log "状态: $http_code | 错误: $body"
    fi

    log "等待 ${INTERVAL} 秒..."
    sleep "$INTERVAL"

done < "$TXT_FILE"

log ""
log "========================================="
log "完成! 总数: $total | 成功: $success | 失败: $fail"
log "chatSessionId: $CHAT_SESSION_ID"
log "日志文件: $LOG_FILE"
log "========================================="
