package com.campus.archive.pattern.suggest;

import com.campus.archive.entity.FileType;

/**
 * 归类建议策略的输入。
 * 单独抽出 FileMeta 而不是直接传实体，是为了让策略与持久化层完全解耦——
 * 将来接入 OCR 时，只需往 textContent 里塞识别文本，其余代码一行都不用改。
 *
 * @param fileName    原始文件名（含扩展名）
 * @param fileType    文件类型
 * @param fileSize    字节数
 * @param textContent 可选的正文/OCR 文本，为 null 表示尚未解析
 */
public record FileMeta(String fileName, FileType fileType, long fileSize, String textContent) {

    public static FileMeta of(String fileName, FileType fileType, long fileSize) {
        return new FileMeta(fileName, fileType, fileSize, null);
    }

    public String lowerName() {
        return fileName == null ? "" : fileName.toLowerCase();
    }

    public String baseName() {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }
}
