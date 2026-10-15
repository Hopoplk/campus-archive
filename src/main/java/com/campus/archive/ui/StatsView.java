package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.List;
import java.util.Map;

/** 统计页：只展示对用户有帮助的信息（材料数量、分类分布、私密与回收站情况）。 */
public class StatsView extends VBox implements MainWindow.Refreshable {

    private final AppContext ctx;
    private final Label total = UiKit.label("0", "metric-value");
    private final Label priv = UiKit.label("0", "metric-value");
    private final Label pending = UiKit.label("0", "metric-value");
    private final Label trashed = UiKit.label("0", "metric-value");
    private final BarChart<String, Number> chart = new BarChart<>(new CategoryAxis(), new NumberAxis());

    public StatsView(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("content");
        setSpacing(16);
        build();
        refresh();
    }

    private void build() {
        HBox metrics = new HBox(14,
                metric("材料总数", total),
                metric("私密材料", priv),
                metric("待办日程", pending),
                metric("回收站", trashed));
        for (var n : metrics.getChildren()) HBox.setHgrow(n, Priority.ALWAYS);

        chart.setTitle("各分类材料数量");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setPrefHeight(340);

        VBox chartCard = new VBox(8, chart);
        chartCard.getStyleClass().add("card");
        VBox.setVgrow(chart, Priority.ALWAYS);

        VBox hint = new VBox(6,
                UiKit.label("统计口径说明", "card-title"),
                UiKit.label("· 材料总数与分类分布只统计未删除的材料；回收站单独计数。", "muted"),
                UiKit.label("· 私密材料计入总数，但正文与缩略图在未解锁时不会出现在任何统计明细中。", "muted"),
                UiKit.label("· 本页不提供任何公开排行或学生间比较，符合《需求分析报告》对隐私的约束。", "muted"));
        hint.getStyleClass().add("card");

        getChildren().addAll(metrics, chartCard, hint);
        VBox.setVgrow(chartCard, Priority.ALWAYS);
    }

    private VBox metric(String title, Label value) {
        VBox box = new VBox(4, UiKit.label(title, "muted"), value);
        box.getStyleClass().add("card");
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void refresh() {
        Map<String, Object> stats = ctx.searchService().stats();
        total.setText(String.valueOf(stats.get("total")));
        priv.setText(String.valueOf(stats.get("privateCount")));
        pending.setText(String.valueOf(stats.get("pendingSchedules")));
        trashed.setText(String.valueOf(stats.get("trashed")));

        chart.getData().clear();
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Object rows = stats.get("byCategory");
        if (rows instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof Map<?, ?> m) {
                    String name = String.valueOf(m.get("name"));
                    Object cnt = m.get("cnt");
                    long v = cnt instanceof Number n ? n.longValue() : 0;
                    series.getData().add(new XYChart.Data<>(name, v));
                }
            }
        }
        chart.getData().add(series);
    }
}
