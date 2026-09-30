"""
Mock System A - Fake Data Registry
====================================
Contains all test data for verification queries.
"""

# Each entry: keywords, optional entities, optional time_range, data (columns + rows)

DATA_REGISTRY = [
    # ========================================================================
    # 深圳用户规模快照（对应 view_hydwsdb_mk_mc_user_scale_quanliang）
    # ========================================================================
    {
        "keywords": ["全量用户", "用户总数", "用户规模", "用户数", "user_num",
                     "user_scale_quanliang", "深圳用户"],
        "data": {
            "columns": [
                {"name": "ptdate", "data_type": "date", "description": "数据日期"},
                {"name": "gdj", "data_type": "string", "description": "供电局"},
                {"name": "user_num", "data_type": "int", "description": "全量用户数"},
            ],
            "rows": [
                {"ptdate": "2026-09-18", "gdj": "市局本部", "user_num": 1250000},
                {"ptdate": "2026-09-18", "gdj": "福田供电局", "user_num": 860000},
                {"ptdate": "2026-09-18", "gdj": "罗湖供电局", "user_num": 620000},
                {"ptdate": "2026-09-18", "gdj": "南山供电局", "user_num": 980000},
                {"ptdate": "2026-09-17", "gdj": "市局本部", "user_num": 1248000},
                {"ptdate": "2026-09-17", "gdj": "福田供电局", "user_num": 859000},
                {"ptdate": "2026-09-17", "gdj": "罗湖供电局", "user_num": 619500},
                {"ptdate": "2026-09-17", "gdj": "南山供电局", "user_num": 978000},
                {"ptdate": "2026-09-16", "gdj": "市局本部", "user_num": 1246500},
                {"ptdate": "2026-09-16", "gdj": "福田供电局", "user_num": 858200},
                {"ptdate": "2026-09-16", "gdj": "罗湖供电局", "user_num": 619100},
                {"ptdate": "2026-09-16", "gdj": "南山供电局", "user_num": 976400},
            ],
        },
    },
    # ========================================================================
    # 深圳用电户规模快照（对应 view_yongdian_user_scale）
    # ========================================================================
    {
        "keywords": ["用电户", "用电用户", "usercount", "yongdian_user_scale",
                     "供电局用户"],
        "data": {
            "columns": [
                {"name": "ptdate", "data_type": "date", "description": "数据日期"},
                {"name": "gdj", "data_type": "string", "description": "供电局"},
                {"name": "usercount", "data_type": "int", "description": "用电户用户数"},
            ],
            "rows": [
                {"ptdate": "2026-09-18", "gdj": "市局本部", "usercount": 3580000},
                {"ptdate": "2026-09-18", "gdj": "福田供电局", "usercount": 1230000},
                {"ptdate": "2026-09-18", "gdj": "罗湖供电局", "usercount": 890000},
                {"ptdate": "2026-09-18", "gdj": "南山供电局", "usercount": 1460000},
                {"ptdate": "2026-09-17", "gdj": "市局本部", "usercount": 3576000},
                {"ptdate": "2026-09-17", "gdj": "福田供电局", "usercount": 1229000},
                {"ptdate": "2026-09-17", "gdj": "罗湖供电局", "usercount": 889500},
                {"ptdate": "2026-09-17", "gdj": "南山供电局", "usercount": 1458000},
                {"ptdate": "2026-09-16", "gdj": "市局本部", "usercount": 3572000},
                {"ptdate": "2026-09-16", "gdj": "福田供电局", "usercount": 1228500},
                {"ptdate": "2026-09-16", "gdj": "罗湖供电局", "usercount": 889000},
                {"ptdate": "2026-09-16", "gdj": "南山供电局", "usercount": 1456000},
            ],
        },
    },
    # 深圳 2026年7月 月累计供电量（复现 月累计 查询场景）
    {
        "keywords": ["深圳", "供电量", "月累计", "累计供电量", "2026-07", "2026年7月"],
        "data": {
            "columns": [
                {"name": "ptdate", "data_type": "date", "description": "日期"},
                {"name": "daypowersupply", "data_type": "float", "description": "日供电量"},
                {"name": "monthpowersupply", "data_type": "float", "description": "月累计供电量"},
            ],
            "rows": [
                {"ptdate": "2026-07-01", "daypowersupply": 1200.5, "monthpowersupply": 1200.5},
                {"ptdate": "2026-07-02", "daypowersupply": 1180.2, "monthpowersupply": 2380.7},
                {"ptdate": "2026-07-31", "daypowersupply": 1250.8, "monthpowersupply": 38900.6},
            ],
        },
    },
    # ========================================================================
    # SOUTH GRID PROJECT DATA
    # ========================================================================

    # Q1: 乌东德右岸电厂实际出力
    {
        "keywords": ["乌东德", "右岸", "实际出力"],
        "data": {
            "columns": [
                {"name": "plant_name", "data_type": "string", "description": "电厂名称"},
                {"name": "actual_output", "data_type": "float", "description": "实际出力MW"},
            ],
            "rows": [{"plant_name": "乌东德右岸电厂", "actual_output": 5200.0}],
        },
    },

    # Q2: 2025年6月1日广东跳闸线路
    {
        "keywords": ["广东", "跳闸", "线路", "2025-06-01", "停电时间"],
        "data": {
            "columns": [
                {"name": "line_name", "data_type": "string"},
                {"name": "outage_time", "data_type": "datetime"},
                {"name": "restore_time", "data_type": "datetime"},
            ],
            "rows": [
                {"line_name": "广深线I", "outage_time": "2025-06-01 08:30:00", "restore_time": "2025-06-01 10:15:00"},
                {"line_name": "珠海线III", "outage_time": "2025-06-01 14:20:00", "restore_time": "2025-06-01 15:45:00"},
                {"line_name": "东莞线II", "outage_time": "2025-06-01 09:05:00", "restore_time": "2025-06-01 12:30:00"},
            ],
        },
    },

    # Q3: 广东近三天风能最大最小出力
    {
        "keywords": ["广东", "风能", "出力", "最大", "最小"],
        "data": {
            "columns": [
                {"name": "date", "data_type": "date"},
                {"name": "max_output", "data_type": "float"},
                {"name": "min_output", "data_type": "float"},
            ],
            "rows": [
                {"date": "2026-02-28", "max_output": 3520.0, "min_output": 890.0},
                {"date": "2026-03-01", "max_output": 4100.0, "min_output": 1020.0},
                {"date": "2026-03-02", "max_output": 3850.0, "min_output": 760.0},
            ],
        },
    },

    # Q4: 广东跳闸线路+装机容量
    {
        "keywords": ["广东", "跳闸", "装机容量"],
        "data": {
            "columns": [
                {"name": "line_name", "data_type": "string"},
                {"name": "installed_capacity", "data_type": "float"},
                {"name": "outage_time", "data_type": "datetime"},
            ],
            "rows": [
                {"line_name": "广深线I", "installed_capacity": 500.0, "outage_time": "2025-06-01 08:30:00"},
                {"line_name": "珠海线III", "installed_capacity": 320.0, "outage_time": "2025-06-01 14:20:00"},
                {"line_name": "东莞线II", "installed_capacity": 450.0, "outage_time": "2025-06-01 09:05:00"},
            ],
        },
    },

    # Q5: 本月跳闸次数按天统计
    {
        "keywords": ["跳闸", "次数", "天", "日"],
        "data": {
            "columns": [
                {"name": "date", "data_type": "date"},
                {"name": "trip_count", "data_type": "int"},
            ],
            "rows": [
                {"date": "2026-03-01", "trip_count": 5},
                {"date": "2026-03-02", "trip_count": 8},
            ],
        },
    },

    # Q6: 本月新能源最大出力
    {
        "keywords": ["新能源", "最大出力", "省份"],
        "data": {
            "columns": [
                {"name": "province", "data_type": "string"},
                {"name": "max_renewable_output", "data_type": "float"},
            ],
            "rows": [
                {"province": "广东", "max_renewable_output": 12500.0},
                {"province": "云南", "max_renewable_output": 18200.0},
                {"province": "贵州", "max_renewable_output": 9800.0},
                {"province": "广西", "max_renewable_output": 7600.0},
                {"province": "海南", "max_renewable_output": 3200.0},
            ],
        },
    },

    # Q7: 跳闸排名前五调管机构
    {
        "keywords": ["跳闸", "调管机构", "排名"],
        "data": {
            "columns": [
                {"name": "dispatch_org", "data_type": "string"},
                {"name": "total_trips", "data_type": "int"},
            ],
            "rows": [
                {"dispatch_org": "广东调度", "total_trips": 156},
                {"dispatch_org": "云南调度", "total_trips": 132},
                {"dispatch_org": "贵州调度", "total_trips": 98},
                {"dispatch_org": "广西调度", "total_trips": 87},
                {"dispatch_org": "海南调度", "total_trips": 45},
                {"dispatch_org": "总调", "total_trips": 38},
            ],
        },
    },

    # Q8: 上月跳闸变电站
    {
        "keywords": ["跳闸", "变电站"],
        "data": {
            "columns": [
                {"name": "substation_name", "data_type": "string"},
                {"name": "trip_count", "data_type": "int"},
            ],
            "rows": [
                {"substation_name": "广州北变电站", "trip_count": 12},
                {"substation_name": "深圳南变电站", "trip_count": 9},
                {"substation_name": "珠海东变电站", "trip_count": 7},
                {"substation_name": "佛山西变电站", "trip_count": 5},
            ],
        },
    },

    # Q9: 全网统调负荷
    {
        "keywords": ["全网", "统调负荷"],
        "data": {
            "columns": [{"name": "dispatched_load", "data_type": "float"}],
            "rows": [{"dispatched_load": 215800.0}],
        },
    },

    # Q10: 全网含分布式最高统调
    {
        "keywords": ["全网", "系统负荷", "含分布式", "最高统调", "最大值"],
        "data": {
            "columns": [
                {"name": "date", "data_type": "date"},
                {"name": "max_load", "data_type": "float"},
            ],
            "rows": [{"date": "2026-01-15", "max_load": 238500.0}],
        },
    },

    # Q11: 近三日各省负荷率
    {
        "keywords": ["负荷率", "各省", "地调", "近三日", "平均"],
        "data": {
            "columns": [
                {"name": "province", "data_type": "string"},
                {"name": "date", "data_type": "date"},
                {"name": "avg_load_rate", "data_type": "float"},
                {"name": "load_rate", "data_type": "float"},
            ],
            "rows": [
                {"province": "广东", "date": "2026-02-28", "avg_load_rate": 78.5, "load_rate": 78.5},
                {"province": "广东", "date": "2026-03-01", "avg_load_rate": 80.2, "load_rate": 80.2},
                {"province": "广东", "date": "2026-03-02", "avg_load_rate": 79.1, "load_rate": 79.1},
                {"province": "广西", "date": "2026-02-28", "avg_load_rate": 72.3, "load_rate": 72.3},
                {"province": "广西", "date": "2026-03-01", "avg_load_rate": 73.8, "load_rate": 73.8},
                {"province": "广西", "date": "2026-03-02", "avg_load_rate": 71.9, "load_rate": 71.9},
                {"province": "云南", "date": "2026-02-28", "avg_load_rate": 68.1, "load_rate": 68.1},
                {"province": "云南", "date": "2026-03-01", "avg_load_rate": 69.5, "load_rate": 69.5},
                {"province": "云南", "date": "2026-03-02", "avg_load_rate": 67.8, "load_rate": 67.8},
                {"province": "贵州", "date": "2026-02-28", "avg_load_rate": 65.2, "load_rate": 65.2},
                {"province": "贵州", "date": "2026-03-01", "avg_load_rate": 66.1, "load_rate": 66.1},
                {"province": "贵州", "date": "2026-03-02", "avg_load_rate": 64.8, "load_rate": 64.8},
                {"province": "海南", "date": "2026-02-28", "avg_load_rate": 58.9, "load_rate": 58.9},
                {"province": "海南", "date": "2026-03-01", "avg_load_rate": 60.2, "load_rate": 60.2},
                {"province": "海南", "date": "2026-03-02", "avg_load_rate": 59.5, "load_rate": 59.5},
            ],
        },
    },

    # Q12: 非化石发电量
    {
        "keywords": ["非化石", "非火力", "发电量"],
        "data": {
            "columns": [
                {"name": "generation_type", "data_type": "string"},
                {"name": "total_generation", "data_type": "float"},
            ],
            "rows": [
                {"generation_type": "水电", "total_generation": 45200.0},
                {"generation_type": "风电", "total_generation": 18500.0},
                {"generation_type": "光伏", "total_generation": 12300.0},
                {"generation_type": "核电", "total_generation": 28600.0},
            ],
        },
    },

    # Q13: 广深地区最大统调负荷
    {
        "keywords": ["广深", "最大统调负荷", "最大负荷"],
        "data": {
            "columns": [{"name": "max_dispatched_load", "data_type": "float"}],
            "rows": [{"max_dispatched_load": 98500.0}],
        },
    },

    # Q14: 本月各类型发电量
    {
        "keywords": ["各类型", "发电量", "发电类型"],
        "data": {
            "columns": [
                {"name": "generation_type", "data_type": "string"},
                {"name": "generation_amount", "data_type": "float"},
            ],
            "rows": [
                {"generation_type": "火电", "generation_amount": 52000.0},
                {"generation_type": "水电", "generation_amount": 38000.0},
                {"generation_type": "风电", "generation_amount": 15200.0},
                {"generation_type": "光伏", "generation_amount": 9800.0},
                {"generation_type": "核电", "generation_amount": 22000.0},
                {"generation_type": "抽蓄", "generation_amount": 1200.0},
            ],
        },
    },

    # Q15: 2024年6月跳闸
    {
        "keywords": ["2024", "跳闸", "线路", "6月"],
        "data": {
            "columns": [
                {"name": "line_name", "data_type": "string"},
                {"name": "outage_time", "data_type": "datetime"},
            ],
            "rows": [
                {"line_name": "惠州线V", "outage_time": "2024-06-05 10:30:00"},
                {"line_name": "韶关线I", "outage_time": "2024-06-12 14:20:00"},
                {"line_name": "梅州线II", "outage_time": "2024-06-20 08:45:00"},
            ],
        },
    },

    # Q16: 停电复电时间间隔
    {
        "keywords": ["停电", "复电", "间隔", "时间", "停电时间", "复电时间", "2小时"],
        "data": {
            "columns": [
                {"name": "line_name", "data_type": "string"},
                {"name": "outage_time", "data_type": "datetime"},
                {"name": "restore_time", "data_type": "datetime"},
                {"name": "outage_start_time", "data_type": "datetime"},
                {"name": "restoration_end_time", "data_type": "datetime"},
            ],
            "rows": [
                {"line_name": "线路A", "outage_time": "2025-06-01 08:00:00", "restore_time": "2025-06-01 09:30:00", "outage_start_time": "2025-06-01 08:00:00", "restoration_end_time": "2025-06-01 09:30:00"},
                {"line_name": "线路B", "outage_time": "2025-06-01 10:00:00", "restore_time": "2025-06-01 13:00:00", "outage_start_time": "2025-06-01 10:00:00", "restoration_end_time": "2025-06-01 13:00:00"},
                {"line_name": "线路C", "outage_time": "2025-06-02 14:00:00", "restore_time": "2025-06-02 14:45:00", "outage_start_time": "2025-06-02 14:00:00", "restoration_end_time": "2025-06-02 14:45:00"},
                {"line_name": "线路D", "outage_time": "2025-06-03 09:00:00", "restore_time": "2025-06-03 12:30:00", "outage_start_time": "2025-06-03 09:00:00", "restoration_end_time": "2025-06-03 12:30:00"},
                {"line_name": "线路E", "outage_time": "2025-06-04 16:00:00", "restore_time": "2025-06-04 16:50:00", "outage_start_time": "2025-06-04 16:00:00", "restoration_end_time": "2025-06-04 16:50:00"},
            ],
        },
    },

    # Q17: 全网发电量
    {
        "keywords": ["全网", "发电量", "总发电量", "累计", "实际发电量", "今年"],
        "data": {
            "columns": [{"name": "total_generation", "data_type": "float"}, {"name": "actual_generation", "data_type": "float"}],
            "rows": [{"total_generation": 685000.0, "actual_generation": 685000.0}],
        },
    },

    # Q18: 集中式光伏发电量和统调发电
    {
        "keywords": ["集中式光伏", "统调发电"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "centralized_solar", "data_type": "float"},
                {"name": "total_dispatched", "data_type": "float"},
            ],
            "rows": [
                {"month": "2025-03", "centralized_solar": 2800.0, "total_dispatched": 58000.0},
                {"month": "2025-04", "centralized_solar": 3500.0, "total_dispatched": 55000.0},
                {"month": "2025-05", "centralized_solar": 4200.0, "total_dispatched": 62000.0},
                {"month": "2025-06", "centralized_solar": 4800.0, "total_dispatched": 68000.0},
            ],
        },
    },

    # Q19: 西电东送电量同比
    {
        "keywords": ["西电东送", "电量"],
        "data": {
            "columns": [
                {"name": "year", "data_type": "string"},
                {"name": "transmission_energy", "data_type": "float"},
            ],
            "rows": [
                {"year": "2023", "transmission_energy": 2305.0},
                {"year": "2024", "transmission_energy": 2456.0},
            ],
        },
    },

    # Q20: 各地区中调发电量用电量
    {
        "keywords": ["地区", "中调", "发电量", "用电量"],
        "data": {
            "columns": [
                {"name": "region", "data_type": "string"},
                {"name": "total_generation", "data_type": "float"},
                {"name": "total_consumption", "data_type": "float"},
            ],
            "rows": [
                {"region": "广东", "total_generation": 285000.0, "total_consumption": 310000.0},
                {"region": "广西", "total_generation": 98000.0, "total_consumption": 85000.0},
                {"region": "云南", "total_generation": 156000.0, "total_consumption": 92000.0},
                {"region": "贵州", "total_generation": 112000.0, "total_consumption": 88000.0},
                {"region": "海南", "total_generation": 34000.0, "total_consumption": 38000.0},
            ],
        },
    },

    # Q21: 新能源渗透率
    {
        "keywords": ["新能源", "渗透率"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "max_penetration_rate", "data_type": "float"},
            ],
            "rows": [
                {"month": "2026-01", "max_penetration_rate": 32.5},
                {"month": "2026-02", "max_penetration_rate": 28.8},
                {"month": "2026-03", "max_penetration_rate": 35.2},
                {"month": "2026-04", "max_penetration_rate": 38.6},
                {"month": "2026-05", "max_penetration_rate": 41.2},
                {"month": "2026-06", "max_penetration_rate": 45.0},
            ],
        },
    },

    # Q22: 上月全网最高统调负荷
    {
        "keywords": ["上月", "全网", "最高统调负荷"],
        "data": {
            "columns": [{"name": "max_load", "data_type": "float"}, {"name": "date", "data_type": "date"}],
            "rows": [{"max_load": 225600.0, "date": "2026-02-15"}],
        },
    },

    # Q23: 南方五省集中式光伏装机容量
    {
        "keywords": ["南方五省", "光伏", "装机容量"],
        "data": {
            "columns": [
                {"name": "province", "data_type": "string"},
                {"name": "installed_capacity", "data_type": "float"},
            ],
            "rows": [
                {"province": "广东", "installed_capacity": 18500.0},
                {"province": "广西", "installed_capacity": 12800.0},
                {"province": "云南", "installed_capacity": 22000.0},
                {"province": "贵州", "installed_capacity": 15600.0},
                {"province": "海南", "installed_capacity": 5800.0},
            ],
        },
    },

    # Q24: 不同地区同类能源装机容量
    {
        "keywords": ["地区", "能源", "装机容量"],
        "data": {
            "columns": [
                {"name": "region", "data_type": "string"},
                {"name": "generation_type", "data_type": "string"},
                {"name": "installed_capacity", "data_type": "float"},
            ],
            "rows": [
                {"region": "广东", "generation_type": "风电", "installed_capacity": 8500.0},
                {"region": "广东", "generation_type": "光伏", "installed_capacity": 18500.0},
                {"region": "广西", "generation_type": "风电", "installed_capacity": 6200.0},
                {"region": "广西", "generation_type": "光伏", "installed_capacity": 12800.0},
                {"region": "云南", "generation_type": "风电", "installed_capacity": 15000.0},
                {"region": "云南", "generation_type": "光伏", "installed_capacity": 22000.0},
                {"region": "贵州", "generation_type": "风电", "installed_capacity": 9800.0},
                {"region": "贵州", "generation_type": "光伏", "installed_capacity": 15600.0},
            ],
        },
    },

    # Q25: 计划和实际电量趋势
    {
        "keywords": ["计划电量", "实际电量", "趋势"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "planned_generation", "data_type": "float"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"month": "2025-01", "planned_generation": 55000.0, "actual_generation": 54200.0},
                {"month": "2025-02", "planned_generation": 48000.0, "actual_generation": 47500.0},
                {"month": "2025-03", "planned_generation": 52000.0, "actual_generation": 53800.0},
                {"month": "2025-04", "planned_generation": 50000.0, "actual_generation": 51200.0},
                {"month": "2025-05", "planned_generation": 58000.0, "actual_generation": 59500.0},
                {"month": "2025-06", "planned_generation": 65000.0, "actual_generation": 64800.0},
            ],
        },
    },

    # Q26: 同地区不同发电类型
    {
        "keywords": ["相同地区", "发电类型", "发电量"],
        "data": {
            "columns": [
                {"name": "region", "data_type": "string"},
                {"name": "generation_type", "data_type": "string"},
                {"name": "total_generation", "data_type": "float"},
            ],
            "rows": [
                {"region": "广东", "generation_type": "火电", "total_generation": 85000.0},
                {"region": "广东", "generation_type": "风电", "total_generation": 15200.0},
                {"region": "广东", "generation_type": "光伏", "total_generation": 18500.0},
                {"region": "广东", "generation_type": "核电", "total_generation": 28600.0},
                {"region": "云南", "generation_type": "水电", "total_generation": 95000.0},
                {"region": "云南", "generation_type": "风电", "total_generation": 22000.0},
                {"region": "云南", "generation_type": "光伏", "total_generation": 15000.0},
            ],
        },
    },
]
