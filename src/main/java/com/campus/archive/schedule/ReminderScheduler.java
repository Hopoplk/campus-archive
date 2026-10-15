package com.campus.archive.schedule;

import com.campus.archive.dto.ReminderEvent;
import com.campus.archive.entity.Schedule;
import com.campus.archive.pattern.reminder.ReminderSubject;
import com.campus.archive.service.ScheduleService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 提醒调度线程（详细设计图 5-5 活动图）。
 *
 * <p>每 60 秒唤醒一次，条件扫描"提醒时间已到、未完成、未提醒"的事项，
 * 命中后构造 {@link ReminderEvent} 交给 {@link ReminderSubject} 分发给全部观察者——
 * 调度器本身不需要知道有哪些提醒渠道（设计模式 ③）。</p>
 *
 * <p>纯本地定时器，无网络依赖；reminded 标记保证幂等，应用重启也不会重复提醒。</p>
 */
public class ReminderScheduler {

    private static final int SCAN_INTERVAL_SECONDS = 60;

    private final ScheduleService scheduleService;
    private final ReminderSubject subject;
    private ScheduledExecutorService executor;

    public ReminderScheduler(ScheduleService scheduleService, ReminderSubject subject) {
        this.scheduleService = scheduleService;
        this.subject = subject;
    }

    public void start() {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ReminderScheduler");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::safeScan, 3, SCAN_INTERVAL_SECONDS, TimeUnit.SECONDS);
        System.out.println("[提醒] 调度线程已启动，扫描周期 " + SCAN_INTERVAL_SECONDS + " 秒");
    }

    public void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private void safeScan() {
        try {
            scanOnce();
        } catch (RuntimeException e) {
            // 调度线程不能因为一次异常而终止
            System.err.println("[提醒] 扫描失败：" + e.getMessage());
        }
    }

    /**
     * 执行一次扫描，返回命中的提醒条数。
     * 独立暴露为公开方法，便于单元测试与答辩时"手动触发一次扫描"当场演示。
     */
    public int scanOnce() {
        return scanOnce(LocalDateTime.now());
    }

    public int scanOnce(LocalDateTime now) {
        List<Schedule> due = scheduleService.findDue(now);
        for (Schedule s : due) {
            boolean overdue = s.getDueTime() != null && s.getDueTime().isBefore(now);
            subject.publish(new ReminderEvent(s.getId(), s.getTitle(), s.getDueTime(), overdue));
            scheduleService.markReminded(s.getId());
            if (overdue) scheduleService.markOverdue(s.getId());
        }
        return due.size();
    }
}
