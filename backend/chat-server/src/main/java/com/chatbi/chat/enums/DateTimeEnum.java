package com.chatbi.chat.enums;

public enum DateTimeEnum {
    //ptdate,weekofyear,month,quarter,year
    DAY("ptdate","日期"),
    WEEKOFYEAR("weekofyear","自然周"),
    MONTH("month","月份"),
    QUARTER("quarter","季度"),
    YEAR("year","年份")
    ;

    private final String dataType;

    private final String name;

    public String getDataType() {
        return dataType;
    }

    DateTimeEnum(String dataType, String name) {
        this.dataType = dataType;
        this.name = name;
    }

    public static Boolean ifDateDim(String dimKey){
        for (DateTimeEnum value : DateTimeEnum.values()) {
            if(value.getDataType().equals(dimKey)){
                return true;
            }
        }
        return false;
    }

    public static String getName(String dimKey){
        for (DateTimeEnum value : DateTimeEnum.values()) {
            if(value.getDataType().equals(dimKey)){
                return value.name;
            }
        }
        return DAY.name;
    }
}
