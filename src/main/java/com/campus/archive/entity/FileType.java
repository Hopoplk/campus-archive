package com.campus.archive.entity;

/** 材料文件类型。与 material.file_type 列的枚举名一一对应。 */
public enum FileType {
    PDF, WORD, IMAGE, EXCEL, OTHER;

    public static FileType of(String fileName) {
        String n = fileName == null ? "" : fileName.toLowerCase();
        int dot = n.lastIndexOf('.');
        String ext = dot >= 0 ? n.substring(dot + 1) : "";
        return switch (ext) {
            case "pdf" -> PDF;
            case "doc", "docx", "wps", "rtf" -> WORD;
            case "xls", "xlsx", "csv" -> EXCEL;
            case "png", "jpg", "jpeg", "gif", "bmp", "webp" -> IMAGE;
            default -> OTHER;
        };
    }

    public boolean isPreviewable() {
        return this == PDF || this == IMAGE;
    }
}
