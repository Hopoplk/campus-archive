package com.campus.archive.infra;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalDateTime 与 SQLite TEXT 的映射。
 * 不依赖 JDBC 驱动对 java.time 的支持，统一按 ISO-8601 存取，读取时兼容空格分隔与纳秒后缀。
 */
public class LocalDateTimeTypeHandler extends BaseTypeHandler<LocalDateTime> {

    private static final DateTimeFormatter OUT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, LocalDateTime parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, parameter.format(OUT));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return parse(rs.getString(columnName));
    }

    @Override
    public LocalDateTime getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return parse(rs.getString(columnIndex));
    }

    @Override
    public LocalDateTime getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return parse(cs.getString(columnIndex));
    }

    /** 兼容 "2026-10-12 13:00:00"、"2026-10-12T13:00:00" 与带小数秒的写法。 */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String s = raw.trim().replace('T', ' ');
        int dot = s.indexOf('.');
        if (dot > 0) s = s.substring(0, dot);
        if (s.length() == 10) s = s + " 00:00:00";
        if (s.length() == 16) s = s + ":00";
        return LocalDateTime.parse(s, OUT);
    }
}
