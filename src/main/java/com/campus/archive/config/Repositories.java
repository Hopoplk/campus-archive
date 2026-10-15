package com.campus.archive.config;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import java.util.function.Function;

/**
 * 会话边界封装。
 *
 * <p>MyBatis 的 SqlSession 不是线程安全的，而本系统存在 UI 线程与后台 I/O 线程、
 * 调度线程并发访问数据库的情况（详细设计 5.6 节）。因此统一采用"一次操作一个会话"：
 * 单步操作走 {@link #query}（自动提交），跨多表操作走 {@link #tx}（显式事务 + 失败回滚）。</p>
 */
public class Repositories {

    private final SqlSessionFactory factory;

    public Repositories(SqlSessionFactory factory) {
        this.factory = factory;
    }

    /** 只读或单步写操作。 */
    public <T> T query(Function<DaoBundle, T> work) {
        try (SqlSession session = factory.openSession(true)) {
            return work.apply(DaoBundle.of(session));
        }
    }

    /** 跨多表的事务操作：任一步抛异常则整体回滚（详细设计 9.4 节事务边界）。 */
    public <T> T tx(Function<DaoBundle, T> work) {
        try (SqlSession session = factory.openSession(false)) {
            try {
                T result = work.apply(DaoBundle.of(session));
                session.commit();
                return result;
            } catch (RuntimeException e) {
                session.rollback();
                throw e;
            }
        }
    }
}
