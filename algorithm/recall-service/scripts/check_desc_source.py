# -*- coding: utf-8 -*-
"""临时排查：备份库里描述类字段的填充情况（用后即删）"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))
import pymysql
from app import config

conn = pymysql.connect(host=config.MYSQL_HOST, port=config.MYSQL_PORT,
                       user=config.MYSQL_USER, password=config.MYSQL_PASSWORD,
                       database=config.MYSQL_DB, charset="utf8mb4",
                       cursorclass=pymysql.cursors.DictCursor)
cur = conn.cursor()
checks = [
    ("olap_basic_pro_indicator", "caliber_description"),
    ("olap_basic_pro_indicator", "unit"),
    ("olap_table_pro", "note"),
    ("olap_basic_pro_dimension", "description"),
    ("olap_basic_pro_dimension", "dimension_values"),
    ("ai_body_knowledge_info", "knowledge_content"),
]
for tbl, col in checks:
    try:
        cur.execute(f"SELECT COUNT(*) AS c, SUM({col} IS NULL OR {col}='') AS e FROM {tbl}")
        r = cur.fetchone()
        print(f"{tbl}.{col}: 总{r['c']} 空{r['e']}")
    except Exception as ex:
        print(f"{tbl}.{col}: ERR {str(ex)[:90]}")

cur.execute("SELECT basic_code, LEFT(caliber_description, 60) AS d FROM olap_basic_pro_indicator "
            "WHERE caliber_description IS NOT NULL AND caliber_description<>'' LIMIT 3")
for r in cur.fetchall():
    print("样例:", r)
conn.close()
