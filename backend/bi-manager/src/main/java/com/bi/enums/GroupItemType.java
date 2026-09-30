package com.bi.enums;

import com.common.exception.BizException;
import lombok.Getter;

/**
 * 组合项类型
 */
@Getter
public enum GroupItemType {

    DIMENSION("dimension", "维度"),
    ATOMIC_METRIC("atomic_metric", "原子指标"),
    CALCULATED_METRIC("calculated_metric", "计算指标"),
    DERIVED_METRIC("derived_metric", "派生指标"),
    INDICATOR_GROUP("indicator_group", "指标组合");

    private final String code;
    private final String desc;

    GroupItemType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static GroupItemType fromCode(String code) {
        for (GroupItemType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BizException(400, "不支持的组合项类型: " + code);
    }
}
