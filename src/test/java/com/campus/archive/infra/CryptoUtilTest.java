package com.campus.archive.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** 加密工具与 JSON 工具的单元测试。 */
@DisplayName("基础设施：加密与备份清单")
class CryptoUtilTest {

    @Test
    @DisplayName("同一密钥与盐派生结果稳定，不同盐派生结果不同")
    void keyDerivationIsDeterministic() {
        char[] key = "my-secret-key".toCharArray();
        byte[] salt1 = CryptoUtil.randomBytes(16);
        byte[] salt2 = CryptoUtil.randomBytes(16);

        byte[] a = CryptoUtil.deriveKey(key, salt1, 1000);
        byte[] b = CryptoUtil.deriveKey(key, salt1, 1000);
        byte[] c = CryptoUtil.deriveKey(key, salt2, 1000);

        assertEquals(32, a.length, "应派生 256 位密钥");
        assertArrayEquals(a, b);
        assertFalse(java.util.Arrays.equals(a, c), "换盐必须得到不同密钥，否则彩虹表会奏效");
    }

    @Test
    @DisplayName("AES-256-GCM 加解密往返一致，且每次密文不同（IV 随机）")
    void encryptDecryptRoundTrip() {
        byte[] key = CryptoUtil.deriveKey("k".toCharArray(), CryptoUtil.randomBytes(16), 1000);
        byte[] plain = "大学生的证书材料内容".getBytes(java.nio.charset.StandardCharsets.UTF_8);

        byte[] iv1 = CryptoUtil.randomBytes(12);
        byte[] iv2 = CryptoUtil.randomBytes(12);
        byte[] c1 = CryptoUtil.encrypt(plain, key, iv1);
        byte[] c2 = CryptoUtil.encrypt(plain, key, iv2);

        assertArrayEquals(plain, CryptoUtil.decrypt(c1, key, iv1));
        assertFalse(java.util.Arrays.equals(c1, c2), "相同明文在不同 IV 下必须产生不同密文");
        assertNotEquals(plain.length, c1.length, "GCM 会附加认证标签");
    }

    @Test
    @DisplayName("密文被篡改时必须解密失败，而不是悄悄返回错误内容")
    void tamperedCipherIsDetected() {
        byte[] key = CryptoUtil.deriveKey("k".toCharArray(), CryptoUtil.randomBytes(16), 1000);
        byte[] iv = CryptoUtil.randomBytes(12);
        byte[] cipher = CryptoUtil.encrypt("原始内容".getBytes(), key, iv);

        cipher[3] ^= 0x5A;   // 翻转一个比特
        assertThrows(IllegalStateException.class, () -> CryptoUtil.decrypt(cipher, key, iv));
    }

    @Test
    @DisplayName("文件摘要用于重复检测，相同内容摘要相同、不同内容摘要不同")
    void fileDigestDetectsDuplicates() throws IOException {
        Path dir = Files.createTempDirectory("archive-hash-");
        Path a = dir.resolve("a.txt");
        Path b = dir.resolve("b.txt");
        Path c = dir.resolve("c.txt");
        Files.writeString(a, "相同的内容");
        Files.writeString(b, "相同的内容");
        Files.writeString(c, "不同的内容");

        assertEquals(CryptoUtil.sha256Hex(a), CryptoUtil.sha256Hex(b));
        assertNotEquals(CryptoUtil.sha256Hex(a), CryptoUtil.sha256Hex(c));
        assertEquals(64, CryptoUtil.sha256Hex(a).length());
    }

    @Test
    @DisplayName("密钥用后清零")
    void wipeClearsSecret() {
        char[] secret = "abcdef".toCharArray();
        CryptoUtil.wipe(secret);
        assertArrayEquals(new char[]{0, 0, 0, 0, 0, 0}, secret);
    }

    @Test
    @DisplayName("备份清单 JSON 能正确转义并原样读回（含中文、引号与反斜杠）")
    void miniJsonRoundTrip() {
        var original = new java.util.LinkedHashMap<String, Object>();
        original.put("name", "含\"引号\"的文件\\路径.pdf");
        original.put("size", 1234L);
        original.put("private", false);
        original.put("tags", java.util.List.of("四级", "求职"));

        String json = MiniJson.write(original);
        var parsed = MiniJson.parseObject(json);

        assertEquals("含\"引号\"的文件\\路径.pdf", parsed.get("name"));
        assertEquals(1234L, parsed.get("size"));
        assertEquals(Boolean.FALSE, parsed.get("private"));
        assertEquals(java.util.List.of("四级", "求职"), parsed.get("tags"));
    }
}
