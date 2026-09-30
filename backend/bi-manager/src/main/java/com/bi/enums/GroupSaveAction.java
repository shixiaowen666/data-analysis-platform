package com.bi.enums;

import com.common.exception.BizException;
import lombok.Getter;

/**
 * 组合保存动作
 */
@Getter
public enum GroupSaveAction {

    DRAFT("draft", 0),
    PUBLISH("publish", 1);

    private final String code;
    private final int status;

    GroupSaveAction(String code, int status) {
        this.code = code;
        this.status = status;
    }

    public static GroupSaveAction fromCode(String code) {
        if (code == null) {
            throw new BizException(400, "保存动作不能为空");
        }
        String normalized = code.trim();
        if ("0".equals(normalized)) {
            return DRAFT;
        }
        if ("1".equals(normalized)) {
            return PUBLISH;
        }
        for (GroupSaveAction action : values()) {
            if (action.code.equals(normalized)) {
                return action;
            }
        }
        throw new BizException(400, "不支持的保存动作: " + code + "，请使用 draft 或 publish");
    }
}
