# -*- coding: utf-8 -*-
"""增量更新《部署说明-web-json镜像加载与nginx配置.docx》

只在用户手工修改过的 docx 上做定点插入，不重跑 gen_deploy_doc.py（避免覆盖手工改动）：
  1. 4.3.3 提示词创建  → 补充提示词放置说明
  2. 新增 4.4 使用发布包一键完成（推荐）
  3. 7.2 数据库配置    → 补充「方式2、SQL 脚本方式」「方式3、使用一键部署脚本」
"""
import sys
sys.stdout.reconfigure(encoding='utf-8')
from docx import Document
from docx.shared import Pt
from docx.oxml.ns import qn
from docx.oxml import OxmlElement

DOC_PATH = r"D:\工作\公司\项目\问数\web-json\analyze-streaming-v2\docs\部署说明-web-json镜像加载与nginx配置.docx"
GRAY_FILL = "F2F2F2"


def set_run_font(run, name="Calibri", east="宋体", size=10.5, bold=False, color=None):
    run.font.name = name
    run.font.size = Pt(size)
    run.font.bold = bold
    if color is not None:
        run.font.color.rgb = color
    r = run._element.rPr.rFonts
    r.set(qn("w:eastAsia"), east)


def shade(paragraph, fill=GRAY_FILL):
    pPr = paragraph._p.get_or_add_pPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:val"), "clear")
    shd.set(qn("w:color"), "auto")
    shd.set(qn("w:fill"), fill)
    pPr.append(shd)


doc = Document(DOC_PATH)


def new_para(text="", style=None):
    """新建段落（先追加到文档末尾，之后统一移动到目标位置）"""
    p = doc.add_paragraph(style=style)
    if text:
        r = p.add_run(text)
        set_run_font(r)
    return p


def new_code(code):
    p = new_para()
    lines = code.strip("\n").split("\n")
    for i, line in enumerate(lines):
        run = p.add_run(line)
        set_run_font(run, name="Consolas", east="宋体", size=9)
        if i < len(lines) - 1:
            run.add_break()
    shade(p)
    return p


def new_note(text):
    p = new_para()
    r = p.add_run("【注意】" + text)
    set_run_font(r, size=10.5, bold=True, color=__import__("docx").shared.RGBColor(0xC0, 0x00, 0x00))
    return p


def new_tip(text):
    p = new_para()
    r = p.add_run("【提示】" + text)
    set_run_font(r, size=10.5, bold=True, color=__import__("docx").shared.RGBColor(0x1F, 0x4E, 0x79))
    return p


def new_bullet(text, bold_prefix=None):
    p = new_para(style="List Bullet")
    if bold_prefix:
        r1 = p.add_run(bold_prefix)
        set_run_font(r1, bold=True)
    r2 = p.add_run(text)
    set_run_font(r2)
    return p


def new_h2(text):
    p = new_para(text, style="Heading 2")
    return p


def insert_after(anchor, paras):
    """把 paras 依次插入到 anchor 段落后面（保持顺序）"""
    prev = anchor
    for p in paras:
        prev._p.addnext(p._p)
        prev = p


def find_para(exact=None, startswith=None):
    for p in doc.paragraphs:
        t = p.text.strip()
        if exact is not None and t == exact:
            return p
        if startswith is not None and t.startswith(startswith):
            return p
    raise ValueError(f"未找到锚点段落: exact={exact} startswith={startswith}")


# =====================================================================
# 1) 4.3.3 提示词创建 —— 补充内容
# =====================================================================
anchor = find_para(exact="4.3.3 提示词创建")
insert_after(anchor, [
    new_para("web-json 的提示词分「主提示词」和「总结提示词」两类。存放规则是：数据库的提示词版本表里记录版本号，提示词文件里存正文，两者必须配套。数据库导入的是 v1.0.0 脚本，提示词文件也必须放 v1.0.0 的，版本对不上会导致页面取不到提示词。"),
    new_para("提示词文件要放到服务器的挂载目录 /data/chatbi/service/web-json/prompts 下，目录结构如下："),
    new_code("""/data/chatbi/service/web-json/prompts/
├── prompt-main/          # 主提示词
│   └── v1.0.0.txt
└── prompt-summary/       # 总结提示词
    └── v1.0.0.txt"""),
    new_para("文件名里的 v1.0.0 就是版本号，必须与数据库脚本里的版本号一致。文件可以从随文档交付的发布包 publish/prompts/ 里拷贝（推荐直接使用 4.4 节的一键部署脚本，自动放好；也可以手动拷贝，在发布包解压目录下执行下面的命令）："),
    new_code("""# 在服务器上创建提示词子目录
mkdir -p /data/chatbi/service/web-json/prompts/prompt-main
mkdir -p /data/chatbi/service/web-json/prompts/prompt-summary

# 把发布包里的提示词文件拷贝到挂载目录
cp publish/prompts/prompt-main/v1.0.0.txt    /data/chatbi/service/web-json/prompts/prompt-main/
cp publish/prompts/prompt-summary/v1.0.0.txt /data/chatbi/service/web-json/prompts/prompt-summary/"""),
    new_tip("提示词文件放好后，需要重启容器才生效（docker restart web-json）。可执行下面的命令确认文件已放对位置："),
    new_code("ls -l /data/chatbi/service/web-json/prompts/prompt-main/ /data/chatbi/service/web-json/prompts/prompt-summary/"),
])

