package com.chatbi.chat.enums;

import lombok.Getter;

/***
 * @ClassName QueryStatusEnum
 * @Description //状态 0-执行中 1-已完成 2-正在思考 3-正在查数 4-已终止
 * @Author chenxiwen
 * @Date 4/14/25 11:51 PM
 * @Version 1.0
 */
public enum QueryStatusEnum {
    RUNNING(0,"running","执行中"),
    FINISHED(1,"finished","已完成"),
    THINKING(2,"thinking","正在思考"),
    QUERY_DATA(3,"queryData","正在查数"),
    STOP(4,"stop","已终止"),
    ERROR(5, "error","结果报错");;

    @Getter
    private Integer key;
    private String name;
    private String desc;

    QueryStatusEnum(Integer key,String name,String desc){
        this.key = key;
        this.name = name;
        this.desc = desc;
    }

    public void setKey(Integer key) {
        this.key = key;
    }
}
