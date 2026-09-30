package com.chatbi.chat.enums;

public enum TableTypeEnum {
	/**
     * 表
     */
    TABLE("0", "物理表"),

    /**
     * 视图
     * */
    VIEW("1", "视图")
    ;

    private String id;

    private String name;

    TableTypeEnum(String id, String name){
        this.id = id;
        this.name = name;
    }
    public static String getName(Integer id){
        for(TableTypeEnum tableTypeEnum : TableTypeEnum.values()){
            if(id.equals(tableTypeEnum.getId())){
                return tableTypeEnum.getName();
            }
        }
        return null;
    }
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
