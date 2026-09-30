package com.chatbi.chat.enums;

public enum UpdateTypeEnum {
	
    OFF_LINE(0, "离线", "2"), // 2:选择  null:不选
    REAL_TIME_TOTAL(1, "实时累计", "3"),
    REAL_TIME_INTERVAL(2, "实时时段", "1,4");// 1:选择  4:不选

    private Integer id;
    private String name;
    private String timeTypes;
   
    UpdateTypeEnum(int id, String name, String timeTypes) {
        this.id = id;
        this.name = name;
        this.timeTypes = timeTypes;
    }
    
    public static Integer getIdByTimeType(Integer timeType) {
    	if (timeType == null) {
    		return OFF_LINE.getId();
    	}
        for (UpdateTypeEnum e : UpdateTypeEnum.values()) {
            if(e.getTimeTypes().contains(String.valueOf(timeType))) {
                return e.getId();
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

	public String getTimeTypes() {
		return timeTypes;
	}
}
