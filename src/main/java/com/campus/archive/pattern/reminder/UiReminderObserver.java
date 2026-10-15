package com.campus.archive.pattern.reminder;

import com.campus.archive.dto.ReminderEvent;

import java.util.function.Consumer;

/**
 * 观察者实现 B：推送到界面（首页"近期提醒"区域置顶 + 角标数字）。
 *
 * <p>刻意用 {@link Consumer} 而不是直接引用 JavaFX 控件：pattern 包因此完全不依赖表现层，
 * 单元测试里可以传一个普通 lambda 来断言"界面被通知了"。</p>
 */
public class UiReminderObserver implements ReminderObserver {

    private final Consumer<ReminderEvent> sink;

    public UiReminderObserver(Consumer<ReminderEvent> sink) {
        this.sink = sink;
    }

    @Override
    public String name() {
        return "首页提醒区";
    }

    @Override
    public void onReminder(ReminderEvent event) {
        if (sink != null) sink.accept(event);
    }
}
