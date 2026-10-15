package com.campus.archive.dto;

import java.time.LocalDateTime;

/**
 * 提醒事件。由 ReminderScheduler 构造，通过观察者模式（pattern.reminder 包）分发给各提醒渠道。
 *
 * @param scheduleId 事项 ID
 * @param title      事项标题
 * @param dueTime    截止时间
 * @param overdue    触发时是否已经逾期
 */
public record ReminderEvent(String scheduleId, String title, LocalDateTime dueTime, boolean overdue) {

    public String text() {
        String when = dueTime == null ? "" : dueTime.toString().replace('T', ' ').substring(0, 16);
        return (overdue ? "【已逾期】" : "【提醒】") + title + "　截止 " + when;
    }
}
