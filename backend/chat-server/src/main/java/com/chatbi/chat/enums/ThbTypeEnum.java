package com.chatbi.chat.enums;

public enum ThbTypeEnum {
	// 时粒度
	HOUR_SHB(61, "时环比", "hour", "h_shb"),
	HOUR_RTB(62, "日同比", "hour", "h_rtb"),
	HOUR_ZTB(63, "周同比", "hour", "h_ztb"),
	// 日粒度
    DAY_RHB(11, "日环比", "day", "d_rhb"),
    DAY_ZTB(12, "周同比", "day", "d_ztb"),
    DAY_YTB(13, "月同比", "day", "d_ytb"),
    DAY_JTB(14, "季同比", "day", "d_jtb"),
    DAY_NTB(15, "年同比", "day", "d_ntb"),
    DAY_BYC(16, "比上月末增长率", "day", "d_byc"),
    DAY_BNC(17, "比上年末增长率", "day", "d_bnc"),
    DAY_BJC(18, "比上季末增长率", "day", "d_bjc"),
    DAY_BMT(19, "比某天增长率", "day", "d_bmt"),
    // 周粒度
    WEEK_ZHB(21, "周环比", "week", "w_zhb"),
    WEEK_YTB(22, "月同比", "week", "w_ytb"),
    WEEK_JTB(23, "季同比", "week", "w_jtb"),
    WEEK_NTB(24, "年同比", "week", "w_ntb"),
    // 月粒度
    MONTH_YHB(31, "月环比", "month", "m_yhb"),
    MONTH_JTB(32, "季同比", "month", "m_jtb"),
    MONTH_NTB(33, "年同比", "month", "m_ntb"),
    // 季粒度
    QUARTER_JHB(41, "季环比", "quarter", "q_jhb"),
    QUARTER_NTB(42, "年同比", "quarter", "q_ntb"),
    // 年粒度
    YEAR_NHB(51, "年环比", "year", "y_nhb"),
	// 合计环比
	TOTAL(99, "环比", "total", "hb");

    private Integer id;
    private String name;
    private String function;
    private String key;
    ThbTypeEnum(int id, String name, String function, String key) {
        this.id = id;
        this.name = name;
        this.function = function;
        this.key = key;
    }
    
    public static String getNameById(Integer id) {
        for (ThbTypeEnum e : ThbTypeEnum.values()) {
            if(id.equals(e.getId())){
                return e.getName();
            }
        }
        return null;
    }
    
    public static String getKeyById(Integer id) {
        for (ThbTypeEnum e : ThbTypeEnum.values()) {
            if(id.equals(e.getId())){
                return e.getKey();
            }
        }
        return null;
    }
    
    public static String getNameByIdAndCalType (Integer id, Integer calType) {
        for (ThbTypeEnum e : ThbTypeEnum.values()) {
            if (id.equals(e.getId())) {
            	if (calType != null && calType.intValue() == ThbCalTypeEnum.THB_SUB.getId().intValue()) {
            		return e.getName().replace("比", "差");
            	} else {
            		return e.getName();
            	}
            }
        }
        return null;
    }
    
    public static String getKeyByIdAndCalType (Integer id, Integer calType) {
        for (ThbTypeEnum e : ThbTypeEnum.values()) {
            if (id.equals(e.getId())) {
                return e.getKey() + calType;
            }
        }
        return null;
    }
    
    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
	public String getFunction() {
		return function;
	}
	public String getKey() {
		return key;
	}
}
