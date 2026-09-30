package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;

/**
 * 开放平台查询变量
 */
@Data
public class OpendataVariable implements Serializable {
    /**
     * 指标或维度id
     */
    private Long dimIndId;
    /**
     * 类型 1维度，2指标
     */
    private Integer dimIndType;
    /**
     * 变量key
     */
    private String varKey;
    /**
     * 运算符
     */
    private String symbol;

}
