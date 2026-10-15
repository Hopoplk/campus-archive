package com.campus.archive.pattern.export;

import com.campus.archive.dto.ExportItem;

import java.io.IOException;

/**
 * 材料内容读取器。普通材料直接读磁盘，私密材料在这里完成解密，
 * 于是模板类不需要知道自己打包的到底是明文还是密文。
 */
@FunctionalInterface
public interface MaterialContentReader {

    /**
     * @param item 待读取条目
     * @return 材料明文字节
     * @throws IOException 文件不存在或不可读
     * @throws IllegalStateException 密文校验失败（由调用方转为 E203）
     */
    byte[] read(ExportItem item) throws IOException;
}
