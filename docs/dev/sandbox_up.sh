#!/usr/bin/env bash
# 沙箱一键拉起测试环境：MariaDB -> mock System B(:5055) -> bi-manager(:8591) -> 前端静态代理(:8600)
# 用法：bash docs/dev/sandbox_up.sh            # 不重新构建
#       REBUILD=1 bash docs/dev/sandbox_up.sh  # 重新打包 bi-manager + 重新构建前端(dev 模式)
set -e
ROOT=$(cd "$(dirname "$0")/../.." && pwd)
FE_DIST=${FE_DIST:-/home/user/fe_dist}
LOG=${LOG:-/home/user}

# 1. MariaDB
if ! mysqladmin -uchatbi -pchatbi123 ping >/dev/null 2>&1; then
  (sudo service mariadb start || sudo service mysql start || sudo mysqld_safe >/dev/null 2>&1 &) ; sleep 4
fi
mysql -uchatbi -pchatbi123 --default-character-set=utf8mb4 new_bi < "$ROOT/backend/bi-manager/src/main/resources/db/quality.sql" 2>/dev/null || true

# 2. mock System B / chat-server agent meta
if ! curl -s -o /dev/null -X POST http://127.0.0.1:5055/api/v1/prompts/groups; then
  setsid nohup python3 "$ROOT/docs/dev/mock_system_b.py" > "$LOG/mock_sysb.log" 2>&1 < /dev/null &
fi

# 3. bi-manager
if [ "$REBUILD" = "1" ]; then (cd "$ROOT/backend/bi-manager" && mvn -q -DskipTests package); fi
if ! curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8591/api/v1/chat/error-types | grep -qE "200|401"; then
  (cd "$ROOT/backend/bi-manager" && QUALITY_SYSTEM_B_URL=http://127.0.0.1:5055 QUALITY_CHAT_SERVER_URL=http://127.0.0.1:5055 QUALITY_RECALL_URL=http://127.0.0.1:5055 \
    setsid nohup java -Xmx400m -jar target/bi-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=sandbox > "$LOG/bi.log" 2>&1 < /dev/null &)
  for i in $(seq 1 40); do sleep 3; curl -s -o /dev/null -w "%{http_code}" http://127.0.0.1:8591/api/v1/chat/error-types | grep -qE "200|401" && break; done
fi

# 4. 前端
if [ "$REBUILD" = "1" ] || [ ! -f "$FE_DIST/index.html" ]; then
  (cd "$ROOT/frontend/chatbi-new" && NODE_OPTIONS=--max-old-space-size=1400 npx vue-cli-service build --mode development --no-module --dest "$FE_DIST")
fi
if ! curl -s -o /dev/null http://127.0.0.1:8600/; then
  FE_DIST="$FE_DIST" setsid nohup python3 "$ROOT/docs/dev/fe_static_proxy.py" > "$LOG/fe_serve.log" 2>&1 < /dev/null &
  sleep 1
fi
echo "ready: fe http://127.0.0.1:8600  bi http://127.0.0.1:8591  mock http://127.0.0.1:5055"
