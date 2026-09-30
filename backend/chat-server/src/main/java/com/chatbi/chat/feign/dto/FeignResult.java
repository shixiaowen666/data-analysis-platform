package com.chatbi.chat.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * Feign 调用通用返回结果
 */
@Data
public class FeignResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer code;
    private String message;
    private T data;

    public boolean isSuccess() {
        return this.code != null && this.code == 200;
    }
}
