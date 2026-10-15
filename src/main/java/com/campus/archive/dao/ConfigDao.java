package com.campus.archive.dao;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 应用配置键值表。 */
public interface ConfigDao {

    @Select("SELECT value FROM app_config WHERE key = #{key}")
    String get(@Param("key") String key);

    @Insert("""
            INSERT INTO app_config(key, value) VALUES(#{key}, #{value})
            ON CONFLICT(key) DO UPDATE SET value = excluded.value
            """)
    int put(@Param("key") String key, @Param("value") String value);
}
