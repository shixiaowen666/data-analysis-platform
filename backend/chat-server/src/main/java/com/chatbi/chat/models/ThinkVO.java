package com.chatbi.chat.models;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;


/***
 * @ClassName ThinkVO
 * @Version 1.0
 */
@Data
public class ThinkVO {
    @Schema(description ="状态 0-异常中断 1-结束 2-输出中")
    private Integer status;
    @Schema(description ="输出内容")
    private String message;
    @Schema(description ="耗时")
    private Long speed;

    public ThinkVO() {
    }

    public ThinkVO(Integer status, String message, Long speed) {
        this.status = status;
        this.message = message;
        this.speed = speed;
    }
}