# =====================================================================
# 2) 新增 4.4 使用发布包一键完成（推荐）
# =====================================================================
anchor = find_para(startswith="第五章")
insert_after(anchor, [
    new_h2("4.4 使用发布包一键完成（推荐）"),
    new_para("开发团队会随本文档一起交付一个发布包 publish/，它把 4.2 建目录、4.3 写 .env、4.3.3 放提示词、以及 7.2 建库导入数据这几步全部做成了自动脚本。发布包结构如下："),
    new_code("""publish/
├── conf/                         # 配置文件
│   └── .env.example              # 配置模板（复制一份改名为 .env 再填写）
├── sql/
│   └── mysql_full_v1.0.0.sql     # 数据库全量脚本 v1.0.0（建表 + 初始数据 + 提示词版本记录）
├── prompts/                      # 提示词文件
│   ├── prompt-main/v1.0.0.txt
│   └── prompt-summary/v1.0.0.txt
└── deploy/
    └── install.sh                # 服务器端一键部署脚本"""),
    new_para("使用分两步。第一步：在你自己的电脑上，把 publish/conf/.env.example 复制一份改名为 .env，按 4.3.2 节的说明填好「必改」项；第二步：运行一键上传脚本 upload_publish.sh（脚本在项目根目录），它会自动把发布包打包上传到服务器并执行 install.sh："),
    new_code("""# 在你自己的电脑上执行（Windows 用 Git Bash，Mac 用终端）
# 格式：bash upload_publish.sh 用户名@服务器IP
bash upload_publish.sh qf@172.23.55.2"""),
    new_para("install.sh 会在服务器上依次自动完成："),
    new_bullet("创建部署目录 /data/chatbi/service/web-json/{logs, conf, prompts/...}；", bold_prefix="① "),
    new_bullet("安装 .env 到 conf/.env；", bold_prefix="② "),
    new_bullet("安装提示词 v1.0.0 到 prompts/；", bold_prefix="③ "),
    new_bullet("创建数据库并导入 mysql_full_v1.0.0.sql（前提：.env 里已填好数据库地址、账号、密码）。", bold_prefix="④ "),
    new_note("一键脚本只负责「放文件和建库」。容器仍要用第五章的 docker run 命令启动，nginx 仍按第六章配置，两者不能省略。"),
])

# =====================================================================
# 3) 7.2 数据库配置 —— 补充方式2 / 方式3
# =====================================================================
anchor = find_para(startswith="7.3 ")
insert_after(anchor, [
    new_para("方式2、SQL 脚本方式（发布包提供全量脚本）"),
    new_para("发布包 publish/sql/mysql_full_v1.0.0.sql 是 v1.0.0 的全量脚本（建表 + 初始数据 + 提示词版本记录）。若不用一键脚本，也可以手动执行。先登录数据库创建数据库（库名要与 .env 里的 MYSQL_DATABASE 一致）："),
    new_code("""CREATE DATABASE IF NOT EXISTS webapp DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"""),
    new_para("然后导入全量脚本。以命令行 mysql 客户端为例（需在服务器上安装 mysql 客户端；也可以用 Navicat / DBeaver 直接导入该 .sql 文件）："),
    new_code("""# 把脚本上传到服务器后执行（webapp 换成你自己的库名）
mysql -h 数据库IP -P 3306 -u 用户名 -p webapp < /tmp/publish/sql/mysql_full_v1.0.0.sql"""),
    new_note("导入的 SQL 脚本版本必须与提示词文件版本一致（都是 v1.0.0）。"),
    new_para("方式3、使用一键部署脚本（推荐）"),
    new_para("最简单的方式是直接用 4.4 节的 install.sh，它会自动完成「创建数据库 + 导入脚本」。若脚本因网络等原因未能自动建库，可先按方式2手动执行建库与导入命令，再重跑一遍 install.sh 即可。"),
])
doc.save(DOC_PATH)
print("增量更新完成，已保存。")
