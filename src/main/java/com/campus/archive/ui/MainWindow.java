package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.ReminderEvent;
import com.campus.archive.pattern.reminder.UiReminderObserver;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 主窗口：左侧一级导航 + 顶部搜索栏 + 中部内容区。
 * 一级入口：首页 / 我的档案 / 日程 / 统计 / 我的。
 */
public class MainWindow {

    private final AppContext ctx;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Map<String, Node> views = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private final Label pageTitle = UiKit.label("首页", "page-title");
    private final Label reminderBadge = UiKit.label("0", "badge");

    private String current = "home";

    public MainWindow(AppContext ctx) {
        this.ctx = ctx;
        build();
        show("home");
        wireReminderObserver();
    }

    public BorderPane root() {
        return root;
    }

    private void build() {
        // ---- 左侧导航 ----
        VBox nav = new VBox();
        nav.getStyleClass().add("nav-pane");
        nav.getChildren().add(UiKit.label("学业档案管理", "nav-brand"));

        views.put("home", new HomeView(ctx, this));
        views.put("archive", new ArchiveView(ctx));
        views.put("schedule", new ScheduleView(ctx));
        views.put("stats", new StatsView(ctx));
        views.put("settings", new SettingsView(ctx));

        for (Map.Entry<String, Node> e : views.entrySet()) {
            Button b = new Button(titleOf(e.getKey()));
            b.getStyleClass().add("nav-button");
            b.setMaxWidth(Double.MAX_VALUE);
            b.setOnAction(a -> show(e.getKey()));
            navButtons.put(e.getKey(), b);
            nav.getChildren().add(b);
        }

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        nav.getChildren().add(spacer);
        Label footer = UiKit.label("本地运行 · 无需登录\n数据不出本机", "muted");
        footer.setStyle("-fx-text-fill:#8ea3c7; -fx-padding: 0 16 8 16;");
        nav.getChildren().add(footer);

        // ---- 顶部栏 ----
        HBox topLeft = new HBox(12, pageTitle);
        topLeft.setAlignment(Pos.CENTER_LEFT);
        HBox topRight = new HBox(10, reminderBadge);
        topRight.setAlignment(Pos.CENTER_RIGHT);
        Region grow = new Region();
        HBox.setHgrow(grow, Priority.ALWAYS);
        HBox top = new HBox(topLeft, grow, topRight);
        top.getStyleClass().add("top-bar");
        top.setAlignment(Pos.CENTER_LEFT);

        content.setPadding(new Insets(0));
        root.setLeft(nav);
        root.setTop(top);
        root.setCenter(content);
        root.getStyleClass().add("root");
    }

    /** 切换一级页面。 */
    public void show(String key) {
        Node v = views.get(key);
        if (v == null) return;
        current = key;
        content.getChildren().setAll(v);
        pageTitle.setText(titleOf(key));
        navButtons.forEach((k, b) -> {
            b.getStyleClass().remove("nav-button-active");
            if (k.equals(key)) b.getStyleClass().add("nav-button-active");
        });
        if (v instanceof Refreshable r) r.refresh();
    }

    public String currentView() {
        return current;
    }

    public void setReminderCount(int n) {
        reminderBadge.setText(String.valueOf(n));
        reminderBadge.setVisible(n > 0);
        reminderBadge.setManaged(n > 0);
    }

    /**
     * 注册观察者模式中的界面观察者（模式 ③）。
     * 界面更新必须回到 JavaFX 应用线程，因此通过 Platform.runLater 派发。
     */
    private void wireReminderObserver() {
        ctx.reminderSubject().register(new UiReminderObserver(event ->
                javafx.application.Platform.runLater(() -> onReminder(event))));
    }

    private void onReminder(ReminderEvent event) {
        setReminderCount(ctx.reminderSubject().recentEvents().size());
        if (views.get("home") instanceof HomeView hv) {
            hv.pushReminder(event);
        }
    }

    public List<ReminderEvent> recentReminders() {
        return ctx.reminderSubject().recentEvents();
    }

    private static String titleOf(String key) {
        return switch (key) {
            case "home" -> "首页";
            case "archive" -> "我的档案";
            case "schedule" -> "日程提醒";
            case "stats" -> "统计";
            default -> "我的";
        };
    }

    /** 页面在被切回时刷新数据。 */
    public interface Refreshable {
        void refresh();
    }
}
