package com.campus.archive.dto;

import java.nio.file.Path;
import java.util.List;

/**
 * 导出结果。
 *
 * @param packagePath 生成的压缩包路径
 * @param fileCount   实际写入的文件数
 * @param skipped     因失效而跳过的条目
 * @param checksum    压缩包的 SHA-256 校验和，用于导入时校验完整性
 * @param bytes       压缩包字节数
 */
public record ExportResult(Path packagePath, int fileCount, List<String> skipped, String checksum, long bytes) {

    public String summaryText() {
        StringBuilder sb = new StringBuilder();
        sb.append("导出完成：").append(fileCount).append(" 个文件");
        if (skipped != null && !skipped.isEmpty()) sb.append("，跳过 ").append(skipped.size()).append(" 个失效条目");
        sb.append("\n保存位置：").append(packagePath);
        sb.append("\n校验码：").append(checksum == null ? "-" : checksum.substring(0, Math.min(16, checksum.length())));
        return sb.toString();
    }
}
