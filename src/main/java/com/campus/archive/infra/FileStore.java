package com.campus.archive.infra;

import com.campus.archive.entity.FileType;

import java.io.IOException;import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.UUID;

/**
 * 物理文件存储（详细设计 4.3 节 FileStore）。
 * 只负责文件读写；磁盘上的物理文件名一律用 UUID，用户看到的文件名只存在于数据库。
 */
public class FileStore {

    private final Path filesRoot;
    private final Path vaultRoot;

    public FileStore(Path filesRoot, Path vaultRoot) {
        this.filesRoot = filesRoot;
        this.vaultRoot = vaultRoot;
    }

    public Path filesRoot() { return filesRoot; }
    public Path vaultRoot() { return vaultRoot; }

    /** 保存原始文件，返回相对 files/ 的路径，形如 2026/10/{uuid}.pdf。 */
    public String save(InputStream in, String originalFileName) throws IOException {
        LocalDate today = LocalDate.now();
        String sub = String.format("%04d/%02d", today.getYear(), today.getMonthValue());
        Path dir = filesRoot.resolve(sub);
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "." + extensionOf(originalFileName);
        Path target = dir.resolve(name);
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        return sub + "/" + name;
    }

    /** 把解密后的明文写回原路径（取消私密保护时使用）。 */
    public void restorePlaintext(String storedPath, byte[] plain) throws IOException {
        Path target = resolve(storedPath);
        Files.createDirectories(target.getParent());
        Files.write(target, plain);
    }

    public Path resolve(String storedPath) {
        return filesRoot.resolve(storedPath).normalize();
    }

    public boolean exists(String storedPath) {
        return storedPath != null && Files.exists(resolve(storedPath));
    }

    public void delete(String storedPath) throws IOException {
        if (storedPath != null) Files.deleteIfExists(resolve(storedPath));
    }

    public long size(String storedPath) throws IOException {
        return Files.size(resolve(storedPath));
    }

    /** 密文写入 vault/，返回相对 vault/ 的路径。 */
    public String saveEncrypted(String materialId, byte[] cipherText) throws IOException {
        Files.createDirectories(vaultRoot);
        String name = materialId + ".enc";
        Files.write(vaultRoot.resolve(name), cipherText);
        return name;
    }

    public byte[] readEncrypted(String encryptedPath) throws IOException {
        return Files.readAllBytes(vaultRoot.resolve(encryptedPath));
    }

    public void deleteEncrypted(String encryptedPath) throws IOException {
        if (encryptedPath != null) Files.deleteIfExists(vaultRoot.resolve(encryptedPath));
    }

    /** 私密材料解密后写入临时文件供界面预览，调用方负责在使用后删除。 */
    public Path writeTemp(byte[] plain, String suffix) throws IOException {
        Path tmp = Files.createTempFile("archive-preview-", suffix);
        Files.write(tmp, plain);
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    /** 从原始文件名提取安全的扩展名；非法或缺失时退化为 bin。 */
    static String extensionOf(String fileName) {
        if (fileName == null) return "bin";
        int dot = fileName.lastIndexOf('.');
        String ext = dot >= 0 ? fileName.substring(dot + 1) : "";
        ext = ext.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
        return ext.isEmpty() || ext.length() > 8 ? "bin" : ext;
    }
}
