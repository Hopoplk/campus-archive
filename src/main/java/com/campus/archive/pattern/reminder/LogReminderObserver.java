package com.campus.archive.pattern.reminder;

import com.campus.archive.dto.ReminderEvent;

/** 观察者实现 A：写入运行日志。便于答辩时从日志证明"提醒确实触发了"，而不只是界面上看到。 */
public class LogReminderObserver implements ReminderObserver {

    @Override
    public String name() {
        return "运行日志";
    }

    @Override
    public void onReminder(ReminderEvent event) {
        System.out.println("[提醒][" + java.time.LocalDateTime.now() + "] " + event.text());
    }
}
