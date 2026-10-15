package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.ExportResult;
import com.campus.archive.dto.Result;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;

/**
 * 我的：低频功能集中管理——安全设置、导入导出、回收站入口，以及设计模式应用的现场自证面板。
 */
public class SettingsView extends VBox implements MainWindow.Refreshable {

    private final AppContext ctx;
    private final Label securityStatus = UiKit.label("", "muted");
    private final Label dataPath = UiKit.label("", "muted");
    private final Label patternPanel = UiKit.label("", "muted");

    public SettingsView(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("content");
        setSpacing(14);
        build();
        refresh();
    }

    private void build() {
        getChildren().addAll(securityCard(), dataCard(), backupCard(), patternCard());
    }

    private VBox securityCard() {
        Button init = new Button("设置 / 验证安全密钥");
        init.getStyleClass().addAll("button", "primary-button");
        init.setOnAction(e -> {
            if (ctx.securityService().isEnabled()) {
                PrivateUnlockDialog.ensureUnlocked(ctx);
            } else {
                PrivateUnlockDialog.ensureUnlocked(ctx);
            }
            refresh();
        });

        Button lock = new Button("立即锁定私密空间");
        lock.getStyleClass().addAll("button", "ghost-button");
        lock.setOnAction(e -> {
            ctx.securityService().lock();
            UiKit.info("私密空间已锁定，派生密钥已从内存清零");
            refresh();
        });

        HBox actions = new HBox(8, init, lock);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, UiKit.label("私密空间与安全密钥", "card-title"), securityStatus, actions,
                UiKit.label("密钥经 PBKDF2-HMAC-SHA256（10 万轮）派生，系统只保存盐值与校验值，"
                        + "明文密钥从不落盘；私密材料以 AES-256-GCM 加密后存放于 vault/ 目录。", "muted"));
        box.getStyleClass().add("card");
        return box;
    }

    private VBox dataCard() {
        dataPath.getStyleClass().add("code-area");
        VBox box = new VBox(8, UiKit.label("数据位置", "card-title"), dataPath,
                UiKit.label("无登录体系意味着数据完全属于用户本人；请通过下方备份功能定期导出，"
                        + "并在更换设备时导入。", "muted"));
        box.getStyleClass().add("card");
        return box;
    }

    private VBox backupCard() {
        Button export = new Button("导出完整档案备份");
        export.getStyleClass().addAll("button", "primary-button");
        export.setOnAction(e -> onExport());

        Button importBtn = new Button("从备份包导入");
        importBtn.getStyleClass().addAll("button", "ghost-button");
        importBtn.setOnAction(e -> onImport());

        HBox actions = new HBox(8, export, importBtn);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, UiKit.label("导入 / 导出与备份", "card-title"), actions,
                UiKit.label("备份包内含全部材料文件、schedule.json（日程）与 manifest.json（清单，含 SHA-256 校验和）。"
                        + "导入采用单事务，失败会整体回滚并清理已写入的文件。", "muted"));
        box.getStyleClass().add("card");
        return box;
    }

    private VBox patternCard() {
        patternPanel.setWrapText(true);
        Button demoStrategy = new Button("演示：切换归类建议策略");
        demoStrategy.getStyleClass().addAll("button", "ghost-button");
        demoStrategy.setOnAction(e -> {
            var list = ctx.materialService().availableSuggestionStrategies();
            ChoiceDialog<String> d = new ChoiceDialog<>(ctx.materialService().currentSuggestionStrategy(), list);
            d.setTitle("策略模式①");
            d.setHeaderText("运行期替换归类建议算法");
            d.setContentText("选择策略（不需要重启，也不改动业务代码）");
            d.showAndWait().ifPresent(name -> {
                ctx.materialService().useSuggestionStrategy(name);
                UiKit.info("已切换为：" + ctx.materialService().currentSuggestionStrategy()
                        + "\n下次上传时，建议结果将由新算法产生。");
                refresh();
            });
        });

        Button demoReminder = new Button("演示：触发一次观察者分发");
        demoReminder.getStyleClass().addAll("button", "ghost-button");
        demoReminder.setOnAction(e -> {
            int n = ctx.reminderScheduler().scanOnce();
            UiKit.info("已触发提醒扫描，命中 " + n + " 条。\n"
                    + "事件同时送达：" + ctx.reminderSubject().observers().stream()
                    .map(o -> o.name()).reduce((a, b) -> a + "、 " + b).orElse("无")
                    + (n == 0 ? "\n\n（没有到期事项，可在日程页新建一条提醒时间为 1 分钟后的日程再试）" : ""));
        });

        HBox actions = new HBox(8, demoStrategy, demoReminder);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(8, UiKit.label("设计模式应用（现场自证）", "card-title"),
                patternPanel, actions);
        box.getStyleClass().add("card");
        return box;
    }

    @Override
    public void refresh() {
        boolean enabled = ctx.securityService().isEnabled();
        boolean unlocked = ctx.securityService().isUnlocked();
        securityStatus.setText("状态：" + (enabled ? (unlocked ? "已设置密钥，当前【已解锁】" : "已设置密钥，当前【已锁定】") : "尚未启用")
                + (enabled ? "　连续验证失败次数：" + ctx.securityService().failCount() : ""));

        dataPath.setText("应用数据目录：" + ctx.database().home()
                + "\n数据库文件　：" + ctx.database().dbFile()
                + "\n原始材料仓库：files/　私密密文区：vault/　备份输出：backup/");

        patternPanel.setText(
                "① 策略模式（归类建议）：SuggestionEngine 持有 CategorySuggestionStrategy，当前策略 = "
                        + ctx.materialService().currentSuggestionStrategy() + "\n"
                        + "② 模板方法模式（导出/备份）：AbstractArchiveExporter.export() 固定流程骨架，"
                        + "具体实现 = SelectionExportExporter（选定材料导出）、FullBackupExporter（全量备份）\n"
                        + "③ 观察者模式（日程提醒）：ReminderSubject 当前注册 " + ctx.reminderSubject().observerCount()
                        + " 个观察者 = " + ctx.reminderSubject().observers().stream()
                        .map(o -> o.name()).reduce((a, b) -> a + "、 " + b).orElse("无"));
    }

    private void onExport() {
        boolean hasPrivate = ctx.searchService().stats().get("privateCount") instanceof Number n && n.longValue() > 0;
        boolean includePrivate = false;
        if (hasPrivate) {
            Alert a = new Alert(Alert.AlertType.CONFIRMATION);
            a.setTitle("导出选项");
            a.setHeaderText("档案中包含私密材料");
            a.setContentText("私密材料一旦进入备份包就是普通文件，任何人拿到备份包都能读取。\n"
                    + "确定要把私密材料一并导出吗？\n（选择「取消」则只导出非私密材料）");
            a.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);
            var r = a.showAndWait();
            includePrivate = r.isPresent() && r.get() == ButtonType.YES;
            if (includePrivate && !PrivateUnlockDialog.ensureUnlocked(ctx)) return;
        }

        DirectoryChooser dc = new DirectoryChooser();
        dc.setTitle("选择备份输出目录");
        File dir = dc.showDialog(getScene() == null ? null : getScene().getWindow());
        final boolean withPrivate = includePrivate;
        Path target = dir != null ? dir.toPath() : ctx.database().dir("backup");

        Result<ExportResult> r = ctx.backupService().exportAll(target, withPrivate);
        if (r.isSuccess()) {
            UiKit.info(r.getData().summaryText());
        } else {
            UiKit.error("[" + r.getCode() + "] " + r.getMessage());
        }
    }

    private void onImport() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择备份包");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("备份包", "*.zip"));
        File f = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (f == null) return;
        if (!UiKit.confirm("导入备份", "导入会向当前档案追加内容（不会覆盖已有材料）。\n确定导入「" + f.getName() + "」吗？")) return;
        Result<String> r = ctx.backupService().importFrom(f.toPath());
        if (r.isSuccess()) {
            UiKit.info(r.getData());
        } else {
            UiKit.error("[" + r.getCode() + "] " + r.getMessage());
        }
    }
}
