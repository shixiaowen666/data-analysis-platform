package com.chatbi.chat.constant;

import java.util.HashMap;
import java.util.Map;

/**
 * @program: metrics-olap-wm
 * @description: op转换类
 * @author: zjd
 * @create: 2026-03-26 15:57
 **/

public class OperatorConverter {
    private static final Map<String, Integer> OPERATOR_MAP = new HashMap<>();

    static {
        OPERATOR_MAP.put(">", 0);
        OPERATOR_MAP.put("<", 1);
        OPERATOR_MAP.put("==", 2);
        OPERATOR_MAP.put(">=", 3);
        OPERATOR_MAP.put("<=", 4);
        OPERATOR_MAP.put("in", 5);
        OPERATOR_MAP.put("not in", 6);
        OPERATOR_MAP.put("!=", 7);
        OPERATOR_MAP.put("like", 8);
        OPERATOR_MAP.put("not like", 9);
    }

    public static Integer convertOperator(String operator) {
        if (operator == null) {
            return null;
        }
        return OPERATOR_MAP.get(operator.toLowerCase());
    }
}
