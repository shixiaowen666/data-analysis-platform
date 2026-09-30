package com.chatbi.chat.enums;

public enum ThbCalTypeEnum {
	
	THB_DIV(0, "同环比"), 
    THB_SUB(1, "同环比差");

    private Integer id;
    private String name;
    
    ThbCalTypeEnum(int id, String name) {
        this.id = id;
        this.name = name;
    }
    
	public Integer getId() {
		return id;
	}

	public String getName() {
		return name;
	}
}
