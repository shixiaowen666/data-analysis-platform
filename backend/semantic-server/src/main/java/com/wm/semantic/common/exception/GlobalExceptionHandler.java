package com.wm.semantic.common.exception;

import com.wm.semantic.common.response.QueryDataResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BizException.class)
    public QueryDataResponse<Void> handleBizException(BizException e) {
        log.warn("业务异常: {}", e.getMessage(), e);
        return QueryDataResponse.error(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public QueryDataResponse<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return QueryDataResponse.error(500, "系统错误");
    }
}