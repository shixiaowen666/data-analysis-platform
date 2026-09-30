FROM jdk17:17.0.19-jre
ENV TZ=Asia/Shanghai

# 保留原有：安装ping、时区配置、垃圾清理 完全不变
RUN apt-get update && \
    apt-get install -y --no-install-recommends iputils-ping && \
    ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && \
    echo $TZ > /etc/timezone && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# 【行业标准改造】统一业务目录，废弃老旧/data工作目录
# 预创建标准配置、日志目录（SpringBoot规范路径）
RUN mkdir -p /app/config /app/logs

# 拷贝脚本、jar到标准工作目录 /app
COPY startup.sh /app/
COPY target/*-SNAPSHOT.jar  /app/app.jar

# 行业标准工作目录 /app
WORKDIR /app

# 标准入口不变
ENTRYPOINT ["sh", "startup.sh"]