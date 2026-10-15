package com.campus.archive.dto;

/**
 * 导出一个待打包条目。
 *
 * @param materialId   材料 ID；为 null 表示这是一个由导出流程自己生成的虚拟条目（如备份清单）
 * @param displayName  用户可见的文件名，作为压缩包内的条目名
 * @param storedPath   相对 files/ 或 vault/ 的存储路径
 * @param size         字节数
 * @param isPrivate    是否为私密材料
 */
public record ExportItem(String materialId, String displayName, String storedPath, long size, boolean isPrivate) {

    /** 引用是否还有效（材料未被永久删除）。 */
    public boolean isValid() {
        return storedPath != null && !storedPath.isBlank();
    }

    public static ExportItem broken(String displayName) {
        return new ExportItem(null, displayName, null, 0, false);
    }
}
