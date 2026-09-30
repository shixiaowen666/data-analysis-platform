package com.metadata.engine.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 采集状态
 */
@Getter
@AllArgsConstructor
public enum CollectStatusEnum {

    RUNNING("RUNNING"),
    SUCCESS("SUCCESS"),
    FAIL("FAIL");

    private final String code;
}
