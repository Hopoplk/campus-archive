package com.campus.archive.pattern.reminder;

import com.campus.archive.dto.ReminderEvent;

/**
 * 【设计模式 ③ 观察者模式 Observer】提醒事件的观察者。
 *
 * <p>为什么在这里用观察者：一条到期事项需要<b>同时</b>触发多个互不相关的响应——
 * 写入运行日志、刷新首页提醒区、弹出系统托盘通知，将来还可能加邮件、日历订阅。
 * 若把这些调用直接写在 ReminderScheduler 里，调度器就要认识每一个具体渠道，
 * 新增一个渠道就得改一次调度器，且任一渠道抛异常会拖垮整轮扫描。</p>
 *
 * <p>观察者模式把"谁关心这件事"从"事情发生了"中解耦：调度器只认识本接口。</p>
 */
@FunctionalInterface
public interface ReminderObserver {

    /**
     * 观察者名称，便于日志与界面展示当前注册了哪些渠道。
     * 提供默认实现，使接口成为函数式接口——简单的观察者可以直接用 lambda 表达。
     */
    default String name() {
        return getClass().getSimpleName();
    }

    /**
     * 收到提醒。实现必须自行消化异常——一个渠道失败不能影响其他渠道。
     */
    void onReminder(ReminderEvent event);
}
