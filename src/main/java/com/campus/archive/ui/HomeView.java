package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 首页：材料上传入口。
 *
 * <p>两条入口最终汇到同一处：点按钮直接弹出文件选择器，把文件拖到虚线区域则取第一个文件；
 * 选定后打开 {@link UploadDialog} 选择存放分类。</p>
 */
public class HomeView extends VBox {

    private static final String DROP_ACTIVE = "drop-active";
    private static final long MAX_FILE_BYTES = 200L * 1024 * 1024;
    private static final int MAX_FILES = 200;

    private final AppContext ctx;

    public HomeView(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("content");
        setSpacing(16);
        build();
    }

    private void build() {
        Button upload = new Button("＋  上传材料");
        upload.getStyleClass().addAll("button", "primary-button");
        upload.setStyle("-fx-font-size: 15px; -fx-padding: 12 26 12 26;");
        upload.setOnAction(e -> chooseFiles());

        VBox dropArea = new VBox(6,
                UiKit.label("把文件拖到这里", "drop-hint"),
                UiKit.label("可一次拖入多个文件，它们会存入同一个分类，单个不超过 200 MB", "muted"));
        dropArea.setAlignment(Pos.CENTER);
        dropArea.getStyleClass().add("drop-area");
        VBox.setVgrow(dropArea, Priority.ALWAYS);

        HBox actions = new HBox(12, upload);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(14, dropArea, actions);
        card.getStyleClass().addAll("card", "drop-card");
        card.setMinHeight(200);
        card.setPrefHeight(200);
        enableDrop(card);

        getChildren().add(card);
    }

    /** 点按钮：直接进文件选择器（可多选），选完再打开分类选择窗口。 */
    private void chooseFiles() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择要归档的材料（可多选）");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("常见材料格式",
                        "*.pdf", "*.doc", "*.docx", "*.png", "*.jpg", "*.jpeg", "*.xls", "*.xlsx"),
                new FileChooser.ExtensionFilter("全部文件", "*.*"));
        List<File> picked = fc.showOpenMultipleDialog(getScene() == null ? null : getScene().getWindow());
        if (picked != null && !picked.isEmpty()) openDialog(picked);
    }

    /** 过滤目录与超限文件；剩下的作为一批一起进同一个分类。 */
    private void openDialog(List<File> picked) {
        List<File> usable = new ArrayList<>();
        int oversized = 0;
        for (File f : picked) {
            if (f.isDirectory()) continue;
            if (f.length() > MAX_FILE_BYTES) { oversized++; continue; }
            if (usable.size() >= MAX_FILES) break;
            usable.add(f);
        }
        if (usable.isEmpty()) {
            UiKit.warn(oversized > 0 ? "所选文件均超过 200 MB，已取消" : "请选择文件，而不是文件夹");
            return;
        }
        if (oversized > 0) {
            UiKit.warn("已跳过 " + oversized + " 个超过 200 MB 的文件");
        }
        new UploadDialog(ctx, usable).showAndWait();
    }

    /** 整张卡片都是投放热区，拖入时高亮虚线框。 */
    private void enableDrop(VBox card) {
        card.setOnDragOver(e -> {
            if (e.getDragboard().hasFiles()) e.acceptTransferModes(TransferMode.COPY);
            e.consume();
        });
        card.setOnDragEntered(e -> {
            if (e.getDragboard().hasFiles() && !card.getStyleClass().contains(DROP_ACTIVE)) {
                card.getStyleClass().add(DROP_ACTIVE);
            }
            e.consume();
        });
        card.setOnDragExited(e -> {
            card.getStyleClass().remove(DROP_ACTIVE);
            e.consume();
        });
        card.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean done = false;
            if (db.hasFiles()) {
                done = openWithDropped(db.getFiles());
            }
            e.setDropCompleted(done);
            card.getStyleClass().remove(DROP_ACTIVE);
            e.consume();
        });
    }

    /** 拖入的多个文件作为一批，一起存入同一个分类。 */
    private boolean openWithDropped(List<File> dropped) {
        if (dropped.stream().noneMatch(f -> !f.isDirectory())) {
            UiKit.warn("请拖入文件，而不是文件夹");
            return false;
        }
        openDialog(dropped);
        return true;
    }
}
