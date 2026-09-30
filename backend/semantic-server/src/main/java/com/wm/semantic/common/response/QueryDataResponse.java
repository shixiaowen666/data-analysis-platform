package com.wm.semantic.common.response;

public class QueryDataResponse<T> {
    private int code;
    private String message;
    private T data;

    public int getCode() { return code; }
    public void setCode(int code) { this.code = code; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public T getData() { return data; }
    public void setData(T data) { this.data = data; }

    public static <T> QueryDataResponse<T> success(T data) {
        QueryDataResponse<T> response = new QueryDataResponse<>();
        response.setCode(200);
        response.setMessage("success");
        response.setData(data);
        return response;
    }

    public static <T> QueryDataResponse<T> error(int code, String message) {
        QueryDataResponse<T> response = new QueryDataResponse<>();
        response.setCode(code);
        response.setMessage(message);
        return response;
    }
}