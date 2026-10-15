package com.campus.archive.pattern.export;

/**
 * 导出失败。携带与《详细设计》9.5 节一致的三位错误码，由服务层转换为 Result。
 */
public class ExportException extends RuntimeException {

    private final String code;

    public ExportException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ExportException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
