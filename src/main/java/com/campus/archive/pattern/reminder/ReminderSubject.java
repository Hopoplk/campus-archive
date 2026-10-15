package com.campus.archive.pattern.reminder;

import com.campus.archive.dto.ReminderEvent;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 【设计模式 ③ 观察者模式】被观察者（主题）。
 *
 * <p>线程安全设计（对应详细设计 5.6 节"共享资源"表）：
 * 注册/注销发生在 UI 线程，事件发布发生在 ReminderScheduler 调度线程，
 * 因此观察者列表使用 CopyOnWriteArrayList，保证遍历发布时不会被并发修改打断。</p>
 *
 * <p>同时保留最近若干条事件，供界面首次打开时补齐"离开期间错过的提醒"。</p>
 */
public class ReminderSubject {

    private static final int HISTORY_LIMIT = 50;

    private final CopyOnWriteArrayList<ReminderObserver> observers = new CopyOnWriteArrayList<>();
    private final Deque<ReminderEvent> history = new ArrayDeque<>();

    public void register(ReminderObserver observer) {
        if (observer != null && !observers.contains(observer)) {
            observers.add(observer);
        }
    }

    public void unregister(ReminderObserver observer) {
        observers.remove(observer);
    }

    public List<ReminderObserver> observers() {
        return List.copyOf(observers);
    }

    public int observerCount() {
        return observers.size();
    }

    /**
     * 通知全部观察者。逐个隔离异常：某个渠道失败只记入历史，
     * 不影响其余渠道，也不把异常抛回调度线程。
     */
    public void publish(ReminderEvent event) {
        if (event == null) return;
        synchronized (history) {
            history.addFirst(event);
            while (history.size() > HISTORY_LIMIT) history.removeLast();
        }
        for (ReminderObserver o : observers) {
            try {
                o.onReminder(event);
            } catch (RuntimeException e) {
                System.err.println("[提醒] 观察者 " + o.name() + " 处理失败：" + e.getMessage());
            }
        }
    }

    /** 最近事件快照（新的在前）。 */
    public List<ReminderEvent> recentEvents() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public void clearHistory() {
        synchronized (history) {
            history.clear();
        }
    }
}
