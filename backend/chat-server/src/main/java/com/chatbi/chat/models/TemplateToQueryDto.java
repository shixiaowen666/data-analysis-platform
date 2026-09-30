package com.chatbi.chat.models;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 开放平台模版参数
 */
@Data
public class TemplateToQueryDto implements Serializable {
    /**
     * 更新类型:0 离线，1 实时累计，2 实时时段
     */
    private Integer updateType;
    /**
     * 主查询参数
     */
    private DataRequestDTO olapDataRequest;
    /**
     * 辅助列
     */
//    private List<HelpColInfo> helpColInfoList;
    /**
     * 计算列
     */
//    private List<CalColInfo> calColInfoList;
    /**
     * 查询变量
     */
    private List<OpendataVariable> queryParams;

}
