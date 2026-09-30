# -*- coding: utf-8 -*-
"""生成《web-json 镜像部署与 nginx 配置说明文档》Word 文档"""
from docx import Document
from docx.shared import Pt, Cm, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

OUT_PATH = r"D:\工作\公司\项目\问数\web-json\analyze-streaming-v2\docs\部署说明-web-json镜像加载与nginx配置.docx"

BLUE = RGBColor(0x1F, 0x4E, 0x79)
DARK = RGBColor(0x33, 0x33, 0x33)
RED = RGBColor(0xC0, 0x00, 0x00)
GRAY_FILL = "F2F2F2"
HDR_FILL = "D9E2F3"

doc = Document()

# ---------- 页面设置 ----------
for sec in doc.sections:
    sec.top_margin = Cm(2.2)
    sec.bottom_margin = Cm(2.2)
    sec.left_margin = Cm(2.5)
    sec.right_margin = Cm(2.5)


def set_run_font(run, name="Calibri", east="宋体", size=10.5, bold=False, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color
    r = run._element.rPr.rFonts
    r.set(qn("w:eastAsia"), east)


def style_heading(style_name, east="微软雅黑", color=BLUE, size=16):
    st = doc.styles[style_name]
    st.font.name = "Calibri"
    st.font.size = Pt(size)
    st.font.bold = True
    st.font.color.rgb = color
    rpr = st.element.get_or_add_rPr()
    rf = rpr.find(qn("w:rFonts"))
    if rf is None:
        rf = OxmlElement("w:rFonts")
        rpr.append(rf)
    rf.set(qn("w:eastAsia"), east)


style_heading("Heading 1", size=16)
style_heading("Heading 2", size=13)
style_heading("Heading 3", size=11.5)


def shade(paragraph, fill=GRAY_FILL):
    pPr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    pPr.append(shd)


def add_h1(text):
    doc.add_heading(text, level=1)


def add_h2(text):
    doc.add_heading(text, level=2)


def add_h3(text):
    doc.add_heading(text, level=3)


def add_p(text, bold=False, size=10.5, color=None):
    p = doc.add_paragraph()
    run = p.add_run(text)
    set_run_font(run, size=size, bold=bold, color=color)
    return p


def add_note(text):
    p = doc.add_paragraph()
    run = p.add_run("【注意】" + text)
    set_run_font(run, size=10.5, bold=True, color=RED)
    return p


def add_tip(text):
    p = doc.add_paragraph()
    run = p.add_run("【提示】" + text)
    set_run_font(run, size=10.5, bold=True, color=BLUE)
    return p


def add_code(code):
    p = doc.add_paragraph()
    p.paragraph_format.space_before = Pt(4)
    p.paragraph_format.space_after = Pt(4)
    lines = code.strip("\n").split("\n")
    for i, line in enumerate(lines):
        run = p.add_run(line)
        set_run_font(run, name="Consolas", east="宋体", size=9)
        if i < len(lines) - 1:
            run.add_break()
    shade(p)
    return p


def add_bullet(text, bold_prefix=None):
    p = doc.add_paragraph(style="List Bullet")
    if bold_prefix:
        r1 = p.add_run(bold_prefix)
        set_run_font(r1, bold=True)
    r2 = p.add_run(text)
    set_run_font(r2)
    return p


def add_table(headers, rows, widths=None):
    t = doc.add_table(rows=1, cols=len(headers))
    t.style = "Table Grid"
    hdr = t.rows[0].cells
    for j, h in enumerate(headers):
        hdr[j].text = ""
        p = hdr[j].paragraphs[0]
        run = p.add_run(h)
        set_run_font(run, bold=True, size=10)
        shade(p, fill=HDR_FILL)
    for row in rows:
        cells = t.add_row().cells
        for j, val in enumerate(row):
            cells[j].text = ""
            p = cells[j].paragraphs[0]
            run = p.add_run(val)
            set_run_font(run, size=9.5)
    if widths:
        for j, w in enumerate(widths):
            for row in t.rows:
                row.cells[j].width = Cm(w)
    doc.add_paragraph()
    return t


# =====================================================================
# 封面
# =====================================================================
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_before = Pt(60)
run = p.add_run("web-json 镜像部署与 nginx 配置说明文档")
set_run_font(run, east="微软雅黑", size=22, bold=True, color=BLUE)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run("（面向零基础读者的完整操作指南）")
set_run_font(run, east="微软雅黑", size=12, color=DARK)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
run = p.add_run("镜像文件：web-json-amd-20260908-01.tar")
set_run_font(run, name="Consolas", east="宋体", size=11)

p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(40)
run = p.add_run("适用读者：无 Docker / nginx 经验的新手运维人员")
set_run_font(run, size=11)

doc.add_page_break()

# =====================================================================
# 第一章 文档说明
# =====================================================================
add_h1("第一章 文档说明")

add_h2("1.1 本文档能帮你做什么")
add_p("开发团队会交付一个名为 web-json-amd-20260908-01.tar 的文件，它是 Docker 镜像的打包文件（由 docker save 命令导出）。")
add_p("本文档从拿到这个 tar 文件开始，一步一步教你在服务器上完成以下全部工作：")
add_bullet("把 tar 文件导入成 Docker 镜像（docker load）；")
add_bullet("准备配置文件和挂载目录；")
add_bullet("启动 web-json 服务容器；")
add_bullet("配置 nginx 反向代理，让用户通过浏览器访问 Web 页面；")
add_bullet("验证页面是否正常、排障。")

add_h2("1.2 名词解释")
add_p("文档里会用到一些术语，先看这张表（不熟悉的名词随时回来看）：")
add_table(
    ["名词", "通俗解释"],
    [
        ["Docker 镜像（Image）", "打包好的应用「模板」，里面装好了程序和环境。它本身不能运行，类似一个光盘映像。"],
        ["容器（Container）", "镜像「运行起来」之后的实例，真正干活的进程，可以启动、停止、删除。"],
        ["docker save", "把镜像导出成一个 .tar 文件，方便拷贝到别的机器（开发团队已帮你做完）。"],
        ["docker load", "把 .tar 文件导回成镜像（你在这份文档里要做的操作）。"],
        ["tar 文件", "一种打包文件（.tar 后缀），这里就是镜像的载体。"],
        ["反向代理", "nginx 在中间「收快递再转交」：用户只访问 nginx 的端口，nginx 把请求转给内部的 web-json 服务。"],
        ["前缀剥离", "用户访问 /web_json_html/test_logs，nginx 把 /web_json_html/ 前缀去掉，转成 /test_logs 发给后端。"],
        ["SSE 流式输出", "一种网页实时接收服务器消息的技术，流式分析页面「逐字打字」的效果依赖它。"],
        ["挂载（-v）", "把宿主机（服务器）的文件夹「借」给容器用，容器重启或删除后数据还在。"],
    ],
)

add_h2("1.3 整体流程一览")
add_p("整个部署就下面 6 步，照着文档做即可：")
add_code("""① 上传 tar 文件到服务器
② docker load 导入镜像
③ 创建目录 + 编写 .env 配置文件
④ docker run 启动容器（服务监听 8123 端口）
⑤ 配置 nginx 反向代理（对外监听 8090 端口，示例）
⑥ 浏览器访问验证
访问地址形如：http://服务器IP:8090/web_json_html/test_all""")

# =====================================================================
# 第二章 准备工作
# =====================================================================
add_h1("第二章 准备工作")

add_h2("2.1 你需要准备的东西")
add_table(
    ["需要准备", "说明"],
    [
        ["web-json-amd-20260908-01.tar 文件", "开发交付的镜像包，先放在你自己的电脑上。"],
        ["一台 Linux 服务器", "本文档以 CentOS / Ubuntu 为例，命令两边都会给。"],
        ["SSH 远程登录工具", "如 XShell、MobaXterm、FinalShell，或直接用系统自带终端，用来远程操作服务器。"],
        [".env 配置内容", "系统运行所需的配置（数据库、Redis、大模型密钥等），见第四章，可以找开发要现成的。"],
        ["root 权限或 sudo 权限", "安装软件、改配置都需要。"],
    ],
)

add_h2("2.2 登录服务器")
add_p("用 SSH 工具连接服务器，在工具里新建连接，填写：")
add_bullet("主机：服务器 IP（如 192.168.1.100，具体问管理员）")
add_bullet("端口：默认 22")
add_bullet("用户名 / 密码：管理员提供的账号")
add_p("命令行方式登录（在你自己电脑的终端里执行，按提示输入密码）：")
add_code("""ssh 用户名@服务器IP
# 示例：ssh root@192.168.1.100""")
add_p("登录成功后，命令行会变成类似 root@xxx 的提示符，下面的操作都在这个终端里进行。")

add_h2("2.3 检查 Docker 是否已安装")
add_p("执行下面两条命令：")
add_code("""docker --version
docker ps""")
add_bullet("第一条能输出版本号（如 Docker version 24.0.7），说明 Docker 已安装。", bold_prefix="期望结果：")
add_bullet("第二条列出当前运行的容器，刚开始是空的（只显示表头）也正常，说明 Docker 服务正常。", bold_prefix="期望结果：")
add_note("如果提示 docker: command not found，说明还没装 Docker，先执行下面 2.4 节安装，再回来继续。")

add_h2("2.4 安装 Docker（仅当未安装时需要）")
add_p("CentOS / RedHat 系统执行：")
add_code("""yum install -y docker
systemctl start docker        # 启动 Docker 服务
systemctl enable docker       # 设置开机自启""")
add_p("Ubuntu / Debian 系统执行：")
add_code("""apt-get update
apt-get install -y docker.io
systemctl start docker
systemctl enable docker""")
add_p("安装完重新执行 2.3 节的两条命令确认没问题再继续。")

# =====================================================================
# 第三章 加载镜像
# =====================================================================
add_h1("第三章 加载镜像（docker load）")

add_h2("3.1 把 tar 文件上传到服务器")
add_p("在你【自己电脑】的终端（注意：不是服务器终端）执行 scp 命令，把 tar 文件传到服务器的 /opt 目录：")
add_code("""scp web-json-amd-20260908-01.tar 用户名@服务器IP:/opt/
# 示例：scp web-json-amd-20260908-01.tar root@192.168.1.100:/opt/""")
add_p("命令解释：")
add_bullet("scp：Linux 的文件复制命令，支持跨机器传输；")
add_bullet("第一个参数：本地的 tar 文件（如果文件在别的目录，写完整路径）；")
add_bullet("用户名@服务器IP:/opt/：传到服务器哪个目录，这里传到了 /opt/ 下。")
add_p("传输过程中会要求输入服务器密码。传完后文件就在服务器的 /opt/ 目录里了。")
add_tip("用 XShell / FinalShell 的话，也可以直接用图形界面的「上传文件」按钮，拖进去即可，效果一样。")

add_h2("3.2 导入镜像")
add_p("现在回到【服务器终端】，执行：")
add_code("""cd /opt
docker load -i web-json-amd-20260908-01.tar""")
add_p("命令解释：")
add_bullet("cd /opt：进入 /opt 目录（文件在这里）；")
add_bullet("docker load：把 tar 包导入成 Docker 镜像；")
add_bullet("-i web-json-amd-20260908-01.tar：-i 是 input 的意思，指定要导入的文件。")
add_p("等待进度条跑完，最后会看到类似这样的输出，说明导入成功：")
add_code("""Loaded image: web-json:amd-20260908-01""")
add_note("注意看这一行的 镜像名:标签，后面启动容器时要原样用到。")

add_h2("3.3 验证镜像是否导入成功")
add_code("""docker images""")
add_p("输出是一个表格，重点看前两列：")
add_table(
    ["列名", "含义"],
    [
        ["REPOSITORY（仓库名）", "镜像名，应该能看到 web-json"],
        ["TAG（标签）", "版本标签，应该能看到 amd-20260908-01"],
        ["IMAGE ID", "镜像的唯一编号，不用管"],
        ["SIZE（大小）", "镜像占用的空间，1GB 左右都正常"],
    ],
)
add_p("如果表格里能看到 web-json 和 amd-20260908-01，镜像就绪，进入第四章。")

# =====================================================================
# 第四章 目录与配置文件
# =====================================================================
add_h1("第四章 准备挂载目录与 .env 配置文件")

add_h2("4.1 为什么要挂载目录")
add_p("容器是「临时」的：如果被删除重建，容器里产生的数据会全部丢失。")
add_p("所以我们要在服务器上建三个文件夹，把它们「借」给容器用（挂载）：")
add_table(
    ["服务器目录（自己建）", "挂载到容器哪里", "存放什么"],
    [
        ["/data/chatbi/service/web-json/logs", "/opt/project/logs", "运行日志"],
        ["/data/chatbi/service/web-json/conf", "/code/.env（单个文件）", "系统配置文件"],
        ["/data/chatbi/service/web-json/prompts", "/code/system_b/prompts", "提示词文件（AI 对话模板）"],
    ],
)
add_p("这样以后升级版本、重建容器，日志和配置都不会丢。")

add_h2("4.2 创建目录")
add_p("执行（一行一条，共三条）：")
add_code("""mkdir -p /data/chatbi/service/web-json/logs
mkdir -p /data/chatbi/service/web-json/conf
mkdir -p /data/chatbi/service/web-json/prompts""")
add_p("命令解释：mkdir 是创建文件夹的命令；-p 表示「如果上级目录不存在就一起创建」。")
add_p("可以执行 ls 确认创建成功：")
add_code("""ls -l /data/chatbi/service/web-json/""")

add_h2("4.3 创建 .env 配置文件")
add_p("用 vim 编辑配置文件（先创建 conf 目录下的 .env 文件）：")
add_code("""vim /data/chatbi/service/web-json/conf/.env""")
add_p("vim 对新手不太友好，记住 4 个操作就行：")
add_table(
    ["操作", "按键"],
    [
        ["进入编辑模式", "按字母 i（左下角出现 -- INSERT -- 表示可以打字了）"],
        ["粘贴内容", "把下面的配置全选复制后，在编辑模式里点鼠标右键粘贴"],
        ["退出编辑模式", "按 Esc 键"],
        ["保存并退出", "输入 :wq 然后按回车（注意是英文冒号）"],
    ],
)
add_note("如果系统提示没有 vim，可以改用 nano：把上面命令换成 nano /data/chatbi/service/web-json/conf/.env，nano 用法：直接粘贴，然后按 Ctrl+X，按 Y 确认，按回车保存。")

add_h3("4.3.1 完整配置示例（可直接复制）")
add_p("把下面的内容复制进 .env 文件（井号 # 开头的是注释，可以不删）：")
add_code("""# ================= System B 配置 =================

# ---- System A 连接（问数平台的服务地址，必改）----
SYSTEM_A_BASE_URL=http://127.0.0.1:5001
SYSTEM_A_TIMEOUT=60
SYSTEM_A_MAX_RETRIES=2

# ---- System B 自身监听（容器内端口，默认不用改）----
SYSTEM_B_HOST=0.0.0.0
SYSTEM_B_PORT=8123

# ---- 大模型配置（必改：API 密钥、模型名）----
LLM_API_KEY=sk-在这里填写你的API密钥
LLM_API_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen3-235b-a22b-instruct-2507
LLM_TEMPERATURE=0.1
LLM_MAX_TOKENS=4096

# ---- DAG 执行引擎线程数（默认即可）----
DAG_THREAD_POOL_SIZE=4

# ---- 日志 ----
LOG_LEVEL=INFO
LOG_DIR=/opt/project/logs

# ---- Redis（必改：问数平台会话缓存）----
REDIS_HOST=redis://用户名:密码@Redis服务器IP:6379/0

# ---- 问数平台（测试页「平台会话」模式用）----
CHATBI_BASE_URL=http://问数平台IP:8091
CHATBI_USERNAME=admin
CHATBI_PASSWORD=在这里填写密码

# ---- MySQL 数据库（必改）----
MYSQL_HOST=数据库IP
MYSQL_PORT=3306
MYSQL_USER=chatbi
MYSQL_PASSWORD=在这里填写密码
MYSQL_DATABASE=webapp

# ---- JWT 鉴权密钥（与 Java 端保持一致，一般不用改）----
JWT_SECRET=chatbi-jwt-secret-key-for-production-2026""")

add_h3("4.3.2 关键配置项逐项说明")
add_p("下表中标「必改」的项，必须根据你的实际环境修改，否则服务可能起不来或功能异常：")
add_table(
    ["配置项", "作用", "要不要改"],
    [
        ["SYSTEM_A_BASE_URL", "System A 服务（问数平台接口）的地址。问数平台和 web-json 在同一台机器写 127.0.0.1，不在同一台写实际 IP 加端口。", "必改"],
        ["SYSTEM_B_PORT", "web-json 自身监听的端口，容器内固定 8123，与第五章 docker run 的 -p 8123:8123 对应。", "一般不改"],
        ["LLM_API_KEY", "大模型（DashScope 通义千问）的 API 密钥，找开发要。", "必改"],
        ["LLM_API_BASE_URL", "大模型接口地址，默认用阿里云 DashScope 官方地址即可。", "一般不改"],
        ["LLM_MODEL", "使用的模型名，如 qwen3-235b-a22b-instruct-2507，按开发要求填。", "必改"],
        ["REDIS_HOST", "Redis 连接串，格式 redis://用户名:密码@IP:端口/库号，问数平台的会话缓存。", "必改"],
        ["MYSQL_HOST / PORT / USER / PASSWORD / DATABASE", "MySQL 数据库连接信息，web-json 需要读写数据库（提示词、操作日志等）。", "必改"],
        ["CHATBI_BASE_URL / USERNAME / PASSWORD", "问数平台登录信息，测试页「平台会话」模式创建真实会话时用。", "按需"],
        ["JWT_SECRET", "鉴权密钥，必须和 Java 端一致，否则接口校验失败。", "按开发要求"],
        ["LOG_DIR", "日志输出目录，固定 /opt/project/logs，对应挂载的 logs 目录。", "不用改"],
    ],
)
add_tip("改完 .env 后，如果服务已经启动过，需要重启容器才生效（见 5.3 节）。")

# =====================================================================
# 第五章 启动容器
# =====================================================================
add_h1("第五章 启动容器（docker run）")

add_h2("5.1 启动命令（完整版）")
add_p("在服务器终端执行下面整段命令（每行结尾的反斜杠 \\ 表示「这行没写完，下一行继续」，可以整段复制粘贴）：")
add_code("""docker run -d \\
  --name web-json \\
  --network chatbi-net \\
  -p 8123:8123 \\
  -v /data/chatbi/service/web-json/logs:/opt/project/logs \\
  -v /data/chatbi/service/web-json/conf/.env:/code/.env \\
  -v /data/chatbi/service/web-json/prompts:/code/system_b/prompts \\
  -e ENV=dev \\
  -e TZ=Asia/Shanghai \\
  -e LANG=C.UTF-8 \\
  -e NO_UVLOOP=1 \\
  -w /code \\
  --restart=always \\
  web-json:amd-20260908-01""")
add_p("执行成功后，会输出一串容器 ID（几十位字母数字），表示容器已创建并启动。")

add_h2("5.2 命令逐参数解释")
add_table(
    ["参数", "含义"],
    [
        ["docker run", "创建并启动一个容器"],
        ["-d", "后台运行（detached），执行完命令不占用终端"],
        ["--name web-json", "给容器起名 web-json，以后操作都用这个名字"],
        ["--network chatbi-net", "把容器加入 chatbi-net 网络（和其他服务互通）。如果提示网络不存在，先按 5.3 节创建"],
        ["-p 8123:8123", "端口映射：服务器 8123 端口 → 容器 8123 端口。格式是 宿主机端口:容器端口"],
        ["-v 服务器目录:容器目录", "挂载目录，三个 -v 对应第四章建的三个目录"],
        ["-e 变量名=值", "设置环境变量，比如时区 TZ=Asia/Shanghai、语言 LANG=C.UTF-8"],
        ["-w /code", "工作目录设为 /code（程序所在位置，镜像里已固定）"],
        ["--restart=always", "服务器重启时容器自动启动，容器意外退出也会自动拉起"],
        ["web-json:amd-20260908-01", "用哪个镜像启动（镜像名:标签，就是第三章 docker load 导入的那个）"],
    ],
)

add_h2("5.3 补充：创建 chatbi-net 网络（仅首次需要）")
add_p("如果 5.1 节执行时报错 network chatbi-net not found，先执行下面命令创建网络，再重新执行 5.1 节：")
add_code("""docker network create chatbi-net""")
add_note("如果这台服务器不需要和其他容器组网，也可以把 5.1 节命令里的 --network chatbi-net 整行删掉，直接用默认网络，效果是仅通过 8123 端口访问。具体按部署要求来。")

add_h2("5.4 启动后验证")
add_p("执行以下三条命令逐一验证：")
add_code("""docker ps""")
add_bullet("看 STATUS 列有没有 Up 字样、PORTS 列有没有 0.0.0.0:8123->8123/tcp。有就是运行中。", bold_prefix="期望结果：")
add_code("""docker logs -f web-json""")
add_bullet("滚动输出运行日志，看到启动成功相关的 INFO 记录即为正常。看完按 Ctrl+C 退出（只是停止看日志，不影响服务）。", bold_prefix="期望结果：")
add_code("""curl http://127.0.0.1:8123/health""")
add_bullet("返回一段 JSON（如 {\"status\":\"ok\"} 之类），说明服务健康检查通过。", bold_prefix="期望结果：")
add_note("如果 docker ps 里看不到容器，或 curl 没反应，直接跳到第八章 FAQ 排查。")

# =====================================================================
# 第六章 nginx 配置
# =====================================================================
add_h1("第六章 nginx 配置（重点）")
add_p("web-json 服务本身已经能通过 http://服务器IP:8123 访问，但正式使用建议加一层 nginx 反向代理，好处是：")
add_bullet("不用把服务端口直接暴露出去，更安全；")
add_bullet("可以挂域名、配 HTTPS；")
add_bullet("通过统一的前缀 /web_json_html/ 访问，便于和多套系统共存。")

add_h2("6.1 检查 nginx 是否已安装")
add_code("""nginx -v""")
add_p("能输出版本号说明已安装，直接跳到 6.2。如果提示 command not found，先安装：")
add_p("CentOS / RedHat 系统执行：")
add_code("""yum install -y nginx
systemctl start nginx
systemctl enable nginx""")
add_p("Ubuntu / Debian 系统执行：")
add_code("""apt-get update
apt-get install -y nginx
systemctl start nginx
systemctl enable nginx""")

add_h2("6.2 新建配置文件 web_json.conf")
add_p("nginx 的配置目录通常是 /etc/nginx/conf.d/。在里面新建一个专门的文件：")
add_code("""vim /etc/nginx/conf.d/web_json.conf""")
add_p("把下面的内容复制进去（和 .env 一样，按 i 粘贴，按 Esc，输入 :wq 回车保存）：")
add_code("""server {
    listen 8090;                     # 对外监听端口（示例值，可按部署要求改成 10011/8089 等已开放端口）
    server_name _;                   # _ 表示匹配任意域名 / IP

    # 所有以 /web_json_html/ 开头的请求，都转发给本机 8123 的 web-json 服务
    location /web_json_html/ {
        # 末尾的 / 是关键：nginx 会把 /web_json_html/ 前缀剥掉再转发
        # 例：/web_json_html/test_logs  ->  http://127.0.0.1:8123/test_logs
        proxy_pass http://127.0.0.1:8123/;

        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # SSE 流式响应必须关闭缓冲，否则流式分析页面看不到实时逐字输出
        proxy_buffering off;
        proxy_cache off;
        proxy_read_timeout 3600s;
    }
}""")

add_h2("6.3 三个关键点（务必理解）")
add_p("1. proxy_pass 地址末尾必须带斜杠 /")
add_p("带了 /，nginx 才会把 /web_json_html/ 前缀剥掉：/web_json_html/test_logs → /test_logs。如果漏了末尾的 /，请求会原样转发成 /web_json_html/test_logs，后端找不到路由，页面接口全部 404。")
add_p("2. proxy_buffering off 不能少")
add_p("web-json 的流式分析页面依赖 SSE 实时推送，nginx 默认会缓冲响应、攒够了才发，导致页面半天不出字。这一行就是关掉缓冲，逐字实时转发。")
add_p("3. 127.0.0.1:8123 的含义")
add_p("表示把请求转发给本机 8123 端口（就是第五章启动的 web-json 容器）。如果 nginx 和 web-json 不在同一台服务器，要把 127.0.0.1 改成 web-json 所在服务器的实际 IP。")

add_h2("6.4 检查语法并重载配置")
add_code("""nginx -t""")
add_p("看到 syntax is ok 和 test is successful 才算通过。若有报错，根据提示检查 6.2 节配置里对应的行。")
add_p("通过后让配置生效（二选一）：")
add_code("""nginx -s reload
# 或
systemctl reload nginx""")

add_h2("6.5 防火墙放行端口")
add_p("nginx 监听的端口（示例 8090，改成你实际用的端口）需要在防火墙放行：")
add_p("CentOS 7+（firewalld）：")
add_code("""firewall-cmd --add-port=8090/tcp --permanent
firewall-cmd --reload""")
add_p("Ubuntu（ufw）：")
add_code("""ufw allow 8090/tcp""")
add_note("如果服务器在云平台（阿里云 / 华为云 / 腾讯云等），还要登录云控制台，在「安全组 / 防火墙」里放行该端口，否则外网依然访问不了。")

# =====================================================================
# 第七章 验证与使用
# =====================================================================
add_h1("第七章 验证与使用")

add_h2("7.1 访问 Web 页面")
add_p("在浏览器地址栏输入（8090 换成你实际配置的端口，IP 换成服务器 IP）：")
add_code("""http://服务器IP:8090/web_json_html/test_all""")
add_p("打开后顶部有导航栏，能切换到以下五个页面：")
add_table(
    ["页面", "访问地址", "作用"],
    [
        ["流式分析", "/web_json_html/test_analyze_stream", "输入问题提问，答案逐字输出（SSE 实时效果）"],
        ["元数据", "/web_json_html/test_meta", "查看 / 管理数据库元数据"],
        ["配置管理", "/web_json_html/test_model_config", "查看服务状态 / 健康检查 / Recall 配置"],
        ["操作日志", "/web_json_html/test_logs", "查看用户操作日志"],
        ["提示词管理", "/web_json_html/test_prompts", "查看 / 管理提示词及版本"],
    ],
)

add_h2("7.2 怎么判断部署成功")
add_bullet("五个页面都能打开、顶部导航能自由切换 → nginx 代理生效；")
add_bullet("在「流式分析」页提问，答案能逐字输出 → SSE 正常（第六章 proxy_buffering off 生效）；")
add_bullet("在「操作日志」页能看到记录 → 数据库连接正常。")

# =====================================================================
# 第八章 常见问题
# =====================================================================
add_h1("第八章 常见问题排查（FAQ）")

add_h2("问题 1：docker load 报错，导入失败")
add_p("常见原因是 tar 文件上传不完整（网络中断）。重新用 scp 上传一次，或对比文件大小是否和原始文件一致（ls -l 查看大小）。")

add_h2("问题 2：容器启动后马上退出（docker ps 看不到）")
add_p("查看日志定位原因：")
add_code("""docker logs web-json""")
add_p("最常见的两个原因：")
add_bullet(".env 文件没放对位置或格式错误（少了某个必填配置项）→ 检查第四章挂载路径 /code/.env 是否正确；")
add_bullet("挂载目录路径写错，容器找不到文件 → 对比第五章 docker run 里的 -v 参数。")

add_h2("问题 3：提示 network chatbi-net not found")
add_p("按 5.3 节执行 docker network create chatbi-net 创建网络后重跑启动命令。")

add_h2("问题 4：端口被占用")
add_p("8123 或 8090 提示已被占用时，先看谁占着：")
add_code("""ss -lntp | grep 8123""")
add_p("找到占用进程后，停掉它或换一个端口（换端口要同时改 docker run 的 -p 和 nginx 的 proxy_pass、listen）。")

add_h2("问题 5：页面打不开 / 连接超时")
add_p("按顺序检查：")
add_bullet("nginx 是否在运行：systemctl status nginx；")
add_bullet("端口是否放行：防火墙 + 云平台安全组（见 6.5 节）；")
add_bullet("nginx 的 proxy_pass 是否指向了正确的 IP 和端口（见 6.3 节第 3 点）。")

add_h2("问题 6：页面能打开，但接口报 404")
add_p("几乎都是 proxy_pass 末尾少了斜杠 /，导致前缀没剥掉。检查 6.2 节配置，改成 http://127.0.0.1:8123/ 后 nginx -s reload。")

add_h2("问题 7：流式分析页面不逐字输出（一次性蹦出全部结果）")
add_p("确认 nginx 配置里 proxy_buffering off 和 proxy_cache off 两行存在，且改完后执行过 nginx -s reload。")

add_h2("问题 8：改了 .env 配置不生效")
add_p(".env 在容器启动时读取，修改宿主机上的 .env 后需要重启容器：")
add_code("""docker restart web-json""")

# =====================================================================
# 附录
# =====================================================================
add_h1("附录：常用 Docker 命令速查")
add_table(
    ["命令", "作用"],
    [
        ["docker images", "查看本机所有镜像"],
        ["docker ps", "查看运行中的容器"],
        ["docker ps -a", "查看所有容器（含已停止的）"],
        ["docker logs -f 容器名", "实时查看容器日志（Ctrl+C 退出）"],
        ["docker restart 容器名", "重启容器"],
        ["docker stop 容器名", "停止容器"],
        ["docker start 容器名", "启动已停止的容器"],
        ["docker rm 容器名", "删除容器（先 stop）"],
        ["docker rmi 镜像名:标签", "删除镜像"],
        ["docker network create 网络名", "创建网络"],
    ],
)

p = doc.add_paragraph()
p.paragraph_format.space_before = Pt(30)
run = p.add_run("—— 文档结束 ——")
set_run_font(run, size=10.5, color=DARK)
p.alignment = WD_ALIGN_PARAGRAPH.CENTER

doc.save(OUT_PATH)
print("saved:", OUT_PATH)
