package com.chatbi.chat.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ChartType {

    TABLE(1, "表格"),
    BAR(2, "柱状图"),
    LINE(3, "折线图"),
    PIE(4, "饼图"),
    CARD(5, "指标卡"),
    CROSS_TABLE(6, "交叉表");

    private final Integer code;
    private final String name;
}
