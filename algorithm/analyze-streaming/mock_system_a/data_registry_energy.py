"""
Mock System A - Energy Company Data Registry (国能投资集团)
"""

ENERGY_DATA_REGISTRY = [
    # E-Q1: 发电计划超发
    {
        "keywords": ["发电计划", "实际发电量", "超发", "各场站"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "planned_generation", "data_type": "float"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "planned_generation": 1200.0, "actual_generation": 1350.0},
                {"station_name": "华北风电场B", "planned_generation": 980.0, "actual_generation": 920.0},
                {"station_name": "华东风电场C", "planned_generation": 1500.0, "actual_generation": 1680.0},
                {"station_name": "华东风电场D", "planned_generation": 800.0, "actual_generation": 750.0},
                {"station_name": "华南光伏站E", "planned_generation": 1100.0, "actual_generation": 1250.0},
                {"station_name": "华南光伏站F", "planned_generation": 600.0, "actual_generation": 580.0},
                {"station_name": "西北风电场G", "planned_generation": 900.0, "actual_generation": 1050.0},
                {"station_name": "西北风电场H", "planned_generation": 1300.0, "actual_generation": 1280.0},
            ],
        },
    },

    # E-Q2: 能量利用率月度
    {
        "keywords": ["能量利用率", "月", "连续"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "energy_utilization", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "month": "2025-01", "energy_utilization": 95.2},
                {"station_name": "华北风电场A", "month": "2025-02", "energy_utilization": 94.8},
                {"station_name": "华北风电场A", "month": "2025-03", "energy_utilization": 93.1},
                {"station_name": "华北风电场A", "month": "2025-04", "energy_utilization": 91.5},
                {"station_name": "华北风电场A", "month": "2025-05", "energy_utilization": 90.2},
                {"station_name": "华北风电场B", "month": "2025-01", "energy_utilization": 92.0},
                {"station_name": "华北风电场B", "month": "2025-02", "energy_utilization": 93.5},
                {"station_name": "华北风电场B", "month": "2025-03", "energy_utilization": 91.8},
                {"station_name": "华北风电场B", "month": "2025-04", "energy_utilization": 90.2},
                {"station_name": "华北风电场B", "month": "2025-05", "energy_utilization": 89.5},
                {"station_name": "华东风电场C", "month": "2025-01", "energy_utilization": 96.0},
                {"station_name": "华东风电场C", "month": "2025-02", "energy_utilization": 96.5},
                {"station_name": "华东风电场C", "month": "2025-03", "energy_utilization": 95.8},
                {"station_name": "华东风电场C", "month": "2025-04", "energy_utilization": 96.2},
                {"station_name": "华东风电场C", "month": "2025-05", "energy_utilization": 95.5},
            ],
        },
    },

    # E-Q3: 机组可利用率
    {
        "keywords": ["机组可利用率", "风电场"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "unit_availability", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "unit_availability": 96.5},
                {"station_name": "华北风电场B", "unit_availability": 94.2},
                {"station_name": "华东风电场C", "unit_availability": 97.8},
                {"station_name": "华东风电场D", "unit_availability": 93.5},
                {"station_name": "西北风电场G", "unit_availability": 95.8},
                {"station_name": "西北风电场H", "unit_availability": 92.1},
            ],
        },
    },

    # E-Q4: 弃风率+装机容量
    {
        "keywords": ["弃风率", "装机容量", "季度", "环比"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "quarter", "data_type": "string"},
                {"name": "curtailment_rate", "data_type": "float"},
                {"name": "installed_capacity", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "quarter": "2025-Q2", "curtailment_rate": 8.5, "installed_capacity": 200.0},
                {"station_name": "华北风电场A", "quarter": "2025-Q3", "curtailment_rate": 2.0, "installed_capacity": 200.0},
                {"station_name": "华北风电场B", "quarter": "2025-Q2", "curtailment_rate": 12.0, "installed_capacity": 150.0},
                {"station_name": "华北风电场B", "quarter": "2025-Q3", "curtailment_rate": 5.5, "installed_capacity": 150.0},
                {"station_name": "华东风电场C", "quarter": "2025-Q2", "curtailment_rate": 6.2, "installed_capacity": 180.0},
                {"station_name": "华东风电场C", "quarter": "2025-Q3", "curtailment_rate": 3.8, "installed_capacity": 180.0},
                {"station_name": "西北风电场G", "quarter": "2025-Q2", "curtailment_rate": 15.0, "installed_capacity": 120.0},
                {"station_name": "西北风电场G", "quarter": "2025-Q3", "curtailment_rate": 8.2, "installed_capacity": 120.0},
            ],
        },
    },

    # E-Q5: 夏季限电率
    {
        "keywords": ["夏季", "限电率", "连续两年"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "year", "data_type": "string"},
                {"name": "summer_avg_curtailment_rate", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "year": "2024", "summer_avg_curtailment_rate": 8.5},
                {"station_name": "华北风电场A", "year": "2025", "summer_avg_curtailment_rate": 9.2},
                {"station_name": "华北风电场B", "year": "2024", "summer_avg_curtailment_rate": 12.0},
                {"station_name": "华北风电场B", "year": "2025", "summer_avg_curtailment_rate": 13.5},
                {"station_name": "华东风电场C", "year": "2024", "summer_avg_curtailment_rate": 5.8},
                {"station_name": "华东风电场C", "year": "2025", "summer_avg_curtailment_rate": 4.2},
                {"station_name": "西北风电场G", "year": "2024", "summer_avg_curtailment_rate": 15.2},
                {"station_name": "西北风电场G", "year": "2025", "summer_avg_curtailment_rate": 16.8},
            ],
        },
    },

    # E-Q6: 电价同比
    {
        "keywords": ["电价", "同比", "子分公司"],
        "data": {
            "columns": [
                {"name": "subsidiary", "data_type": "string"},
                {"name": "year", "data_type": "string"},
                {"name": "trade_price", "data_type": "float"},
            ],
            "rows": [
                {"subsidiary": "华北公司", "year": "2024", "trade_price": 0.385},
                {"subsidiary": "华北公司", "year": "2025", "trade_price": 0.365},
                {"subsidiary": "华东公司", "year": "2024", "trade_price": 0.412},
                {"subsidiary": "华东公司", "year": "2025", "trade_price": 0.398},
                {"subsidiary": "华南公司", "year": "2024", "trade_price": 0.395},
                {"subsidiary": "华南公司", "year": "2025", "trade_price": 0.388},
                {"subsidiary": "西北公司", "year": "2024", "trade_price": 0.358},
                {"subsidiary": "西北公司", "year": "2025", "trade_price": 0.325},
                {"subsidiary": "西南公司", "year": "2024", "trade_price": 0.372},
                {"subsidiary": "西南公司", "year": "2025", "trade_price": 0.360},
            ],
        },
    },

    # E-Q7: 发电量完成率
    {
        "keywords": ["发电量", "完成率", "实时"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "planned_generation", "data_type": "float"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "planned_generation": 5000.0, "actual_generation": 4250.0},
                {"station_name": "华北风电场B", "planned_generation": 4200.0, "actual_generation": 3800.0},
                {"station_name": "华东风电场C", "planned_generation": 6000.0, "actual_generation": 5500.0},
                {"station_name": "华东风电场D", "planned_generation": 3500.0, "actual_generation": 2900.0},
            ],
        },
    },

    # E-Q8: 子分公司交易电价
    {
        "keywords": ["子分公司", "交易电价", "本月"],
        "data": {
            "columns": [
                {"name": "subsidiary", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "trade_price", "data_type": "float"},
            ],
            "rows": [
                {"subsidiary": "华北公司", "month": "2026-03", "trade_price": 0.362},
                {"subsidiary": "华北公司", "month": "2025-03", "trade_price": 0.385},
            ],
        },
    },

    # E-Q9: 故障次数同期比对
    {
        "keywords": ["故障次数", "故障时间", "台均故障", "同期"],
        "data": {
            "columns": [
                {"name": "year", "data_type": "string"},
                {"name": "fault_count", "data_type": "int"},
                {"name": "fault_duration", "data_type": "float"},
                {"name": "fault_per_unit", "data_type": "float"},
            ],
            "rows": [
                {"year": "2024", "fault_count": 245, "fault_duration": 4.2, "fault_per_unit": 2.8},
                {"year": "2025", "fault_count": 198, "fault_duration": 3.8, "fault_per_unit": 2.3},
            ],
        },
    },

    # E-Q10: 同一风场内风机发电量
    {
        "keywords": ["风机", "发电量", "最低", "同一风场", "风机编号", "风机发电"],
        "data": {
            "columns": [
                {"name": "turbine_id", "data_type": "string"},
                {"name": "station_name", "data_type": "string"},
                {"name": "generation", "data_type": "float"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"turbine_id": "WTG-001", "station_name": "华北风电场A", "generation": 280.0, "actual_generation": 280.0},
                {"turbine_id": "WTG-002", "station_name": "华北风电场A", "generation": 185.0, "actual_generation": 185.0},
                {"turbine_id": "WTG-003", "station_name": "华北风电场A", "generation": 310.0, "actual_generation": 310.0},
                {"turbine_id": "WTG-004", "station_name": "华北风电场A", "generation": 150.0, "actual_generation": 150.0},
                {"turbine_id": "WTG-005", "station_name": "华北风电场A", "generation": 295.0, "actual_generation": 295.0},
                {"turbine_id": "WTG-006", "station_name": "华北风电场A", "generation": 220.0, "actual_generation": 220.0},
                {"turbine_id": "WTG-007", "station_name": "华北风电场A", "generation": 165.0, "actual_generation": 165.0},
                {"turbine_id": "WTG-008", "station_name": "华北风电场A", "generation": 245.0, "actual_generation": 245.0},
                {"turbine_id": "WTG-009", "station_name": "华北风电场A", "generation": 178.0, "actual_generation": 178.0},
                {"turbine_id": "WTG-010", "station_name": "华北风电场A", "generation": 330.0, "actual_generation": 330.0},
            ],
        },
    },

    # E-Q10b: 各场站限电率（用于排名）
    {
        "keywords": ["限电率", "各场站", "排名", "前三", "排名前三", "场站限电率", "curtailment_rate", "各场站限电率", "限电率排名", "限电率是多少"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "curtailment_rate", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "西北风电场G", "curtailment_rate": 15.0},
                {"station_name": "华北风电场B", "curtailment_rate": 12.0},
                {"station_name": "华北风电场A", "curtailment_rate": 8.5},
                {"station_name": "华东风电场C", "curtailment_rate": 6.2},
                {"station_name": "华东风电场D", "curtailment_rate": 4.5},
                {"station_name": "华南光伏站E", "curtailment_rate": 3.8},
                {"station_name": "华南光伏站F", "curtailment_rate": 2.5},
                {"station_name": "西北风电场H", "curtailment_rate": 5.2},
            ],
        },
    },

    # E-Q11: 各场限电率环比
    {
        "keywords": ["限电率", "环比", "增幅"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "curtailment_rate", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "month": "2025-11", "curtailment_rate": 5.2},
                {"station_name": "华北风电场A", "month": "2025-12", "curtailment_rate": 7.8},
                {"station_name": "华北风电场B", "month": "2025-11", "curtailment_rate": 8.5},
                {"station_name": "华北风电场B", "month": "2025-12", "curtailment_rate": 12.0},
                {"station_name": "华东风电场C", "month": "2025-11", "curtailment_rate": 3.2},
                {"station_name": "华东风电场C", "month": "2025-12", "curtailment_rate": 4.5},
                {"station_name": "西北风电场G", "month": "2025-11", "curtailment_rate": 12.5},
                {"station_name": "西北风电场G", "month": "2025-12", "curtailment_rate": 18.2},
            ],
        },
    },

    # E-Q12: 湖北风电利用小时数
    {
        "keywords": ["湖北", "风电利用小时数", "近五年"],
        "data": {
            "columns": [
                {"name": "year", "data_type": "string"},
                {"name": "wind_utilization_hours", "data_type": "float"},
            ],
            "rows": [
                {"year": "2021", "wind_utilization_hours": 1980},
                {"year": "2022", "wind_utilization_hours": 2050},
                {"year": "2023", "wind_utilization_hours": 2120},
                {"year": "2024", "wind_utilization_hours": 2085},
                {"year": "2025", "wind_utilization_hours": 2150},
            ],
        },
    },

    # E-Q13: 光伏站平均利用小时数
    {
        "keywords": ["光伏", "平均利用小时数", "集团"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "available_hours", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华南光伏站E", "available_hours": 1280.0},
                {"station_name": "华南光伏站F", "available_hours": 1150.0},
                {"station_name": "西北光伏站I", "available_hours": 1420.0},
                {"station_name": "西北光伏站J", "available_hours": 1380.0},
            ],
        },
    },

    # E-Q14: 各场站生产运行数据
    {
        "keywords": ["各场站", "生产运行", "同比", "环比"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "month": "2025-10", "actual_generation": 450.0},
                {"station_name": "华北风电场A", "month": "2025-11", "actual_generation": 480.0},
                {"station_name": "华北风电场A", "month": "2025-12", "actual_generation": 520.0},
                {"station_name": "华北风电场A", "month": "2024-10", "actual_generation": 430.0},
                {"station_name": "华北风电场A", "month": "2024-11", "actual_generation": 460.0},
                {"station_name": "华北风电场A", "month": "2024-12", "actual_generation": 500.0},
                {"station_name": "华北风电场B", "month": "2025-10", "actual_generation": 380.0},
                {"station_name": "华北风电场B", "month": "2025-11", "actual_generation": 350.0},
                {"station_name": "华北风电场B", "month": "2025-12", "actual_generation": 410.0},
                {"station_name": "华北风电场B", "month": "2024-10", "actual_generation": 390.0},
                {"station_name": "华北风电场B", "month": "2024-11", "actual_generation": 370.0},
                {"station_name": "华北风电场B", "month": "2024-12", "actual_generation": 395.0},
            ],
        },
    },

    # E-Q15: 风速和辐照度
    {
        "keywords": ["风速", "辐照度", "平均"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "avg_wind_speed", "data_type": "float"},
                {"name": "avg_irradiance", "data_type": "float"},
            ],
            "rows": [
                {"month": "2025-04", "avg_wind_speed": 5.8, "avg_irradiance": 520.0},
                {"month": "2025-05", "avg_wind_speed": 5.2, "avg_irradiance": 580.0},
                {"month": "2025-06", "avg_wind_speed": 4.8, "avg_irradiance": 620.0},
                {"month": "2025-07", "avg_wind_speed": 4.5, "avg_irradiance": 650.0},
                {"month": "2025-08", "avg_wind_speed": 4.9, "avg_irradiance": 610.0},
                {"month": "2025-09", "avg_wind_speed": 5.5, "avg_irradiance": 550.0},
                {"month": "2025-10", "avg_wind_speed": 6.2, "avg_irradiance": 480.0},
                {"month": "2025-11", "avg_wind_speed": 6.8, "avg_irradiance": 420.0},
                {"month": "2025-12", "avg_wind_speed": 7.1, "avg_irradiance": 380.0},
                {"month": "2026-01", "avg_wind_speed": 7.5, "avg_irradiance": 350.0},
                {"month": "2026-02", "avg_wind_speed": 6.9, "avg_irradiance": 410.0},
                {"month": "2026-03", "avg_wind_speed": 6.3, "avg_irradiance": 470.0},
            ],
        },
    },

    # E-Q16: 限电量和装机容量比值
    {
        "keywords": ["限电量", "装机容量", "比值", "排名"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "curtailment_energy", "data_type": "float"},
                {"name": "installed_capacity", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "curtailment_energy": 85.0, "installed_capacity": 200.0},
                {"station_name": "华北风电场B", "curtailment_energy": 120.0, "installed_capacity": 150.0},
                {"station_name": "华东风电场C", "curtailment_energy": 45.0, "installed_capacity": 180.0},
                {"station_name": "西北风电场G", "curtailment_energy": 180.0, "installed_capacity": 120.0},
                {"station_name": "西北风电场H", "curtailment_energy": 95.0, "installed_capacity": 250.0},
            ],
        },
    },

    # E-Q17: 理论和实际发电量差值
    {
        "keywords": ["理论发电量", "实际发电量", "差值", "月度"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "theoretical_generation", "data_type": "float"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "month": "2025-10", "theoretical_generation": 500.0, "actual_generation": 450.0},
                {"station_name": "华北风电场A", "month": "2025-11", "theoretical_generation": 520.0, "actual_generation": 480.0},
                {"station_name": "华北风电场A", "month": "2025-12", "theoretical_generation": 550.0, "actual_generation": 520.0},
                {"station_name": "华北风电场B", "month": "2025-10", "theoretical_generation": 420.0, "actual_generation": 380.0},
                {"station_name": "华北风电场B", "month": "2025-11", "theoretical_generation": 400.0, "actual_generation": 350.0},
                {"station_name": "华北风电场B", "month": "2025-12", "theoretical_generation": 450.0, "actual_generation": 410.0},
            ],
        },
    },

    # E-Q18: 某发电场实际vs计划差值
    {
        "keywords": ["发电场", "实际发电量", "计划发电量", "差值", "12月"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "planned_generation", "data_type": "float"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [{"station_name": "华北风电场A", "planned_generation": 550.0, "actual_generation": 520.0}],
        },
    },

    # E-Q19: 哈里伯顿天然气价格
    {
        "keywords": ["天然气", "现货价格", "哈里伯顿", "2022"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "gas_spot_price", "data_type": "float"},
            ],
            "rows": [
                {"month": "2022-01", "gas_spot_price": 4.38},
                {"month": "2022-02", "gas_spot_price": 4.69},
                {"month": "2022-03", "gas_spot_price": 4.95},
                {"month": "2022-04", "gas_spot_price": 6.60},
                {"month": "2022-05", "gas_spot_price": 8.14},
                {"month": "2022-06", "gas_spot_price": 8.69},
                {"month": "2022-07", "gas_spot_price": 7.28},
                {"month": "2022-08", "gas_spot_price": 9.33},
                {"month": "2022-09", "gas_spot_price": 7.88},
                {"month": "2022-10", "gas_spot_price": 5.50},
                {"month": "2022-11", "gas_spot_price": 5.56},
                {"month": "2022-12", "gas_spot_price": 5.39},
            ],
        },
    },

    # E-Q20: 风电度电成本趋势
    {
        "keywords": ["度电成本", "风电", "趋势", "变化"],
        "data": {
            "columns": [
                {"name": "year", "data_type": "string"},
                {"name": "cost_per_kwh", "data_type": "float"},
            ],
            "rows": [
                {"year": "2023", "cost_per_kwh": 0.285},
                {"year": "2024", "cost_per_kwh": 0.268},
                {"year": "2025", "cost_per_kwh": 0.252},
            ],
        },
    },

    # E-Q21: 风电/光伏电价趋势
    {
        "keywords": ["电价", "趋势", "变化"],
        "data": {
            "columns": [
                {"name": "year", "data_type": "string"},
                {"name": "generation_type", "data_type": "string"},
                {"name": "avg_price", "data_type": "float"},
            ],
            "rows": [
                {"year": "2022", "generation_type": "风电", "avg_price": 0.42},
                {"year": "2023", "generation_type": "风电", "avg_price": 0.39},
                {"year": "2024", "generation_type": "风电", "avg_price": 0.37},
                {"year": "2025", "generation_type": "风电", "avg_price": 0.35},
                {"year": "2022", "generation_type": "光伏", "avg_price": 0.38},
                {"year": "2023", "generation_type": "光伏", "avg_price": 0.35},
                {"year": "2024", "generation_type": "光伏", "avg_price": 0.33},
                {"year": "2025", "generation_type": "光伏", "avg_price": 0.31},
            ],
        },
    },

    # E-Q22: 日照光强度
    {
        "keywords": ["日照", "光强度", "北京"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "solar_intensity", "data_type": "float"},
            ],
            "rows": [
                {"month": "2025-01", "solar_intensity": 320.0},
                {"month": "2025-02", "solar_intensity": 380.0},
                {"month": "2025-03", "solar_intensity": 450.0},
                {"month": "2025-04", "solar_intensity": 520.0},
                {"month": "2025-05", "solar_intensity": 580.0},
                {"month": "2025-06", "solar_intensity": 620.0},
                {"month": "2025-07", "solar_intensity": 640.0},
                {"month": "2025-08", "solar_intensity": 600.0},
                {"month": "2025-09", "solar_intensity": 520.0},
                {"month": "2025-10", "solar_intensity": 430.0},
                {"month": "2025-11", "solar_intensity": 340.0},
                {"month": "2025-12", "solar_intensity": 300.0},
            ],
        },
    },

    # E-Q23: 连续3天低于目标值+分析数据
    {
        "keywords": ["连续", "天", "发电量", "目标值", "低于"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "date", "data_type": "date"},
                {"name": "daily_generation", "data_type": "float"},
                {"name": "target_generation", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "date": "2025-10-05", "daily_generation": 12.0, "target_generation": 15.0},
                {"station_name": "华北风电场A", "date": "2025-10-06", "daily_generation": 11.5, "target_generation": 15.0},
                {"station_name": "华北风电场A", "date": "2025-10-07", "daily_generation": 10.8, "target_generation": 15.0},
                {"station_name": "华北风电场A", "date": "2025-10-08", "daily_generation": 13.2, "target_generation": 15.0},
                {"station_name": "华北风电场B", "date": "2025-10-10", "daily_generation": 9.5, "target_generation": 12.0},
                {"station_name": "华北风电场B", "date": "2025-10-11", "daily_generation": 8.8, "target_generation": 12.0},
                {"station_name": "华北风电场B", "date": "2025-10-12", "daily_generation": 9.2, "target_generation": 12.0},
                {"station_name": "华北风电场B", "date": "2025-10-13", "daily_generation": 11.5, "target_generation": 12.0},
            ],
        },
    },

    # E-Q23 supplementary: wind speed
    {
        "keywords": ["风速", "10月", "日"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "date", "data_type": "date"},
                {"name": "avg_wind_speed", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "date": "2025-10-05", "avg_wind_speed": 3.2},
                {"station_name": "华北风电场A", "date": "2025-10-06", "avg_wind_speed": 2.8},
                {"station_name": "华北风电场A", "date": "2025-10-07", "avg_wind_speed": 3.0},
                {"station_name": "华北风电场B", "date": "2025-10-10", "avg_wind_speed": 2.5},
                {"station_name": "华北风电场B", "date": "2025-10-11", "avg_wind_speed": 2.2},
                {"station_name": "华北风电场B", "date": "2025-10-12", "avg_wind_speed": 2.8},
            ],
        },
    },

    # E-Q24: 限电形势
    {
        "keywords": ["限电", "形势", "场站", "公司"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "curtailment_rate", "data_type": "float"},
                {"name": "curtailment_energy", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "month": "2025-01", "curtailment_rate": 5.2, "curtailment_energy": 28.0},
                {"station_name": "华北风电场A", "month": "2025-06", "curtailment_rate": 8.5, "curtailment_energy": 45.0},
                {"station_name": "华北风电场A", "month": "2025-12", "curtailment_rate": 7.8, "curtailment_energy": 42.0},
                {"station_name": "华北风电场B", "month": "2025-01", "curtailment_rate": 8.5, "curtailment_energy": 35.0},
                {"station_name": "华北风电场B", "month": "2025-06", "curtailment_rate": 12.0, "curtailment_energy": 52.0},
                {"station_name": "华北风电场B", "month": "2025-12", "curtailment_rate": 12.0, "curtailment_energy": 50.0},
            ],
        },
    },

    # E-Q25: 可利用小时数
    {
        "keywords": ["可利用小时数", "各场站", "年度"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "available_hours", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "available_hours": 2150.0},
                {"station_name": "华北风电场B", "available_hours": 1850.0},
                {"station_name": "华东风电场C", "available_hours": 2380.0},
                {"station_name": "华东风电场D", "available_hours": 1920.0},
                {"station_name": "华南光伏站E", "available_hours": 1680.0},
                {"station_name": "华南光伏站F", "available_hours": 1520.0},
                {"station_name": "西北风电场G", "available_hours": 2050.0},
                {"station_name": "西北风电场H", "available_hours": 1780.0},
            ],
        },
    },

    # E-Q25 supplementary: 风速数据
    {
        "keywords": ["月均风速", "低于均值"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "avg_wind_speed", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场B", "month": "2025-06", "avg_wind_speed": 4.2},
                {"station_name": "华北风电场B", "month": "2025-12", "avg_wind_speed": 5.8},
                {"station_name": "华南光伏站F", "month": "2025-06", "avg_wind_speed": 3.5},
                {"station_name": "华南光伏站F", "month": "2025-12", "avg_wind_speed": 4.1},
            ],
        },
    },

    # E-Q25 supplementary: 故障记录
    {
        "keywords": ["故障记录", "故障", "停机"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "fault_count", "data_type": "int"},
                {"name": "total_downtime_hours", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场B", "fault_count": 28, "total_downtime_hours": 156.0},
                {"station_name": "华南光伏站F", "fault_count": 15, "total_downtime_hours": 82.0},
                {"station_name": "西北风电场H", "fault_count": 32, "total_downtime_hours": 198.0},
            ],
        },
    },

    # E-Q26: 同比下降环比上升的异常
    {
        "keywords": ["同比下降", "环比上升", "异常", "月份"],
        "data": {
            "columns": [
                {"name": "station_name", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "actual_generation", "data_type": "float"},
            ],
            "rows": [
                {"station_name": "华北风电场A", "month": "2024-01", "actual_generation": 450.0},
                {"station_name": "华北风电场A", "month": "2024-02", "actual_generation": 420.0},
                {"station_name": "华北风电场A", "month": "2024-03", "actual_generation": 480.0},
                {"station_name": "华北风电场A", "month": "2025-01", "actual_generation": 430.0},
                {"station_name": "华北风电场A", "month": "2025-02", "actual_generation": 380.0},
                {"station_name": "华北风电场A", "month": "2025-03", "actual_generation": 460.0},
                {"station_name": "华北风电场B", "month": "2024-01", "actual_generation": 380.0},
                {"station_name": "华北风电场B", "month": "2024-02", "actual_generation": 360.0},
                {"station_name": "华北风电场B", "month": "2024-03", "actual_generation": 400.0},
                {"station_name": "华北风电场B", "month": "2025-01", "actual_generation": 370.0},
                {"station_name": "华北风电场B", "month": "2025-02", "actual_generation": 340.0},
                {"station_name": "华北风电场B", "month": "2025-03", "actual_generation": 390.0},
            ],
        },
    },

    # E-Q27: 风能利用率排名
    {
        "keywords": ["风能利用率", "排名", "子分公司"],
        "data": {
            "columns": [
                {"name": "subsidiary", "data_type": "string"},
                {"name": "wind_energy_utilization", "data_type": "float"},
            ],
            "rows": [
                {"subsidiary": "华北公司", "wind_energy_utilization": 92.5},
                {"subsidiary": "华东公司", "wind_energy_utilization": 95.2},
                {"subsidiary": "华南公司", "wind_energy_utilization": 88.3},
                {"subsidiary": "西北公司", "wind_energy_utilization": 85.6},
                {"subsidiary": "西南公司", "wind_energy_utilization": 90.8},
            ],
        },
    },
]
