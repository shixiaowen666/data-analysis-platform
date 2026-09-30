"""
Mock System A - Hospital Data Registry
"""

HOSPITAL_DATA_REGISTRY = [
    # Hospital Q1/Q2: 各科成本增幅
    {
        "keywords": ["成本", "增幅", "科室", "各科", "增长", "成本增幅", "成本构成", "人力成本", "药品成本", "高值卫生材料", "低值卫生材料", "总务消耗品", "成本增长", "哪部分", "增幅最大", "1-4月", "各科成本"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "cost_category", "data_type": "string"},
                {"name": "current_amount", "data_type": "float"},
                {"name": "previous_amount", "data_type": "float"},
                {"name": "growth_rate", "data_type": "float"},
                {"name": "change_rate", "data_type": "float"},
                {"name": "relative_change", "data_type": "float"},
            ],
            "rows": [
                {"department": "心内科", "cost_category": "人力成本", "current_amount": 850.0, "previous_amount": 800.0, "growth_rate": 6.25, "change_rate": 6.25, "relative_change": 6.25},
                {"department": "心内科", "cost_category": "药品成本", "current_amount": 620.0, "previous_amount": 550.0, "growth_rate": 12.73, "change_rate": 12.73, "relative_change": 12.73},
                {"department": "心内科", "cost_category": "高值卫生材料", "current_amount": 480.0, "previous_amount": 420.0, "growth_rate": 14.29, "change_rate": 14.29, "relative_change": 14.29},
                {"department": "心内科", "cost_category": "低值卫生材料", "current_amount": 120.0, "previous_amount": 110.0, "growth_rate": 9.09, "change_rate": 9.09, "relative_change": 9.09},
                {"department": "心内科", "cost_category": "总务消耗品", "current_amount": 65.0, "previous_amount": 60.0, "growth_rate": 8.33, "change_rate": 8.33, "relative_change": 8.33},
                {"department": "心外科", "cost_category": "人力成本", "current_amount": 920.0, "previous_amount": 880.0, "growth_rate": 4.55, "change_rate": 4.55, "relative_change": 4.55},
                {"department": "心外科", "cost_category": "药品成本", "current_amount": 580.0, "previous_amount": 520.0, "growth_rate": 11.54, "change_rate": 11.54, "relative_change": 11.54},
                {"department": "心外科", "cost_category": "高值卫生材料", "current_amount": 750.0, "previous_amount": 600.0, "growth_rate": 25.0, "change_rate": 25.0, "relative_change": 25.0},
                {"department": "心外科", "cost_category": "低值卫生材料", "current_amount": 95.0, "previous_amount": 88.0, "growth_rate": 7.95, "change_rate": 7.95, "relative_change": 7.95},
                {"department": "心外科", "cost_category": "总务消耗品", "current_amount": 55.0, "previous_amount": 52.0, "growth_rate": 5.77, "change_rate": 5.77, "relative_change": 5.77},
                {"department": "综合内科", "cost_category": "人力成本", "current_amount": 680.0, "previous_amount": 650.0, "growth_rate": 4.62, "change_rate": 4.62, "relative_change": 4.62},
                {"department": "综合内科", "cost_category": "药品成本", "current_amount": 520.0, "previous_amount": 480.0, "growth_rate": 8.33, "change_rate": 8.33, "relative_change": 8.33},
                {"department": "综合内科", "cost_category": "高值卫生材料", "current_amount": 280.0, "previous_amount": 260.0, "growth_rate": 7.69, "change_rate": 7.69, "relative_change": 7.69},
                {"department": "综合内科", "cost_category": "低值卫生材料", "current_amount": 88.0, "previous_amount": 82.0, "growth_rate": 7.32, "change_rate": 7.32, "relative_change": 7.32},
                {"department": "综合内科", "cost_category": "总务消耗品", "current_amount": 42.0, "previous_amount": 40.0, "growth_rate": 5.0, "change_rate": 5.0, "relative_change": 5.0},
            ],
        },
    },

    # Hospital Q3: 收入结构
    {
        "keywords": ["收入结构", "药占比", "耗占比", "医务性收入", "心内科收入", "心内科", "收入占比", "占比水平", "心内科收入结构", "药占比耗占比", "医务性收入占比", "心内科药占比", "收入结构如何"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "metric_name", "data_type": "string"},
                {"name": "current_period", "data_type": "float"},
                {"name": "same_period", "data_type": "float"},
                {"name": "change_value", "data_type": "float"},
            ],
            "rows": [
                {"department": "心内科", "metric_name": "药占比", "current_period": 28.5, "same_period": 30.2, "change_value": -1.7},
                {"department": "心内科", "metric_name": "耗占比", "current_period": 22.3, "same_period": 21.8, "change_value": 0.5},
                {"department": "心内科", "metric_name": "医务性收入占比", "current_period": 49.2, "same_period": 48.0, "change_value": 1.2},
            ],
        },
    },

    # Hospital Q4/Q17: 心内系统工作量与服务效率
    {
        "keywords": ["心内", "工作量", "服务效率", "门诊人次", "出院人次", "手术台次", "平均住院天数", "床位使用率", "心内系统", "心内系统工作量", "心内系统服务效率", "工作量与服务效率", "门诊次均费用", "住院次均费用", "6月", "2025年6月", "心内系统工作量与服务效率"],
        "data": {
            "columns": [
                {"name": "metric_name", "data_type": "string"},
                {"name": "value", "data_type": "float"},
                {"name": "unit", "data_type": "string"},
            ],
            "rows": [
                {"metric_name": "门诊人次", "value": 15800.0, "unit": "人次"},
                {"metric_name": "出院人次", "value": 3200.0, "unit": "人次"},
                {"metric_name": "手术台次", "value": 890.0, "unit": "台次"},
                {"metric_name": "平均住院天数", "value": 7.2, "unit": "天"},
                {"metric_name": "床位使用率", "value": 92.5, "unit": "%"},
                {"metric_name": "病床周转次数", "value": 4.2, "unit": "次"},
                {"metric_name": "门诊次均费用", "value": 385.0, "unit": "元"},
                {"metric_name": "住院次均费用", "value": 12800.0, "unit": "元"},
                {"metric_name": "药占比", "value": 28.5, "unit": "%"},
                {"metric_name": "耗占比", "value": 22.3, "unit": "%"},
                {"metric_name": "医务性收入占比", "value": 49.2, "unit": "%"},
            ],
        },
    },

    # Hospital Q4b: 心内系统工作量与服务效率 - wide format
    {
        "keywords": ["心内", "住院次均费用", "门诊次均费用", "平均住院天数", "收入", "结余"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "outpatient_visits", "data_type": "float"},
                {"name": "discharge_count", "data_type": "float"},
                {"name": "surgery_count", "data_type": "float"},
                {"name": "avg_stay_days", "data_type": "float"},
                {"name": "bed_occupancy_rate", "data_type": "float"},
                {"name": "bed_turnover", "data_type": "float"},
                {"name": "revenue", "data_type": "float"},
                {"name": "surplus", "data_type": "float"},
            ],
            "rows": [
                {"department": "心内科", "outpatient_visits": 15800.0, "discharge_count": 3200.0, "surgery_count": 890.0, "avg_stay_days": 7.2, "bed_occupancy_rate": 92.5, "bed_turnover": 4.2, "revenue": 10000.0, "surplus": 1700.0},
            ],
        },
    },

    # Hospital Q5/Q18: 心内系统收入结构
    {
        "keywords": ["心内", "收入结构"],
        "data": {
            "columns": [
                {"name": "category", "data_type": "string"},
                {"name": "amount", "data_type": "float"},
                {"name": "ratio", "data_type": "float"},
            ],
            "rows": [
                {"category": "医疗收入", "amount": 5200.0, "ratio": 52.0},
                {"category": "药品收入", "amount": 2850.0, "ratio": 28.5},
                {"category": "耗材收入", "amount": 1230.0, "ratio": 12.3},
                {"category": "其他收入", "amount": 720.0, "ratio": 7.2},
            ],
        },
    },

    # Hospital Q6a: 心外系统6月各科室直接成本结余（单月）
    {
        "keywords": ["心外", "6月", "直接成本", "结余", "心外系统", "2025年6月", "心外系统各科室"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "revenue", "data_type": "float"},
                {"name": "direct_cost", "data_type": "float"},
                {"name": "surplus", "data_type": "float"},
            ],
            "rows": [
                {"department": "心脏外科", "revenue": 492.0, "direct_cost": 420.0, "surplus": 72.0},
                {"department": "血管外科", "revenue": 293.0, "direct_cost": 255.0, "surplus": 38.0},
            ],
        },
    },

    # Hospital Q6b/Q14: 心外系统各科室直接成本（多月）
    {
        "keywords": ["心外", "科室", "直接成本", "结余", "心外系统", "心脏外科", "血管外科", "直接成本结余", "收入"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "revenue", "data_type": "float"},
                {"name": "direct_cost", "data_type": "float"},
                {"name": "surplus", "data_type": "float"},
            ],
            "rows": [
                {"department": "心脏外科", "month": "2025-01", "revenue": 465.0, "direct_cost": 380.0, "surplus": 85.0},
                {"department": "心脏外科", "month": "2025-02", "revenue": 457.0, "direct_cost": 365.0, "surplus": 92.0},
                {"department": "心脏外科", "month": "2025-03", "revenue": 473.0, "direct_cost": 395.0, "surplus": 78.0},
                {"department": "心脏外科", "month": "2025-04", "revenue": 475.0, "direct_cost": 410.0, "surplus": 65.0},
                {"department": "心脏外科", "month": "2025-05", "revenue": 473.0, "direct_cost": 385.0, "surplus": 88.0},
                {"department": "心脏外科", "month": "2025-06", "revenue": 492.0, "direct_cost": 420.0, "surplus": 72.0},
                {"department": "血管外科", "month": "2025-01", "revenue": 275.0, "direct_cost": 220.0, "surplus": 55.0},
                {"department": "血管外科", "month": "2025-02", "revenue": 273.0, "direct_cost": 215.0, "surplus": 58.0},
                {"department": "血管外科", "month": "2025-03", "revenue": 283.0, "direct_cost": 235.0, "surplus": 48.0},
                {"department": "血管外科", "month": "2025-04", "revenue": 290.0, "direct_cost": 248.0, "surplus": 42.0},
                {"department": "血管外科", "month": "2025-05", "revenue": 282.0, "direct_cost": 230.0, "surplus": 52.0},
                {"department": "血管外科", "month": "2025-06", "revenue": 293.0, "direct_cost": 255.0, "surplus": 38.0},
            ],
        },
    },

    # Hospital Q7/Q8: 综内系统运营成绩单
    {
        "keywords": ["综内", "运营成绩单"],
        "data": {
            "columns": [
                {"name": "metric_name", "data_type": "string"},
                {"name": "value", "data_type": "float"},
                {"name": "unit", "data_type": "string"},
            ],
            "rows": [
                {"metric_name": "总收入", "value": 12500.0, "unit": "万元"},
                {"metric_name": "总成本", "value": 10800.0, "unit": "万元"},
                {"metric_name": "结余", "value": 1700.0, "unit": "万元"},
                {"metric_name": "药占比", "value": 32.5, "unit": "%"},
                {"metric_name": "耗占比", "value": 18.2, "unit": "%"},
                {"metric_name": "百元医疗收入卫生材料消耗", "value": 28.5, "unit": "元"},
            ],
        },
    },

    # Hospital Q9: 综内系统住院次均费用
    {
        "keywords": ["综内", "住院次均费用", "明细"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "avg_inpatient_cost", "data_type": "float"},
                {"name": "avg_drug_fee", "data_type": "float"},
                {"name": "avg_material_fee", "data_type": "float"},
                {"name": "avg_medical_income", "data_type": "float"},
            ],
            "rows": [
                {"department": "呼吸科", "month": "2025-01", "avg_inpatient_cost": 12500.0, "avg_drug_fee": 3200.0, "avg_material_fee": 1800.0, "avg_medical_income": 4500.0},
                {"department": "呼吸科", "month": "2025-02", "avg_inpatient_cost": 12800.0, "avg_drug_fee": 3350.0, "avg_material_fee": 1850.0, "avg_medical_income": 4600.0},
                {"department": "神经内科", "month": "2025-01", "avg_inpatient_cost": 15200.0, "avg_drug_fee": 4100.0, "avg_material_fee": 2200.0, "avg_medical_income": 5800.0},
                {"department": "神经内科", "month": "2025-02", "avg_inpatient_cost": 15500.0, "avg_drug_fee": 4250.0, "avg_material_fee": 2150.0, "avg_medical_income": 5950.0},
                {"department": "消化内科", "month": "2025-01", "avg_inpatient_cost": 11800.0, "avg_drug_fee": 2900.0, "avg_material_fee": 1500.0, "avg_medical_income": 4200.0},
                {"department": "消化内科", "month": "2025-02", "avg_inpatient_cost": 12100.0, "avg_drug_fee": 3050.0, "avg_material_fee": 1550.0, "avg_medical_income": 4350.0},
            ],
        },
    },

    # Hospital Q10: 心外系统次均耗材费用
    {
        "keywords": ["心外", "次均", "耗材", "费用"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "avg_material_fee", "data_type": "float"},
            ],
            "rows": [
                {"department": "心脏外科", "avg_material_fee": 8500.0},
                {"department": "血管外科", "avg_material_fee": 6200.0},
            ],
        },
    },

    # Hospital Q11: 综内各科室住院次均医务性收入变动
    {
        "keywords": ["综内", "医务性收入", "变动"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "avg_medical_income", "data_type": "float"},
            ],
            "rows": [
                {"department": "呼吸科", "month": "2025-01", "avg_medical_income": 4500.0},
                {"department": "呼吸科", "month": "2025-02", "avg_medical_income": 4600.0},
                {"department": "神经内科", "month": "2025-01", "avg_medical_income": 5800.0},
                {"department": "神经内科", "month": "2025-02", "avg_medical_income": 5950.0},
            ],
        },
    },

    # Hospital Q12: 心内DRG次均医务性收入
    {
        "keywords": ["心内", "DRG", "医务性收入"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "drg_avg_medical_income", "data_type": "float"},
            ],
            "rows": [
                {"month": "2025-01", "drg_avg_medical_income": 6800.0},
                {"month": "2025-02", "drg_avg_medical_income": 7050.0},
            ],
        },
    },

    # Hospital Q13: 心内异地医保次均药品费用
    {
        "keywords": ["心内", "异地医保", "药品费用"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "avg_drug_fee", "data_type": "float"},
            ],
            "rows": [
                {"department": "心内科一", "month": "2025-01", "avg_drug_fee": 3800.0},
                {"department": "心内科一", "month": "2025-02", "avg_drug_fee": 3950.0},
                {"department": "心内科二", "month": "2025-01", "avg_drug_fee": 4200.0},
                {"department": "心内科二", "month": "2025-02", "avg_drug_fee": 4100.0},
            ],
        },
    },

    # Hospital Q15: 综内2月收入结构总体
    {
        "keywords": ["综内", "收入结构", "总体"],
        "data": {
            "columns": [
                {"name": "category", "data_type": "string"},
                {"name": "amount", "data_type": "float"},
                {"name": "ratio", "data_type": "float"},
            ],
            "rows": [
                {"category": "医疗收入", "amount": 4800.0, "ratio": 48.0},
                {"category": "药品收入", "amount": 3200.0, "ratio": 32.0},
                {"category": "耗材收入", "amount": 1500.0, "ratio": 15.0},
                {"category": "其他收入", "amount": 500.0, "ratio": 5.0},
            ],
        },
    },

    # Hospital Q16: 心内医疗服务效率平均住院天数
    {
        "keywords": ["心内", "平均住院天数", "医疗服务效率"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "month", "data_type": "string"},
                {"name": "avg_stay_days", "data_type": "float"},
            ],
            "rows": [
                {"department": "心内科一", "month": "2024-01", "avg_stay_days": 7.5},
                {"department": "心内科一", "month": "2024-02", "avg_stay_days": 7.2},
                {"department": "心内科二", "month": "2024-01", "avg_stay_days": 8.1},
                {"department": "心内科二", "month": "2024-02", "avg_stay_days": 7.8},
            ],
        },
    },

    # Hospital Q19: 综内门诊次均费用
    {
        "keywords": ["综内", "门诊", "次均费用"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "avg_outpatient_cost", "data_type": "float"},
            ],
            "rows": [
                {"department": "呼吸科", "avg_outpatient_cost": 380.0},
                {"department": "神经内科", "avg_outpatient_cost": 420.0},
                {"department": "消化内科", "avg_outpatient_cost": 350.0},
            ],
        },
    },

    # Hospital Q20: 综内异地医保
    {
        "keywords": ["综内", "异地医保"],
        "data": {
            "columns": [
                {"name": "metric_name", "data_type": "string"},
                {"name": "value", "data_type": "float"},
            ],
            "rows": [
                {"metric_name": "异地医保人次", "value": 1250.0},
                {"metric_name": "异地医保费用", "value": 2800.0},
                {"metric_name": "异地医保占比", "value": 18.5},
            ],
        },
    },

    # Hospital Q21: 急诊科工作量
    {
        "keywords": ["急诊", "工作量", "服务效率"],
        "data": {
            "columns": [
                {"name": "metric_name", "data_type": "string"},
                {"name": "value", "data_type": "float"},
            ],
            "rows": [
                {"metric_name": "急诊人次", "value": 28500.0},
                {"metric_name": "抢救成功率", "value": 96.8},
                {"metric_name": "平均留观时间", "value": 6.5},
                {"metric_name": "收住院率", "value": 35.2},
            ],
        },
    },

    # Hospital Q22: 综外耗占比
    {
        "keywords": ["综外", "耗占比", "收入结构"],
        "data": {
            "columns": [
                {"name": "metric_name", "data_type": "string"},
                {"name": "value", "data_type": "float"},
            ],
            "rows": [
                {"metric_name": "耗占比", "value": 24.5},
                {"metric_name": "药占比", "value": 26.8},
                {"metric_name": "医务性收入占比", "value": 48.7},
            ],
        },
    },

    # Hospital Q23: 综内运营成绩单汇总
    {
        "keywords": ["综内", "运营成绩单", "汇总"],
        "data": {
            "columns": [
                {"name": "month", "data_type": "string"},
                {"name": "revenue", "data_type": "float"},
                {"name": "direct_cost", "data_type": "float"},
                {"name": "surplus", "data_type": "float"},
            ],
            "rows": [
                {"month": "2025-01", "revenue": 3200.0, "direct_cost": 2800.0, "surplus": 400.0},
                {"month": "2025-02", "revenue": 3050.0, "direct_cost": 2680.0, "surplus": 370.0},
                {"month": "2025-03", "revenue": 3400.0, "direct_cost": 2950.0, "surplus": 450.0},
                {"month": "2025-04", "revenue": 3350.0, "direct_cost": 2900.0, "surplus": 450.0},
            ],
        },
    },

    # Hospital Q24: 门诊次均费用与住院次均费用
    {
        "keywords": ["门诊次均费用", "住院次均费用", "次均费用", "门诊", "住院"],
        "data": {
            "columns": [
                {"name": "metric_name", "data_type": "string"},
                {"name": "avg_outpatient_cost", "data_type": "float"},
                {"name": "avg_inpatient_cost", "data_type": "float"},
            ],
            "rows": [
                {"metric_name": "全院", "avg_outpatient_cost": 385.0, "avg_inpatient_cost": 12800.0},
            ],
        },
    },

    # Hospital Q25: 各科室药占比数据（用于排名）
    {
        "keywords": ["药占比", "科室药占比", "药品成本占比", "药占比排名", "科室药占比排名", "drug_ratio", "各科室药占比", "医院药占比"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "drug_ratio", "data_type": "float"},
                {"name": "material_ratio", "data_type": "float"},
                {"name": "medical_income_ratio", "data_type": "float"},
            ],
            "rows": [
                {"department": "综合内科", "drug_ratio": 32.5, "material_ratio": 18.2, "medical_income_ratio": 49.3},
                {"department": "心内科", "drug_ratio": 28.5, "material_ratio": 22.3, "medical_income_ratio": 49.2},
                {"department": "综合外科", "drug_ratio": 26.8, "material_ratio": 24.5, "medical_income_ratio": 48.7},
                {"department": "心外科", "drug_ratio": 22.1, "material_ratio": 28.8, "medical_income_ratio": 49.1},
                {"department": "急诊科", "drug_ratio": 35.2, "material_ratio": 15.6, "medical_income_ratio": 49.2},
                {"department": "神经内科", "drug_ratio": 30.8, "material_ratio": 19.5, "medical_income_ratio": 49.7},
                {"department": "呼吸科", "drug_ratio": 29.5, "material_ratio": 20.1, "medical_income_ratio": 50.4},
            ],
        },
    },

    # Hospital Q26: 人力成本同比（当月 vs 去年同月）- 包含全院汇总
    {
        "keywords": ["人力成本", "同比", "去年", "增加", "增长", "本月", "全院", "人力成本同比", "人力成本增加", "人力成本增长", "劳动力成本", "labor_cost", "同比去年", "本月人力成本", "人力成本同比增加"],
        "data": {
            "columns": [
                {"name": "department", "data_type": "string"},
                {"name": "current_labor_cost", "data_type": "float"},
                {"name": "previous_labor_cost", "data_type": "float"},
                {"name": "change_amount", "data_type": "float"},
                {"name": "change_rate", "data_type": "float"},
            ],
            "rows": [
                {"department": "全院合计", "current_labor_cost": 2450.0, "previous_labor_cost": 2330.0, "change_amount": 120.0, "change_rate": 5.15},
                {"department": "心内科", "current_labor_cost": 850.0, "previous_labor_cost": 800.0, "change_amount": 50.0, "change_rate": 6.25},
                {"department": "心外科", "current_labor_cost": 920.0, "previous_labor_cost": 880.0, "change_amount": 40.0, "change_rate": 4.55},
                {"department": "综合内科", "current_labor_cost": 680.0, "previous_labor_cost": 650.0, "change_amount": 30.0, "change_rate": 4.62},
            ],
        },
    },

    # Hospital Q26b: 人力成本同比（时间序列格式，供同比函数使用）
    {
        "keywords": ["人力成本", "同比", "去年", "增加", "增长", "本月", "labor_cost", "年", "人力"],
        "data": {
            "columns": [
                {"name": "year", "data_type": "string"},
                {"name": "labor_cost", "data_type": "float"},
            ],
            "rows": [
                {"year": "2025", "labor_cost": 2330.0},
                {"year": "2026", "labor_cost": 2450.0},
            ],
        },
    },

    # Hospital Q28: 各科成本增幅汇总（按成本类别聚合增幅）
    {
        "keywords": ["成本增幅", "哪部分", "增幅最大", "各科", "成本类别", "成本增幅最大", "成本增长最大", "成本", "增幅", "最大", "1-4月", "成本构成", "各科成本", "成本增长", "哪部分成本", "各科成本增幅"],
        "data": {
            "columns": [
                {"name": "cost_category", "data_type": "string"},
                {"name": "avg_growth_rate", "data_type": "float"},
                {"name": "max_growth_rate", "data_type": "float"},
                {"name": "total_current", "data_type": "float"},
                {"name": "total_previous", "data_type": "float"},
            ],
            "rows": [
                {"cost_category": "高值卫生材料", "avg_growth_rate": 15.66, "max_growth_rate": 25.0, "total_current": 1510.0, "total_previous": 1280.0},
                {"cost_category": "药品成本", "avg_growth_rate": 10.87, "max_growth_rate": 12.73, "total_current": 1720.0, "total_previous": 1550.0},
                {"cost_category": "低值卫生材料", "avg_growth_rate": 8.12, "max_growth_rate": 9.09, "total_current": 303.0, "total_previous": 280.0},
                {"cost_category": "总务消耗品", "avg_growth_rate": 6.37, "max_growth_rate": 8.33, "total_current": 162.0, "total_previous": 152.0},
                {"cost_category": "人力成本", "avg_growth_rate": 5.14, "max_growth_rate": 6.25, "total_current": 2450.0, "total_previous": 2330.0},
            ],
        },
    },
]
