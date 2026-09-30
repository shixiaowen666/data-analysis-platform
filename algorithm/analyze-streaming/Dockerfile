FROM swr.cn-north-4.myhuaweicloud.com/ddn-k8s/docker.io/python:3.11.9-slim-bookworm

WORKDIR /code

COPY ./requirements.txt /code/requirements.txt
COPY ./.env  /code/.env

RUN pip install -i https://pypi.tuna.tsinghua.edu.cn/simple \
    --no-cache-dir -r /code/requirements.txt

ENV NO_UVLOOP=1

COPY system_b  /code/system_b
COPY .env /code
COPY startup.sh /code
RUN ls -la /code

# 编译 Python 字节码并删除 .py 源文件，镜像内仅保留 .pyc 交付部署
# -b (legacy mode): .pyc 与源文件同目录，模块启动方式 python -m system_b.app 不受影响
RUN python -m compileall -q -b /code/system_b && \
    find /code/system_b -name "*.py" -delete && \
    find /code/system_b -type d -name "__pycache__" -empty -delete

EXPOSE 8123
ENTRYPOINT ["bash", "startup.sh"]
