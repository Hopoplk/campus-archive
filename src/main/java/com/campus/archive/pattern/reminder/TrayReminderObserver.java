package com.campus.archive.pattern.reminder;

import com.campus.archive.dto.ReminderEvent;

import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * 观察者实现 C：系统托盘通知。
 * 纯本地能力，不联网；托盘不可用时静默降级（只写一条日志），不影响其他观察者。
 */
public class TrayReminderObserver implements ReminderObserver {

    private TrayIcon trayIcon;

    @Override
    public String name() {
        return "系统托盘通知";
    }

    @Override
    public void onReminder(ReminderEvent event) {
        try {
            if (!SystemTray.isSupported()) return;
            if (trayIcon == null) {
                SystemTray tray = SystemTray.getSystemTray();
                BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = img.createGraphics();
                g.setColor(new Color(0x2F5597));
                g.fillOval(1, 1, 14, 14);
                g.dispose();
                trayIcon = new TrayIcon(img, "学业档案提醒");
                trayIcon.setImageAutoSize(true);
                tray.add(trayIcon);
            }
            trayIcon.displayMessage("学业档案提醒", event.text(), TrayIcon.MessageType.INFO);
        } catch (Exception e) {
            // 托盘不可用属于可接受的降级，不影响其他观察者
            System.out.println("[提醒] 系统托盘不可用：" + e.getClass().getSimpleName());
        }
    }
}
