package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.Result;
import com.campus.archive.dto.Suggestion;
import com.campus.archive.dto.UploadOptions;
import com.campus.archive.dto.UploadSummary;
import com.campus.archive.entity.Category;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 上传对话框。这里同时是<b>设计模式 ① 策略模式</b>的可视化演示点：
 * 切换"归类建议方式"下拉框即可在运行期替换算法，下方每个文件的建议文本会立即重算，
 * 而 {@code MaterialService} 与数据层完全不需要改动。
 */
public class UploadDialog extends Dialog<Void> {

    private final AppContext ctx;
    private final ListView<String> fileList = new ListView<>();
    private final List<File> files = new ArrayList<>();
    private final List<Suggestion> suggestions = new ArrayList<>();
    private final ComboBox<String> strategyBox = new ComboBox<>();
    private final ComboBox<String> categoryBox = new ComboBox<>();
    private final CheckBox useSuggestion = new CheckBox("使用智能建议（自动分类与规范化命名）");
    private final TextField tagField = new TextField();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = UiKit.label("尚未选择文件", "muted");

    public UploadDialog(AppContext ctx) {
        this.ctx = ctx;
        setTitle("上传材料");
        setHeaderText("选择文件后会给出分类与命名建议，你也可以完全跳过，直接保存");
        setResizable(true);

        ButtonType uploadType = new ButtonType("开始上传", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(uploadType, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(720);

        build();
        getDialogPane().getButtonTypes().stream()
                .filter(b -> b.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .findFirst()
                .ifPresent(b -> {
                    Node node = getDialogPane().lookupButton(b);
                    node.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
                        if (files.isEmpty()) {
                            UiKit.warn("请先选择要上传的文件");
                            ev.consume();
                            return;
                        }
                        ev.consume();
                        doUpload();
                    });
                });
    }

    private void build() {
        // ---- 策略选择（模式 ①）----
        strategyBox.setItems(FXCollections.observableArrayList(ctx.materialService().availableSuggestionStrategies()));
        strategyBox.getSelectionModel().select(ctx.materialService().currentSuggestionStrategy());
        strategyBox.setOnAction(e -> {
            ctx.materialService().useSuggestionStrategy(strategyBox.getValue());
            recomputeSuggestions();
        });

        categoryBox.getItems().add("按建议自动分类");
        for (Category c : ctx.repos().query(d -> d.category().findAll())) {
            categoryBox.getItems().add(c.getName());
        }
        categoryBox.getSelectionModel().selectFirst();

        useSuggestion.setSelected(true);
        useSuggestion.setOnAction(e -> {
            strategyBox.setDisable(!useSuggestion.isSelected());
            recomputeSuggestions();
        });

        tagField.setPromptText("可选：标签，多个用逗号分隔，例如：四级,求职");

        Button choose = new Button("选择文件…");
        choose.getStyleClass().addAll("button", "primary-button");
        choose.setOnAction(e -> chooseFiles());
        Button clear = new Button("清空");
        clear.getStyleClass().addAll("button", "ghost-button");
        clear.setOnAction(e -> {
            files.clear();
            suggestions.clear();
            fileList.getItems().clear();
            status.setText("尚未选择文件");
        });

        HBox chooseRow = new HBox(8, choose, clear, status);
        chooseRow.setAlignment(Pos.CENTER_LEFT);

        Label patternHint = UiKit.label("策略模式①：归类建议算法可在运行期替换，切换后立即重算下方建议",
                "pattern-badge");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.add(new Label("归类建议方式"), 0, 0);
        form.add(strategyBox, 1, 0);
        form.add(new Label("目标分类"), 2, 0);
        form.add(categoryBox, 3, 0);
        form.add(new Label("标签"), 0, 1);
        form.add(tagField, 1, 1, 3, 1);
        ColumnConstraints c0 = new ColumnConstraints();
        c0.setMinWidth(96);
        ColumnConstraints c1 = new ColumnConstraints();
        c1.setHgrow(Priority.ALWAYS);
        ColumnConstraints c2 = new ColumnConstraints();
        c2.setMinWidth(66);
        ColumnConstraints c3 = new ColumnConstraints();
        c3.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(c0, c1, c2, c3);

        fileList.setPrefHeight(230);
        fileList.setPlaceholder(UiKit.label("还没有选择文件", "muted"));

        progress.setPrefWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        VBox box = new VBox(12, chooseRow, patternHint, form, useSuggestion, fileList, progress);
        box.setPadding(new Insets(6, 4, 4, 4));
        getDialogPane().setContent(box);
    }

    private void chooseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择要归档的材料（可多选）");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("常见材料格式", "*.pdf", "*.doc", "*.docx", "*.png", "*.jpg", "*.jpeg", "*.xls", "*.xlsx"),
                new FileChooser.ExtensionFilter("全部文件", "*.*"));
        Window owner = getDialogPane().getScene() == null ? null : getDialogPane().getScene().getWindow();
        List<File> picked = fc.showOpenMultipleDialog(owner);
        if (picked == null || picked.isEmpty()) return;

