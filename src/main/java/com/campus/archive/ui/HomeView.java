package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.ReminderEvent;
import com.campus.archive.entity.Material;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;

import java.util.Map;

/**
 * 首页：只放"现在要做什么"——上传入口、近期提醒、最近材料与关键指标。
 * 刻意不堆砌高级功能，对应需求 NFR-UI-09 低信息负担。
 */
public class HomeView extends VBox implements MainWindow.Refreshable {

    private final AppContext ctx;
    private final MainWindow window;
    private final ListView<String> reminderList = new ListView<>();
    private final ListView<String> recentList = new ListView<>();
    private final Label totalValue = UiKit.label("0", "metric-value");
    private final Label privateValue = UiKit.label("0", "metric-value");
    private final Label scheduleValue = UiKit.label("0", "metric-value");
    private final Label trashedValue = UiKit.label("0", "metric-value");
    private final Label strategyHint = UiKit.label("", "pattern-badge");

    public HomeView(AppContext ctx, MainWindow window) {
        this.ctx = ctx;
        this.window = window;
        getStyleClass().add("content");
        setSpacing(16);
        build();
        refresh();
    }

    private void build() {
        // ---- 上传入口 ----
        Button upload = new Button("＋  上传材料");
        upload.getStyleClass().addAll("button", "primary-button");
        upload.setStyle("-fx-font-size: 15px; -fx-padding: 12 26 12 26;");
        upload.setOnAction(e -> new UploadDialog(ctx).showAndWait());

        Button scan = new Button("立即检查提醒（演示观察者模式）");
        scan.getStyleClass().addAll("button", "ghost-button");
        scan.setOnAction(e -> {
            int n = ctx.reminderScheduler().scanOnce();
            UiKit.info(n == 0
                    ? "本次扫描没有到期事项。\n可在「日程提醒」页新建一条把提醒时间设为 1 分钟后的日程再来试。"
                    : "扫描命中 " + n + " 条到期事项，已通过观察者分发给：\n"
                      + ctx.reminderSubject().observers().stream().map(o -> "· " + o.name()).reduce("", (a, b) -> a + b + "\n"));
        });

        HBox actions = new HBox(12, upload, scan);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox hero = new VBox(10,
                UiKit.label("把成绩、证书、日程和综测材料集中管理起来", "section-title"),
                UiKit.label("本地优先 · 无需登录 · 私密材料加密存放", "muted"),
                actions);
        hero.getStyleClass().add("card");

        // ---- 指标卡 ----
        HBox metrics = new HBox(14,
                metric("材料总数", totalValue),
                metric("私密材料", privateValue),
                metric("待办日程", scheduleValue),
                metric("回收站", trashedValue));
        for (var n : metrics.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // ---- 两栏列表 ----
        reminderList.setPlaceholder(UiKit.label("暂无提醒", "muted"));
        recentList.setPlaceholder(UiKit.label("还没有材料，点上方「上传材料」开始", "muted"));

        VBox left = new VBox(8, UiKit.label("近期提醒", "card-title"), reminderList);
        left.getStyleClass().add("card");
        VBox right = new VBox(8, UiKit.label("最近添加", "card-title"), recentList);
        right.getStyleClass().add("card");
        HBox.setHgrow(left, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(reminderList, Priority.ALWAYS);
        VBox.setVgrow(recentList, Priority.ALWAYS);
        HBox lists = new HBox(14, left, right);
        VBox.setVgrow(lists, Priority.ALWAYS);

        // ---- 当前策略提示：让评审一眼看到模式 ① 在用 ----
        VBox patternCard = new VBox(6,
                UiKit.label("设计模式应用情况", "card-title"),
                strategyHint,
                UiKit.label("本原型在材料归类、导出备份、日程提醒三处分别引入了策略、模板方法、观察者三种模式，"
                        + "详见「我的」页与随附的《第 5 周设计模式应用说明》。", "muted"));
        patternCard.getStyleClass().add("card");

        getChildren().addAll(hero, metrics, lists, patternCard);
    }

    private VBox metric(String title, Label value) {
        VBox box = new VBox(4, UiKit.label(title, "muted"), value);
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** 观察者回调：提醒一到就把内容推到首页顶部（对应设计模式 ③ 的界面观察者）。 */
    public void pushReminder(ReminderEvent event) {
        reminderList.getItems().add(0, event.text());
        reminderList.scrollTo(0);
    }

    @Override
    public void refresh() {
        Map<String, Object> stats = ctx.searchService().stats();
        totalValue.setText(String.valueOf(stats.get("total")));
        privateValue.setText(String.valueOf(stats.get("privateCount")));
        scheduleValue.setText(String.valueOf(stats.get("pendingSchedules")));
        trashedValue.setText(String.valueOf(stats.get("trashed")));
        strategyHint.setText("策略模式①：" + ctx.materialService().currentSuggestionStrategy());

        reminderList.getItems().setAll(
                ctx.reminderSubject().recentEvents().stream().map(ReminderEvent::text).toList());
        if (reminderList.getItems().isEmpty()) {
            ctx.scheduleService().listUpcoming(14).stream().limit(8)
                    .forEach(s -> reminderList.getItems().add(
                            "· " + s.getTitle() + "　截止 " + UiKit.fmt(s.getDueTime()) + "　[" + s.getStatusLabel() + "]"));
        }
        window.setReminderCount(ctx.reminderSubject().recentEvents().size());

        recentList.getItems().setAll(ctx.searchService().recent(8).stream()
                .map(m -> "· " + m.getFileName() + "　" + m.getCategoryName() + "　" + UiKit.fmtDate(m.getCreatedAt())
                        + (m.getIsPrivate() ? "　[私密]" : ""))
                .toList());
    }
}
