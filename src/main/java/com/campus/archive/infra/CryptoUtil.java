package com.campus.archive.infra;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * 加密工具（详细设计 9.1 节）。
 * 无状态工具类，不持有任何密钥；明文密钥与派生密钥均不落盘。
 */
public final class CryptoUtil {

    public static final int KDF_ROUNDS = 100_000;
    public static final int SALT_BYTES = 16;
    public static final int IV_BYTES = 12;
    public static final int GCM_TAG_BITS = 128;
    public static final int KEY_BITS = 256;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private CryptoUtil() {
    }

    /** PBKDF2-HMAC-SHA256 派生 256 位密钥。故意"慢"，提高离线暴力破解成本。 */
    public static byte[] deriveKey(char[] rawKey, byte[] salt, int rounds) {
        try {
            PBEKeySpec spec = new PBEKeySpec(rawKey, salt, rounds, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("密钥派生失败", e);
        }
    }

    public static String sha256Hex(byte[] data) {
        try {
            return HEX.formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            throw new IllegalStateException("摘要计算失败", e);
        }
    }

    public static String sha256Hex(String text) {
        return sha256Hex(text.getBytes(StandardCharsets.UTF_8));
    }

    /** 流式计算文件摘要，避免把大文件读进内存。用于重复文件检测与导出校验。 */
    public static String sha256Hex(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buf = new byte[8192];
            try (InputStream in = Files.newInputStream(file)) {
                int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            return HEX.formatHex(md.digest());
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("文件摘要计算失败", e);
        }
    }

    public static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    public static String randomHex(int n) {
        return HEX.formatHex(randomBytes(n));
    }

    public static byte[] fromHex(String hex) {
        return HEX.parseHex(hex);
    }

    public static String toHex(byte[] bytes) {
        return HEX.formatHex(bytes);
    }

    /** AES-256-GCM 加密：同时提供保密性与完整性校验。 */
    public static byte[] encrypt(byte[] plain, byte[] key, byte[] iv) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(plain);
        } catch (Exception e) {
            throw new IllegalStateException("加密失败", e);
        }
    }

    /** AES-256-GCM 解密；密文被篡改时抛异常，由调用方转换为 E203 错误码。 */
    public static byte[] decrypt(byte[] cipherText, byte[] key, byte[] iv) {
        try {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return c.doFinal(cipherText);
        } catch (Exception e) {
            throw new IllegalStateException("DECRYPT_FAILED", e);
        }
    }

    /** 密钥使用后立即清零，避免 char[] / byte[] 长期驻留内存。 */
    public static void wipe(char[] secret) {
        if (secret != null) Arrays.fill(secret, '\0');
    }

    public static void wipe(byte[] secret) {
        if (secret != null) Arrays.fill(secret, (byte) 0);
    }
}
