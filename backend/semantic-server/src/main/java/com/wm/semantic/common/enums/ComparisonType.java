package com.wm.semantic.common.enums;

import lombok.Getter;

/**
 * 指标对比类型。
 *
 * <ul>
 *   <li>NONE: 无对比</li>
 *   <li>POP: 环比（Period-Over-Period，上一相邻周期，跟随时间粒度）</li>
 *   <li>YOY: 同比（Year-On-Year，去年同期）</li>
 *   <li>BOTH: 同比+环比都输出</li>
 * </ul>
 */
@Getter
public enum ComparisonType {

    NONE("none"),
    POP("pop"),
    YOY("yoy"),
    BOTH("both");

    private final String value;

    ComparisonType(String value) {
        this.value = value;
    }

    public static ComparisonType from(String v) {
        if (v == null || v.trim().isEmpty()) {
            return NONE;
        }
        for (ComparisonType t : values()) {
            if (t.value.equalsIgnoreCase(v.trim())) {
                return t;
            }
        }
        return NONE;
    }

    /** 是否包含环比 */
    public boolean includesPop() {
        return this == POP || this == BOTH;
    }

    /** 是否包含同比 */
    public boolean includesYoy() {
        return this == YOY || this == BOTH;
    }
}
