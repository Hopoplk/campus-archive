package com.campus.archive.dao;

import com.campus.archive.entity.Schedule;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

/** 日程数据访问。 */
public interface ScheduleDao {

    @Insert("""
            INSERT INTO schedule(id, title, due_time, remind_at, status, reminded, remark)
            VALUES(#{id}, #{title}, #{dueTime}, #{remindAt}, #{status}, #{reminded}, #{remark})
            """)
    int insert(Schedule s);

    @Update("UPDATE schedule SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") String id, @Param("status") String status);

    @Update("UPDATE schedule SET reminded = 1 WHERE id = #{id}")
    int markReminded(@Param("id") String id);

    @Delete("DELETE FROM schedule WHERE id = #{id}")
    int delete(@Param("id") String id);

    @Select("SELECT * FROM schedule ORDER BY due_time")
    List<Schedule> findAll();

    @Select("""
            SELECT * FROM schedule
            WHERE status <> 'DONE' AND due_time >= #{from} AND due_time <= #{to}
            ORDER BY due_time
            """)
    List<Schedule> listUpcoming(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    /** 调度线程的条件扫描：到点、未完成、未提醒（详细设计图 5-5）。 */
    @Select("""
            SELECT * FROM schedule
            WHERE reminded = 0 AND status <> 'DONE'
              AND remind_at IS NOT NULL AND remind_at <= #{now}
            ORDER BY remind_at
            """)
    List<Schedule> findDueReminders(@Param("now") LocalDateTime now);

    @Select("SELECT COUNT(*) FROM schedule WHERE status <> 'DONE'")
    int countPending();
}
