package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.Result;
import com.campus.archive.dto.UploadOptions;
import com.campus.archive.dto.UploadSummary;
import com.campus.archive.entity.Category;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传对话框（单文件）：只做一件事——确定这份材料放进哪个分类。
 *
 * <p>分类树直接复用档案页的分类结构，全部展开；为了让层级一眼可辨，
 * 除缩进外在名称前标出层级数字（顶层为 0，每下一层 +1）。</p>
 *
 * <p>不再提供目标分类下拉、归类建议策略与多文件批量处理：
 * 选文件在首页完成（按钮或拖拽），本窗口只负责落位。</p>
 */
public class UploadDialog extends Dialog<Void> {

    private final AppContext ctx;
    private final File file;
    private final TreeView<Category> categoryTree = new TreeView<>();
    private final TextField tagField = new TextField();
    private final ProgressBar progress = new ProgressBar(0);
    private final Label status = UiKit.label("请选择存放分类", "muted");

    private Node uploadButton;

    public UploadDialog(AppContext ctx, File file) {
        this.ctx = ctx;
        this.file = file;

        setTitle("上传材料");
        setHeaderText("选择存放分类");
        setResizable(true);

        ButtonType uploadType = new ButtonType("上传", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(uploadType, ButtonType.CANCEL);
        getDialogPane().setPrefWidth(560);

        build();

        uploadButton = getDialogPane().lookupButton(uploadType);
        uploadButton.setDisable(true);
        uploadButton.addEventFilter(javafx.event.ActionEvent.ACTION, ev -> {
            ev.consume();
            if (selectedCategory() != null) doUpload();
        });
    }

    private void build() {
        VBox fileBox = new VBox(2,
                UiKit.label(file.getName(), "section-title"),
                UiKit.label(humanSize(file.length()), "muted"));
        fileBox.getStyleClass().add("card");

        categoryTree.setPrefHeight(300);
        categoryTree.setShowRoot(false);
        categoryTree.setCellFactory(v -> new TreeCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                // 层级数字 + 名称；缩进由 TreeView 自身提供
                setText(empty || item == null ? null : depthHint(getTreeItem()) + item.getName());
            }
        });
        categoryTree.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            boolean none = sel == null;
            if (uploadButton != null) uploadButton.setDisable(none);
            status.setText(none ? "请选择存放分类" : "将存入：" + sel.getValue().getName());
        });
        rebuildTree();

        tagField.setPromptText("可选：标签，多个用逗号分隔，例如：四级,求职");

        progress.setPrefWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);

        HBox tagRow = new HBox(8, new Label("标签"), tagField);
        HBox.setHgrow(tagField, Priority.ALWAYS);
        tagRow.setAlignment(Pos.CENTER_LEFT);

        VBox box = new VBox(10,
                fileBox,
                UiKit.label("存放分类（顶层为 0，每下一层 +1）", "card-title"),
                categoryTree,
                tagRow,
                progress,
                status);
        box.setPadding(new javafx.geometry.Insets(4, 4, 4, 4));
        getDialogPane().setContent(box);
    }

    /** 按档案页的方式重建分类树，并全部展开。 */
    private void rebuildTree() {
        List<Category> flat = ctx.categoryService().listAll();
        Map<String, TreeItem<Category>> nodes = new LinkedHashMap<>();
        for (Category c : flat) {
            nodes.put(c.getId(), new TreeItem<>(c));
        }

        TreeItem<Category> root = new TreeItem<>();      // 隐藏的虚拟根，不参与选择
        for (Category c : flat) {
            TreeItem<Category> node = nodes.get(c.getId());
            TreeItem<Category> parent = c.isRoot() ? root : nodes.get(c.getParentId());
            // 父节点缺失（数据异常）时挂到根上，保证任何分类都可见
            (parent == null ? root : parent).getChildren().add(node);
        }
        expandAll(root);
        categoryTree.setRoot(root);
    }

    private static void expandAll(TreeItem<Category> node) {
        node.setExpanded(true);
        for (TreeItem<Category> child : node.getChildren()) expandAll(child);
    }

    /** 计算层级：顶层为 0，其子节点为 1，依此类推（虚拟根的父节点为 null）。 */
    private static String depthHint(TreeItem<Category> item) {
        int depth = 0;
        for (TreeItem<Category> p = item.getParent(); p != null && p.getParent() != null; p = p.getParent()) {
            depth++;
        }
        return depth + "  ";
    }

    private Category selectedCategory() {
        TreeItem<Category> item = categoryTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private void doUpload() {
        Category cat = selectedCategory();
        if (cat == null) return;

        UploadOptions opt = new UploadOptions()
                .setSuggestCategory(false)                 // 已显式指定分类，不再走归类建议
                .setCategoryId(cat.getId())
                .setTags(tagField.getText().isBlank() ? null : tagField.getText().trim());

        uploadButton.setDisable(true);
        progress.setVisible(true);
        progress.setManaged(true);
        progress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        status.setText("正在上传…");

        Thread worker = new Thread(() -> {
            Result<UploadSummary> r = ctx.materialService().upload(List.of(file), opt);
            Platform.runLater(() -> {
                progress.setProgress(1);
                status.setText("上传结束");
                if (!r.isSuccess()) {
                    UiKit.error("[" + r.getCode() + "] " + r.getMessage());
                    uploadButton.setDisable(false);
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

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }
}
