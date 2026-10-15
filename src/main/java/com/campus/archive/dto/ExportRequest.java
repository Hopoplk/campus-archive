package com.campus.archive.dto;

import java.nio.file.Path;
import java.util.List;

/**
 * 导出请求。同一个请求对象同时服务于"选定材料导出"与"全量备份导出"，
 * 两种导出由模板方法模式（pattern.export 包）下的不同子类处理。
 */
public class ExportRequest {

    private List<String> materialIds;   // 选定材料导出时必填；为空表示全量备份
    private Path targetDir;             // 导出目标目录
    private boolean includePrivate;     // 是否包含私密材料
    private String label = "导出";       // 输出包名前缀

    /** 导出用户选中的若干份材料。 */
    public static ExportRequest ofSelection(List<String> materialIds, Path targetDir) {
        ExportRequest r = new ExportRequest();
        r.materialIds = List.copyOf(materialIds);
        r.targetDir = targetDir;
        r.includePrivate = true;
        r.label = "材料导出";
        return r;
    }

    public static ExportRequest ofFullBackup(Path targetDir, boolean includePrivate) {
        ExportRequest r = new ExportRequest();
        r.targetDir = targetDir;
        r.includePrivate = includePrivate;
        r.label = "档案备份";
        return r;
    }

    public boolean isFullBackup() {
        return materialIds == null;
    }

    public List<String> getMaterialIds() { return materialIds; }
    public void setMaterialIds(List<String> materialIds) { this.materialIds = materialIds; }

    public Path getTargetDir() { return targetDir; }
    public void setTargetDir(Path targetDir) { this.targetDir = targetDir; }

    public boolean isIncludePrivate() { return includePrivate; }
    public void setIncludePrivate(boolean includePrivate) { this.includePrivate = includePrivate; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
