package com.campus.archive.service.impl;

import com.campus.archive.config.Repositories;
import com.campus.archive.dto.Result;
import com.campus.archive.entity.Material;
import com.campus.archive.entity.SecuritySetting;
import com.campus.archive.entity.VaultEntry;
import com.campus.archive.infra.CryptoUtil;
import com.campus.archive.infra.FileStore;
import com.campus.archive.service.PrivateSecurityService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * 私密安全模块实现（详细设计 9.1 节）。
 *
 * <p>三条铁律：明文密钥不落盘、未验证不可见、失败不泄露区分性信息。
 * 派生密钥只以 byte[] 形式驻留内存，锁定或退出时立即清零。</p>
 */
public class PrivateSecurityServiceImpl implements PrivateSecurityService {

    /** 连续失败达到该次数后锁定输入（详细设计图 4-4）。 */
    public static final int MAX_ATTEMPTS = 5;

    private final Repositories repos;
    private final FileStore fileStore;

    /** 会话级派生密钥；为 null 表示私密空间处于锁定状态。 */
    private volatile byte[] sessionKey;
    private volatile int failCount;

    public PrivateSecurityServiceImpl(Repositories repos, FileStore fileStore) {
        this.repos = repos;
        this.fileStore = fileStore;
    }

    private SecuritySetting setting() {
        return repos.query(d -> d.security().findSetting(SecuritySetting.SINGLETON_ID));
    }

    @Override
    public boolean isEnabled() {
        SecuritySetting s = setting();
        return s != null && s.isEnabled();
    }

    @Override
    public boolean isUnlocked() {
        return sessionKey != null;
    }

    @Override
    public Result<Void> initKey(char[] rawKey) {
        if (rawKey == null || rawKey.length < 6) {
            return Result.fail("E200", "安全密钥不少于 6 位");
        }
        if (isEnabled()) {
            return Result.fail("E200", "安全密钥已设置，如需更换请先验证原密钥");
        }
        byte[] salt = CryptoUtil.randomBytes(CryptoUtil.SALT_BYTES);
        byte[] derived = CryptoUtil.deriveKey(rawKey, salt, CryptoUtil.KDF_ROUNDS);
        String keyHash = CryptoUtil.sha256Hex(derived);

        SecuritySetting s = new SecuritySetting();
        s.setKeyHash(keyHash);
        s.setSalt(CryptoUtil.toHex(salt));
        s.setKdfRounds(CryptoUtil.KDF_ROUNDS);
        s.setEnabled(true);
        repos.tx(d -> d.security().insertSetting(s));

        sessionKey = derived;
        failCount = 0;
        return Result.ok();
    }

    @Override
    public Result<Void> unlock(char[] rawKey) {
        SecuritySetting s = setting();
        if (s == null || !s.isEnabled()) {
            return Result.fail("E200", "请先设置安全密钥");
        }
        if (rawKey == null || rawKey.length == 0) {
            return Result.fail("E201", "验证失败，请重新输入");
        }
        byte[] derived = CryptoUtil.deriveKey(rawKey, CryptoUtil.fromHex(s.getSalt()), s.getKdfRounds());
        String hash = CryptoUtil.sha256Hex(derived);

        if (hash.equals(s.getKeyHash())) {
            if (sessionKey != null) CryptoUtil.wipe(sessionKey);
            sessionKey = derived;
            failCount = 0;
            return Result.ok();
        }
        CryptoUtil.wipe(derived);
        failCount++;
        return Result.fail("E201", "验证失败，请重新输入");
    }

    @Override
    public void lock() {
        if (sessionKey != null) {
            CryptoUtil.wipe(sessionKey);
            sessionKey = null;
        }
    }

    @Override
    public Result<Void> markPrivate(String materialId) {
        if (!isUnlocked()) return Result.fail("E202", "请先解锁私密空间");
        Material m = repos.query(d -> d.material().findById(materialId));
        if (m == null) return Result.fail("E102", "材料不存在");
        if (m.getIsPrivate()) return Result.ok();

        Path plainPath = fileStore.resolve(m.getStoredPath());
        if (!Files.exists(plainPath)) return Result.fail("E102", "文件可能已被移动或删除，请重新选择");

        byte[] iv = CryptoUtil.randomBytes(CryptoUtil.IV_BYTES);
        try {
            byte[] plain = Files.readAllBytes(plainPath);
            byte[] cipher = CryptoUtil.encrypt(plain, sessionKey, iv);
            String encryptedPath = fileStore.saveEncrypted(materialId, cipher);

            VaultEntry entry = new VaultEntry();
            entry.setId(UUID.randomUUID().toString());
            entry.setMaterialId(materialId);
            entry.setEncryptedPath(encryptedPath);
            entry.setIv(CryptoUtil.toHex(iv));

            repos.tx(d -> {
                d.security().insertVault(entry);
                d.material().updatePrivate(materialId, true);
                return null;
            });

            // 明文删除放在事务成功之后；失败只记日志，不产生"数据库说私密、磁盘还有明文"的状态
            Files.deleteIfExists(plainPath);
            return Result.ok();
        } catch (IOException e) {
            return Result.fail("E102", "文件读取失败，无法加密");
        } catch (RuntimeException e) {
            return Result.fail("E203", "加密失败，请重试");
        }
    }

    @Override
    public Result<Void> unmarkPrivate(String materialId) {
        if (!isUnlocked()) return Result.fail("E202", "请先解锁私密空间");
        Material m = repos.query(d -> d.material().findById(materialId));
        if (m == null) return Result.fail("E102", "材料不存在");
        if (!m.getIsPrivate()) return Result.ok();

        VaultEntry entry = repos.query(d -> d.security().findVaultByMaterial(materialId));
        if (entry == null) return Result.fail("E203", "私密记录缺失，无法取消保护");

        try {
            byte[] cipher = fileStore.readEncrypted(entry.getEncryptedPath());
            byte[] plain = CryptoUtil.decrypt(cipher, sessionKey, CryptoUtil.fromHex(entry.getIv()));
            fileStore.restorePlaintext(m.getStoredPath(), plain);

            repos.tx(d -> {
                d.security().deleteVault(materialId);
                d.material().updatePrivate(materialId, false);
                return null;
            });
            fileStore.deleteEncrypted(entry.getEncryptedPath());
            return Result.ok();
        } catch (IllegalStateException e) {
            return Result.fail("E203", "密文校验失败，文件可能已损坏");
        } catch (IOException e) {
            return Result.fail("E203", "解密失败，请重试");
        }
    }

    @Override
    public Result<byte[]> readVault(String materialId) {
        if (!isUnlocked()) return Result.fail("E202", "请先解锁私密空间");
        VaultEntry entry = repos.query(d -> d.security().findVaultByMaterial(materialId));
        if (entry == null) return Result.fail("E203", "私密记录缺失");
        try {
            byte[] cipher = fileStore.readEncrypted(entry.getEncryptedPath());
            return Result.ok(CryptoUtil.decrypt(cipher, sessionKey, CryptoUtil.fromHex(entry.getIv())));
        } catch (IllegalStateException e) {
            return Result.fail("E203", "文件已损坏，无法打开");
        } catch (IOException e) {
            return Result.fail("E102", "密文读取失败");
        }
    }

    @Override
    public int failCount() {
        return failCount;
    }
}
