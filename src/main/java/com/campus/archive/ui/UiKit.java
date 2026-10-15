package com.campus.archive.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.Region;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/** 界面通用构件。统一交互反馈与文案风格（对应需求 NFR-UI-05 / NFR-UI-06 / NFR-UI-07）。 */
public final class UiKit {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private UiKit() {
    }

    public static String fmt(LocalDateTime t) {
        return t == null ? "—" : t.format(TIME);
    }

    public static String fmtDate(LocalDateTime t) {
        return t == null ? "—" : t.format(DATE);
    }

    public static void info(String message) {
        alert(Alert.AlertType.INFORMATION, "提示", message);
    }

    public static void warn(String message) {
        alert(Alert.AlertType.WARNING, "注意", message);
    }

    public static void error(String message) {
        alert(Alert.AlertType.ERROR, "操作失败", message);
    }

    private static void alert(Alert.AlertType type, String title, String message) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.getDialogPane().setContent(wrap(message));
        a.showAndWait();
    }

    /** 高风险操作的二次确认（对应 NFR-UI-08 / AC-09）。 */
    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.getDialogPane().setContent(wrap(message));
        a.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    public static Optional<String> prompt(String title, String label, String initial) {
        TextInputDialog d = new TextInputDialog(initial == null ? "" : initial);
        d.setTitle(title);
        d.setHeaderText(null);
        d.setContentText(label);
        return d.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    public static Label label(String text, String styleClass) {
        Label l = new Label(text);
        if (styleClass != null) l.getStyleClass().add(styleClass);
        return l;
    }

    private static Region wrap(String message) {
        Label l = new Label(message);
        l.setWrapText(true);
        l.setMaxWidth(460);
        return l;
    }
}
