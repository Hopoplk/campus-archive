package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.layout.*;

import java.util.Map;

/**
 * 首页：只放"现在要做什么"——上传入口、最近材料与关键指标。
 * 刻意不堆砌高级功能，对应需求 NFR-UI-09 低信息负担。
 */
public class HomeView extends VBox implements MainWindow.Refreshable {

    private final AppContext ctx;
    private final ListView<String> recentList = new ListView<>();
    private final Label totalValue = UiKit.label("0", "metric-value");
    private final Label privateValue = UiKit.label("0", "metric-value");
    private final Label trashedValue = UiKit.label("0", "metric-value");
    private final Label strategyHint = UiKit.label("", "pattern-badge");

    public HomeView(AppContext ctx) {
        this.ctx = ctx;
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

        HBox actions = new HBox(12, upload);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox hero = new VBox(10,
                UiKit.label("把成绩、证书和综测材料集中管理起来", "section-title"),
                UiKit.label("本地优先 · 无需登录 · 私密材料加密存放", "muted"),
                actions);
        hero.getStyleClass().add("card");

        // ---- 指标卡 ----
        HBox metrics = new HBox(14,
                metric("材料总数", totalValue),
                metric("私密材料", privateValue),
                metric("回收站", trashedValue));
        for (var n : metrics.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        // ---- 最近添加 ----
        recentList.setPlaceholder(UiKit.label("还没有材料，点上方「上传材料」开始", "muted"));
        VBox recent = new VBox(8, UiKit.label("最近添加", "card-title"), recentList);
        recent.getStyleClass().add("card");
        VBox.setVgrow(recentList, Priority.ALWAYS);
        VBox.setVgrow(recent, Priority.ALWAYS);

        // ---- 当前策略提示：让评审一眼看到模式 ① 在用 ----
        VBox patternCard = new VBox(6,
                UiKit.label("设计模式应用情况", "card-title"),
                strategyHint,
                UiKit.label("本原型在材料归类、导出备份两处分别引入了策略与模板方法两种模式，"
                        + "详见「我的」页与随附的《第 5 周设计模式应用说明》。", "muted"));
        patternCard.getStyleClass().add("card");

        getChildren().addAll(hero, metrics, recent, patternCard);
    }

    private VBox metric(String title, Label value) {
        VBox box = new VBox(4, UiKit.label(title, "muted"), value);
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    @Override
    public void refresh() {
        Map<String, Object> stats = ctx.searchService().stats();
        totalValue.setText(String.valueOf(stats.get("total")));
        privateValue.setText(String.valueOf(stats.get("privateCount")));
        trashedValue.setText(String.valueOf(stats.get("trashed")));
        strategyHint.setText("策略模式①：" + ctx.materialService().currentSuggestionStrategy());

        recentList.getItems().setAll(ctx.searchService().recent(8).stream()
                .map(m -> "· " + m.getFileName() + "　" + m.getCategoryName() + "　" + UiKit.fmtDate(m.getCreatedAt())
                        + (m.getIsPrivate() ? "　[私密]" : ""))
                .toList());
    }
}
