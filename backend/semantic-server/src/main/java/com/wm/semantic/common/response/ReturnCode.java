package com.wm.semantic.common.response;

/**
 * 统一返回码枚举。
 */
public enum ReturnCode {

    // ---- 通用 ----
    SUCCESS(200, "success"),
    SYSTEM_ERROR(500, "系统内部错误"),
    BIZ_ERROR(501, "业务处理失败"),

    // ---- 请求校验 ----
    PARAM_MISSING(510, "必填参数缺失"),
    PARAM_INVALID(511, "参数格式非法"),

    // ---- 数据源 ----
    DATA_SOURCE_NOT_FOUND(520, "数据源未找到"),
    DATA_SOURCE_MISSING(521, "数据源信息缺失"),
    TABLE_NOT_FOUND(522, "物理表未找到"),
    MODEL_NOT_FOUND(523, "模型未找到"),

    // ---- 元数据 ----
    METADATA_MISSING(530, "字段映射缺失"),
    INDICATOR_MISSING(531, "指标元数据缺失"),
    MODEL_JOIN_MISSING(532, "模型 JOIN 关系缺失"),

    // ---- 维度/指标 ----
    DIM_IND_BOTH_EMPTY(540, "dimensionIds 和 indicatorIds 不能同时为空"),
    NO_AVAILABLE_DATA_SOURCE(541, "无可用数据源"),

    // ---- SQL 构建 ----
    SQL_BUILD_ERROR(550, "SQL 构建失败"),
    FIELD_KEY_DUPLICATE(551, "列别名重复"),
    FIELD_KEY_MISSING(552, "列别名缺失"),

    ;

    private final int code;
    private final String message;

    ReturnCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() { return code; }
    public String getMessage() { return message; }
}
