package com.campus.archive.pattern;

import com.campus.archive.dto.ReminderEvent;
import com.campus.archive.pattern.reminder.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设计模式 ③ 观察者模式的验证。
 * 重点验证：一对多分发、注销生效、单个观察者异常不影响其他观察者、并发发布安全。
 */
@DisplayName("设计模式③ 观察者模式：日程提醒分发")
class ReminderObserverTest {

    private ReminderEvent event(String title) {
        return new ReminderEvent("s-1", title, LocalDateTime.now().plusMinutes(5), false);
    }

    /** 记录收到的每一条事件。 */
    private static class Recorder implements ReminderObserver {
        final List<ReminderEvent> received = new ArrayList<>();
        final String name;

        Recorder(String name) {
            this.name = name;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public void onReminder(ReminderEvent event) {
            received.add(event);
        }
    }

    @Test
    @DisplayName("一条事件同时送达全部观察者——这就是观察者模式的价值")
    void oneEventReachesAllObservers() {
        ReminderSubject subject = new ReminderSubject();
        Recorder log = new Recorder("日志");
        Recorder ui = new Recorder("首页");
        Recorder tray = new Recorder("托盘");
        subject.register(log);
        subject.register(ui);
        subject.register(tray);

        assertEquals(3, subject.observerCount());
        subject.publish(event("提交综测材料"));

        assertEquals(1, log.received.size());
        assertEquals(1, ui.received.size());
        assertEquals(1, tray.received.size());
        assertEquals("提交综测材料", ui.received.get(0).title());
    }

    @Test
    @DisplayName("注销后不再收到事件，且重复注册同一观察者不会重复通知")
    void unregisterAndDeduplicate() {
        ReminderSubject subject = new ReminderSubject();
        Recorder a = new Recorder("A");
        subject.register(a);
        subject.register(a);
        assertEquals(1, subject.observerCount(), "同一观察者重复注册应被忽略");

        subject.publish(event("X"));
        assertEquals(1, a.received.size());

        subject.unregister(a);
        subject.publish(event("Y"));
        assertEquals(1, a.received.size(), "注销后不应再收到事件");
    }

    @Test
    @DisplayName("一个观察者抛异常，不影响其余观察者收到事件")
    void failingObserverDoesNotBreakOthers() {
        ReminderSubject subject = new ReminderSubject();
        Recorder good = new Recorder("正常渠道");
        subject.register(new ReminderObserver() {
            @Override
            public String name() {
                return "故障渠道";
            }

            @Override
            public void onReminder(ReminderEvent event) {
                throw new IllegalStateException("模拟托盘不可用");
            }
        });
        subject.register(good);

        assertDoesNotThrow(() -> subject.publish(event("提醒")));
        assertEquals(1, good.received.size());
    }

    @Test
    @DisplayName("保留最近事件，供界面首次打开时补齐离开期间错过的提醒")
    void keepsRecentHistory() {
        ReminderSubject subject = new ReminderSubject();
        subject.register(new Recorder("R"));
        for (int i = 1; i <= 5; i++) subject.publish(event("第" + i + "条"));

        List<ReminderEvent> recent = subject.recentEvents();
        assertEquals(5, recent.size());
        assertEquals("第5条", recent.get(0).title(), "最近事件应排在最前");

        subject.clearHistory();
        assertTrue(subject.recentEvents().isEmpty());
    }

    @Test
    @DisplayName("调度线程发布与界面线程注册可以并发进行")
    void concurrentPublishAndRegisterIsSafe() throws Exception {
        ReminderSubject subject = new ReminderSubject();
        AtomicInteger delivered = new AtomicInteger();
        subject.register(e -> delivered.incrementAndGet());

        int rounds = 200;
        CountDownLatch latch = new CountDownLatch(rounds);
        Thread publisher = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                subject.publish(event("并发 " + i));
                latch.countDown();
            }
        });
        Thread registrar = new Thread(() -> {
            for (int i = 0; i < 50; i++) {
                Recorder tmp = new Recorder("临时" + i);
                subject.register(tmp);
                subject.unregister(tmp);
            }
        });
        publisher.start();
        registrar.start();
        assertTrue(latch.await(20, TimeUnit.SECONDS));
        publisher.join();
        registrar.join();

        assertTrue(delivered.get() >= rounds, "已注册的观察者应当收到全部事件，实际 " + delivered.get());
    }

    @Test
    @DisplayName("界面观察者通过 Consumer 注入，pattern 包因此不依赖 JavaFX")
    void uiObserverIsUiFrameworkAgnostic() {
        List<String> seen = new ArrayList<>();
        ReminderObserver observer = new UiReminderObserver(e -> seen.add(e.text()));
        observer.onReminder(event("综测材料提交"));
        assertEquals(1, seen.size());
        assertTrue(seen.get(0).contains("综测材料提交"));
    }
}
