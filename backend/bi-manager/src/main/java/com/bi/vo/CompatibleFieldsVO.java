package com.bi.vo;

import lombok.Data;

import java.util.List;

@Data
public class CompatibleFieldsVO {

    private List<CompatibleFieldVO> dimensions;

    private List<CompatibleFieldVO> metrics;

    /**
     * 当前解析出的事实表上下文，便于前端调试
     */
    private List<Long> factTableIds;
}