        int accepted = 0;
        for (File f : picked) {
            if (f.length() > 200L * 1024 * 1024) {
                UiKit.warn("「" + f.getName() + "」超过 200 MB，已跳过");
                continue;
            }
            if (files.size() >= 200) {
                UiKit.warn("一次最多上传 200 个文件，其余请分批处理");
                break;
            }
            files.add(f);
            accepted++;
        }
        status.setText("已选择 " + files.size() + " 个文件（本次新增 " + accepted + " 个）");
        recomputeSuggestions();
    }

    /** 用当前策略为每个文件重算建议，并刷新列表显示。 */
    private void recomputeSuggestions() {
        suggestions.clear();
        fileList.getItems().clear();
        for (File f : files) {
            Suggestion s = useSuggestion.isSelected()
                    ? ctx.materialService().suggest(f)
                    : Suggestion.NONE;
            suggestions.add(s);
            fileList.getItems().add(String.format("  %s    %s", f.getName(), s.displayText()));
        }
    }

    private void doUpload() {
        UploadOptions opt = new UploadOptions()
                .setSuggestCategory(useSuggestion.isSelected())
                .setTags(tagField.getText().isBlank() ? null : tagField.getText().trim());

        int ci = categoryBox.getSelectionModel().getSelectedIndex();
        if (ci > 0) {
            // 用户显式指定了目标分类，覆盖策略给出的建议
            List<Category> cats = ctx.repos().query(d -> d.category().findAll());
            opt.setCategoryId(cats.get(ci - 1).getId());
        }

        progress.setVisible(true);
        progress.setManaged(true);
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        status.setText("正在上传…");

        Thread worker = new Thread(() -> {
            Result<UploadSummary> r = ctx.materialService().upload(new ArrayList<>(files), opt);
            Platform.runLater(() -> {
                progress.setProgress(1);
                status.setText("上传结束");
                if (!r.isSuccess()) {
                    UiKit.error("[" + r.getCode() + "] " + r.getMessage());
                    return;
                }
                UploadSummary s = r.getData();
                StringBuilder sb = new StringBuilder(s.summaryText());
                if (!s.getDuplicateWarnings().isEmpty()) {
                    sb.append("\n\n疑似重复（系统建议确认后再决定是否保留）：\n");
                    s.getDuplicateWarnings().forEach(n -> sb.append("· ").append(n).append('\n'));
                }
                if (s.hasFailure()) {
                    sb.append("\n失败明细：\n");
                    s.getFailed().forEach(f -> sb.append("· ").append(f.fileName())
                            .append("：[").append(f.code()).append("] ").append(f.message()).append('\n'));
                }
                UiKit.info(sb.toString());
                setResult(null);
                close();
            });
        }, "upload-worker");
        worker.setDaemon(true);
        worker.start();
    }
}
