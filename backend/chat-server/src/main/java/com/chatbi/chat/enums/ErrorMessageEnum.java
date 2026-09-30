package com.chatbi.chat.enums;

/***
 * @ClassName ErrorMessageEnum
 * @Description
 * @Author chenxiwen
 * @Date 7/24/25 2:51 PM
 * @Version 1.0
 */
public enum ErrorMessageEnum {
    QUERY_ERROR("0", "数据查询执行失败"),
    QUERY_SUCCESS("1", "数据查询执行成功"),
    ANSWER_SUCCESS("2", "问题答案已生成"),
    ANSWER_ERROR("3", "生成答案失败");

    private String code;

    private String message;

    ErrorMessageEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static String getMessageByCode(String code) {
        for (ErrorMessageEnum value : ErrorMessageEnum.values()) {
            if (value.getCode().equals(code)) {
                return value.getMessage();
            }
        }
        return null;
    }
}
