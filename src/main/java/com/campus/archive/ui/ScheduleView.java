package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.ReminderEvent;
import com.campus.archive.dto.Result;
import com.campus.archive.entity.Schedule;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 日程页。右下角的"立即扫描"按钮用于当场演示<b>设计模式 ③ 观察者模式</b>：
 * 一条事件会同时被日志、系统托盘、首页提醒区三个观察者接收。
 */
public class ScheduleView extends BorderPane implements MainWindow.Refreshable {

    private final AppContext ctx;
    private final ListView<String> list = new ListView<>();
    private final List<Schedule> cache = new java.util.ArrayList<>();
    private final TextField titleField = new TextField();
    private final DatePicker dueDate = new DatePicker(LocalDate.now().plusDays(7));
    private final TextField remindField = new TextField("09:00");
    private final TextField remarkField = new TextField();
    private final Label observerLabel = UiKit.label("", "muted");

    public ScheduleView(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("content");
        build();
        refresh();
    }

    private void build() {
        list.setPlaceholder(UiKit.label("还没有日程，右侧新建一条试试", "muted"));
        list.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> {
            int i = b.intValue();
            if (i >= 0 && i < cache.size()) {
                Schedule s = cache.get(i);
                titleField.setText(s.getTitle());
                remarkField.setText(s.getRemark() == null ? "" : s.getRemark());
                if (s.getDueTime() != null) dueDate.setValue(s.getDueTime().toLocalDate());
                if (s.getRemindAt() != null) remindField.setText(s.getRemindAt().toLocalTime().withSecond(0).toString());
            }
        });

        Button complete = new Button("标记完成");
        complete.getStyleClass().addAll("button", "ghost-button");
        complete.setOnAction(e -> onComplete());
        Button delete = new Button("删除");
        delete.getStyleClass().addAll("button", "ghost-button");
        delete.setOnAction(e -> onDelete());
        Button scan = new Button("立即扫描提醒");
        scan.getStyleClass().addAll("button", "primary-button");
        scan.setOnAction(e -> onScan());
        Button scanThirty = new Button("演示：20 秒后再扫描一次");
        scanThirty.getStyleClass().addAll("button", "ghost-button");
        scanThirty.setOnAction(e -> {
            UiKit.info("已安排：20 秒后自动扫描一次，届时请留意首页提醒区、右下角托盘通知与控制台日志。");
            var t = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(20));
            t.setOnFinished(ev -> onScan());
            t.play();
        });

        HBox actions = new HBox(8, complete, delete, scan, scanThirty);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox left = new VBox(10, UiKit.label("日程与提醒", "card-title"), list, actions, observerLabel);
        left.getStyleClass().add("card");
        VBox.setVgrow(list, Priority.ALWAYS);

        // ---- 右侧编辑区 ----
        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        int r = 0;
        form.add(new Label("事项名称"), 0, r);
        form.add(titleField, 1, r++);
        form.add(new Label("截止日期"), 0, r);
        form.add(dueDate, 1, r++);
        form.add(new Label("提醒时间"), 0, r);
        form.add(remindField, 1, r++);
        form.add(new Label("备注"), 0, r);
        form.add(remarkField, 1, r++);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(70);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1);

        Button save = new Button("保存为新日程");
        save.getStyleClass().addAll("button", "primary-button");
        save.setOnAction(e -> onSave());
        Button reset = new Button("清空表单");
        reset.getStyleClass().addAll("button", "ghost-button");
        reset.setOnAction(e -> {
            titleField.clear();
            remarkField.clear();
            dueDate.setValue(LocalDate.now().plusDays(7));
            remindField.setText("09:00");
            list.getSelectionModel().clearSelection();
        });

        VBox right = new VBox(12,
                UiKit.label("新建 / 编辑事项", "card-title"),
                form,
                new HBox(8, save, reset),
                UiKit.label("提醒由独立的调度线程每 60 秒扫描一次，"
                        + "命中后通过观察者模式分发给所有提醒渠道，纯本地、无需联网。", "muted"));
        right.getStyleClass().add("card");
        right.setPrefWidth(360);

        setCenter(left);
        setRight(right);
        BorderPane.setMargin(right, new Insets(0, 0, 0, 14));
    }

    @Override
    public void refresh() {
        cache.clear();
        cache.addAll(ctx.scheduleService().listAll());
        list.getItems().setAll(cache.stream()
                .map(s -> String.format("%s　%s　截止 %s　提醒 %s　[%s]",
                        s.isPending() ? "○" : "●",
                        s.getTitle(),
                        UiKit.fmt(s.getDueTime()),
                        UiKit.fmt(s.getRemindAt()),
                        s.getStatusLabel()))
                .toList());
        observerLabel.setText("已注册的提醒渠道：" + ctx.reminderSubject().observers().stream()
                .map(o -> o.name()).reduce((a, b) -> a + "、" + b).orElse("无"));
    }

    private void onSave() {
        if (titleField.getText().isBlank()) {
            UiKit.warn("请填写事项名称");
            return;
        }
        LocalDateTime due = LocalDateTime.of(dueDate.getValue(), LocalTime.of(23, 59));
        LocalDateTime remind = due;
        try {
            if (!remindField.getText().isBlank()) {
                remind = LocalDateTime.of(dueDate.getValue(), LocalTime.parse(remindField.getText().trim()));
            }
        } catch (RuntimeException e) {
            UiKit.warn("提醒时间格式应为 HH:mm，例如 09:00");
            return;
        }

        Schedule s = new Schedule();
        s.setTitle(titleField.getText().trim());
        s.setDueTime(due);
        s.setRemindAt(remind);
        s.setRemark(remarkField.getText().isBlank() ? null : remarkField.getText().trim());
        Result<Void> res = ctx.scheduleService().create(s);
        if (res.isSuccess()) {
            UiKit.info("已保存，提醒时间：" + UiKit.fmt(remind));
            refresh();
        } else {
            UiKit.error("[" + res.getCode() + "] " + res.getMessage());
        }
    }

    private void onComplete() {
        Schedule s = selected();
        if (s == null) return;
        ctx.scheduleService().complete(s.getId());
        refresh();
    }

    private void onDelete() {
        Schedule s = selected();
        if (s == null) return;
        if (!UiKit.confirm("删除日程", "确定删除「" + s.getTitle() + "」吗？")) return;
        ctx.scheduleService().delete(s.getId());
        refresh();
    }

    private void onScan() {
        int n = ctx.reminderScheduler().scanOnce();
        List<ReminderEvent> recent = ctx.reminderSubject().recentEvents();
        StringBuilder sb = new StringBuilder();
        sb.append(n == 0 ? "本次扫描没有命中到期事项。\n" : "扫描命中 " + n + " 条到期事项。\n");
        sb.append("\n观察者模式③：事件已分发给以下渠道\n");
        ctx.reminderSubject().observers().forEach(o -> sb.append("· ").append(o.name()).append('\n'));
        if (!recent.isEmpty()) {
            sb.append("\n最近事件：\n· ").append(recent.get(0).text());
        }
        UiKit.info(sb.toString());
        refresh();
    }

    private Schedule selected() {
        int i = list.getSelectionModel().getSelectedIndex();
        if (i < 0 || i >= cache.size()) {
            UiKit.warn("请先在左侧选择一条日程");
            return null;
        }
        return cache.get(i);
    }
}
