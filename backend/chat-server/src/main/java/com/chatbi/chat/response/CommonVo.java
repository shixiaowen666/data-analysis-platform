package com.chatbi.chat.response;

import com.alibaba.fastjson.JSONObject;
import com.chatbi.chat.enums.ResultCode;

import java.io.Serializable;

public class CommonVo<T> implements Serializable {

    private static final long serialVersionUID = 7917345507074842804L;

    private String ret;
    private Integer code;
    private String message;
    private T data;

    public static class Builder {
        public static CommonVo SUCC() {
            CommonVo vo = new CommonVo();
            vo.setRet("success");
            vo.setCode(ResultCode.SUCCESS.getCode());
            return vo;
        }

        public static CommonVo fail(ResultCode resultCode) {
            CommonVo vo = new CommonVo();
            vo.setRet("fail");
            vo.setCode(resultCode.getCode());
            vo.setMessage(resultCode.getMessage());
            return vo;
        }

        public static CommonVo fail(ResultCode resultCode, String message) {
            CommonVo vo = new CommonVo();
            vo.setRet("fail");
            vo.setCode(resultCode.getCode());
            vo.setMessage(message);
            return vo;
        }
    }

    public CommonVo<T> initErrCodeAndMsg(Integer code, String message) {
        this.code = code;
        this.message = message;
        return this;
    }

    public CommonVo initSuccDataAndMsg(Integer code, String message) {
        this.code = code;
        this.message = message;
        return this;
    }

    public CommonVo initSuccDataAndMsg(String message, T data) {
        this.data = data;
        this.message = message;
        return this;
    }

    public CommonVo initSuccData(T data) {
        this.data = data;
        return this;
    }

    public CommonVo initSuccMsg(String message) {
        this.message = message;
        return this;
    }

    public CommonVo<T> initErrCodeAndData(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        return this;
    }

    public boolean isSuccess() {
        return ResultCode.SUCCESS.getCode().equals(code);
    }

    public String getRet() {
        return ret;
    }

    public void setRet(String ret) {
        this.ret = ret;
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("isSuccess", isSuccess());
        jsonObject.put("message", getMessage());
        jsonObject.put("ret", this.ret);
        jsonObject.put("code", this.code);
        return jsonObject.toString();
    }
}
