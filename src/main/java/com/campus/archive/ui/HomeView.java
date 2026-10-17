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
        upload.setOnAction(e -> chooseFile());

        VBox dropArea = new VBox(6,
                UiKit.label("把文件拖到这里", "drop-hint"),
                UiKit.label("单次一个文件，不超过 200 MB", "muted"));
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

    /** 点按钮：直接进文件选择器，选完再打开分类选择窗口。 */
    private void chooseFile() {
        FileChooser fc = new FileChooser();
        fc.setTitle("选择要归档的材料");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("常见材料格式",
                        "*.pdf", "*.doc", "*.docx", "*.png", "*.jpg", "*.jpeg", "*.xls", "*.xlsx"),
                new FileChooser.ExtensionFilter("全部文件", "*.*"));
        File picked = fc.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (picked != null) openDialog(picked);
    }

    private void openDialog(File file) {
        if (file.isDirectory()) {
            UiKit.warn("请选择文件，而不是文件夹");
            return;
        }
        if (file.length() > MAX_FILE_BYTES) {
            UiKit.warn("「" + file.getName() + "」超过 200 MB，已取消");
            return;
        }
        new UploadDialog(ctx, file).showAndWait();
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

    /** 单文件模式：多拖了只取第一个并提示，目录直接过滤。 */
    private boolean openWithDropped(List<File> dropped) {
        List<File> usable = dropped.stream().filter(f -> !f.isDirectory()).toList();
        if (usable.isEmpty()) {
            UiKit.warn("请拖入文件，而不是文件夹");
            return false;
        }
        if (usable.size() > 1) {
            UiKit.warn("一次只能上传一个文件，将使用第一个：\n" + usable.get(0).getName());
        }
        openDialog(usable.get(0));
        return true;
    }
}
