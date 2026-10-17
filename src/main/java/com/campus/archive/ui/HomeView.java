package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.File;
import java.util.List;

/**
 * 首页：材料上传入口。
 *
 * <p>两个入口共用同一条上传链路：点击按钮走文件选择器；
 * 把文件拖到虚线区域则把文件预填进同一个 {@link UploadDialog}，
 * 因此尺寸、数量、归类建议与冲突提示的规则完全一致。</p>
 */
public class HomeView extends VBox {

    private static final String DROP_ACTIVE = "drop-active";

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
        upload.setOnAction(e -> new UploadDialog(ctx).showAndWait());

        VBox dropArea = new VBox(6,
                UiKit.label("把文件拖到这里", "drop-hint"),
                UiKit.label("支持 PDF / Word / 图片 / 表格，单个不超过 200 MB", "muted"));
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

    /** 目录不是材料，先过滤掉；其余交给上传对话框统一做尺寸与数量校验。 */
    private boolean openWithDropped(List<File> dropped) {
        List<File> usable = dropped.stream().filter(f -> !f.isDirectory()).toList();
        if (usable.isEmpty()) {
            UiKit.warn("请拖入文件，而不是文件夹");
            return false;
        }
        new UploadDialog(ctx, usable).showAndWait();
        return true;
    }
}
