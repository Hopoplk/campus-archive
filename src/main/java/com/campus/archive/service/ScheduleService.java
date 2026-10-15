package com.campus.archive.service;

import com.campus.archive.dto.Result;
import com.campus.archive.entity.Schedule;

import java.util.List;

/** 日程与提醒服务（对应需求 UR-06、AC-06）。 */
public interface ScheduleService {

    Result<Void> create(Schedule item);

    Result<Void> complete(String id);

    Result<Void> delete(String id);

    List<Schedule> listAll();

    List<Schedule> listUpcoming(int days);

    int countPending();

    /* ---- 供 ReminderScheduler 调度线程使用 ---- */

    /** 条件扫描：提醒时间已到、未完成、未提醒（详细设计图 5-5）。 */
    List<Schedule> findDue(java.time.LocalDateTime now);

    /** 写入提醒幂等标记，保证同一条事项不会重复提醒。 */
    void markReminded(String id);

    void markOverdue(String id);
}
