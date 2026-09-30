package com.bi.enums;

import cn.hutool.core.util.StrUtil;
import com.common.exception.BizException;
import lombok.Getter;

/**
 * 模型 JOIN 类型
 */
@Getter
public enum JoinType {

    LEFT_JOIN("LEFT JOIN"),
    INNER_JOIN("INNER JOIN"),
    RIGHT_JOIN("RIGHT JOIN");

    private final String value;

    JoinType(String value) {
        this.value = value;
    }

    public static String normalize(String joinType) {
        if (StrUtil.isBlank(joinType)) {
            throw new BizException(400, "关联关系不能为空");
        }
        String key = joinType.trim().toUpperCase().replace('_', ' ');
        switch (key) {
            case "LEFT JOIN":
            case "LEFT":
                return LEFT_JOIN.value;
            case "INNER JOIN":
            case "INNER":
                return INNER_JOIN.value;
            case "RIGHT JOIN":
            case "RIGHT":
                return RIGHT_JOIN.value;
            default:
                throw new BizException(400, "不支持的关联关系: " + joinType + "，请使用 LEFT JOIN / INNER JOIN / RIGHT JOIN");
        }
    }

    public static void validate(String joinType) {
        normalize(joinType);
    }
}
