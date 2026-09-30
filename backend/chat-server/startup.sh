#!/bin/sh
set -e

# 等待配置挂载就绪（防止容器启动早于挂载）
echo "waiting for config volume ready..."
while [ ! -d /app/config ]; do
    sleep 0.5
done

# 标准化分层日志目录（适配单机单实例，移除POD_IP层级，路径更干净）
LOG_DIR=/app/logs/${SPRING_PROFILES_ACTIVE}/${SERVICE_NAME}
mkdir -p ${LOG_DIR}

# 运维友好日志软链接（固定入口，不用记长路径）
rm -rf /app/current-logs
ln -sf ${LOG_DIR} /app/current-logs

# 打印启动信息，方便排障
echo "============================================="
echo "Active Profile: ${SPRING_PROFILES_ACTIVE}"
echo "Service Name:   ${SERVICE_NAME}"
echo "JVM Options:    ${JAVA_OPTS}"
echo "Log Path:       ${LOG_DIR}"
echo "============================================="

# 标准启动：读取JAVA_OPTS、默认加载 /app/config 配置、优雅启动
exec java ${JAVA_OPTS} -jar /app/app.jar \
--spring.profiles.active=k8s