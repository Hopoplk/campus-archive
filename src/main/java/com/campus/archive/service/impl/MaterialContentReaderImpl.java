package com.campus.archive.service.impl;

import com.campus.archive.dto.ExportItem;
import com.campus.archive.infra.FileStore;
import com.campus.archive.pattern.export.MaterialContentReader;
import com.campus.archive.service.PrivateSecurityService;

import java.io.IOException;
import java.nio.file.Files;

/**
 * 材料内容读取器的实现：普通材料直接读磁盘，私密材料委托安全服务解密。
 *
 * <p>正因为有了这层间接，模板方法模式下的导出器完全不需要知道
 * "我打包的这份文件到底是明文还是密文"。</p>
 */
public class MaterialContentReaderImpl implements MaterialContentReader {

    private final FileStore fileStore;
    private final PrivateSecurityService security;

    public MaterialContentReaderImpl(FileStore fileStore, PrivateSecurityService security) {
        this.fileStore = fileStore;
        this.security = security;
    }

    @Override
    public byte[] read(ExportItem item) throws IOException {
        if (!item.isPrivate()) {
            return Files.readAllBytes(fileStore.resolve(item.storedPath()));
        }
        var result = security.readVault(item.materialId());
        if (!result.isSuccess()) {
            throw new IOException(result.getCode() + " " + result.getMessage());
        }
        return result.getData();
    }
}
