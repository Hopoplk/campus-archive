package com.campus.archive.dto;

/**
 * 层间统一返回结构（详细设计 1.4 / 9.5 节）。
 * service 层不外抛技术异常，一律转换成 Result 携带错误码与中文文案。
 */
public class Result<T> {

    private final boolean success;
    private final String code;
    private final String message;
    private final T data;

    private Result(boolean success, String code, String message, T data) {
        this.success = success;
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(true, "0", "操作成功", data);
    }

    public static <T> Result<T> ok() {
        return new Result<>(true, "0", "操作成功", null);
    }

    public static <T> Result<T> fail(String code, String message) {
        return new Result<>(false, code, message, null);
    }

    public boolean isSuccess() { return success; }
    public String getCode() { return code; }
    public String getMessage() { return message; }
    public T getData() { return data; }

    @Override
    public String toString() {
        return success ? message : "[" + code + "] " + message;
    }
}
