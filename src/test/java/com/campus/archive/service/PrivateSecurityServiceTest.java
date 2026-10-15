package com.campus.archive.service;

import com.campus.archive.TestEnv;
import com.campus.archive.config.AppContext;
import com.campus.archive.dto.MaterialQuery;
import com.campus.archive.dto.Result;
import com.campus.archive.dto.UploadOptions;
import com.campus.archive.entity.Material;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 私密空间与安全密钥：设置、校验、加密、解密、取消保护，以及各种失败路径。
 *
 * <p>测试夹具在 @BeforeAll 里一次性把"已启用密钥 + 已有两份私密材料"准备好，
 * 每个用例只验证一个行为，不依赖用例之间的执行顺序。</p>
 */
@DisplayName("服务层：私密空间与安全密钥")
class PrivateSecurityServiceTest {

    private static final String KEY = "myKey123456";
    private static final String PLAINTEXT = "这是一份不应以明文出现在磁盘上的成绩单内容";

    private static AppContext ctx;
    private static Path staging;
    private static Material materialA;   // 保持私密，供读取类用例使用
    private static Material materialB;   // 供"取消私密保护"用例使用

    @BeforeAll
    static void setUp() throws Exception {
        ctx = TestEnv.newContext("security");
        staging = Files.createTempDirectory("archive-security-src-");

        assertTrue(ctx.securityService().initKey(KEY.toCharArray()).isSuccess());

        File a = TestEnv.sample(staging, "国家励志奖学金证书.pdf", PLAINTEXT);
        File b = TestEnv.sample(staging, "私密成绩单.pdf", "成绩单明文内容");
        File c = TestEnv.sample(staging, "普通话等级证书.pdf", "普通话证书明文");

        ctx.materialService().upload(List.of(a, b, c), new UploadOptions());
        materialA = find("国家励志");
        materialB = find("私密成绩单");

        assertTrue(ctx.securityService().markPrivate(materialA.getId()).isSuccess());
        assertTrue(ctx.securityService().markPrivate(materialB.getId()).isSuccess());
    }

    @AfterAll
    static void tearDown() {
        TestEnv.deleteRecursively(staging);
    }

    @BeforeEach
    void ensureUnlocked() {
        if (ctx.securityService().isEnabled() && !ctx.securityService().isUnlocked()) {
            ctx.securityService().unlock(KEY.toCharArray());
        }
    }

    private static Material find(String keyword) {
        var page = ctx.searchService().search(new MaterialQuery().setKeyword(keyword), 1, 5);
        assertFalse(page.items().isEmpty(), "测试夹具应能找到材料：" + keyword);
        return page.items().get(0);
    }

    @Test
    @DisplayName("A. 密钥长度不足时拒绝设置")
    void shortKeyRejected() {
        Result<Void> r = ctx.securityService().initKey("123".toCharArray());
        assertFalse(r.isSuccess());
        assertEquals("E200", r.getCode());
        assertTrue(r.getMessage().contains("6 位"));
    }

    @Test
    @DisplayName("B. 数据库里只有盐与校验值，绝无明文密钥")
    void initKeyStoresOnlySaltAndHash() {
        assertTrue(ctx.securityService().isEnabled());

        var setting = ctx.repos().query(d -> d.security().findSetting("security"));
        assertNotNull(setting);
        assertEquals(32, setting.getSalt().length(), "16 字节盐的十六进制应为 32 字符");
        assertEquals(64, setting.getKeyHash().length(), "SHA-256 应为 64 字符十六进制");
        assertFalse(setting.getKeyHash().contains(KEY), "绝不能出现明文密钥");
        assertTrue(setting.getKdfRounds() >= 100_000);
    }

    @Test
    @DisplayName("C. 锁定后内存中的派生密钥被清零，敏感操作一律被拒")
    void lockClearsSessionKey() {
        ctx.securityService().lock();
        assertFalse(ctx.securityService().isUnlocked());
        assertEquals("E202", ctx.securityService().markPrivate(materialA.getId()).getCode());
        assertEquals("E202", ctx.securityService().readVault(materialA.getId()).getCode());
    }

    @Test
    @DisplayName("D. 错误密钥只返回通用失败提示（对应 AC-05）")
    void wrongKeyGivesGenericFailure() {
        ctx.securityService().lock();

        Result<Void> r = ctx.securityService().unlock("wrong-key".toCharArray());
        assertFalse(r.isSuccess());
        assertEquals("E201", r.getCode());
        assertEquals("验证失败，请重新输入", r.getMessage());
        assertEquals(1, ctx.securityService().failCount());
        assertFalse(ctx.securityService().isUnlocked());
    }

    @Test
    @DisplayName("E. 正确密钥解锁成功，失败计数归零")
    void correctKeyUnlocks() {
        ctx.securityService().lock();
        ctx.securityService().unlock("wrong-key".toCharArray());
        assertEquals(1, ctx.securityService().failCount());

        assertTrue(ctx.securityService().unlock(KEY.toCharArray()).isSuccess());
        assertTrue(ctx.securityService().isUnlocked());
        assertEquals(0, ctx.securityService().failCount());
    }

    @Test
    @DisplayName("F. 设为私密：明文消失、密文落盘、元数据标记为私密")
    void markPrivateEncryptsAndRemovesPlaintext() throws Exception {
        Material c = find("普通话等级");
        Path plainPath = ctx.fileStore().resolve(c.getStoredPath());
        assertTrue(Files.exists(plainPath), "设置为私密之前明文应存在");

        assertTrue(ctx.securityService().markPrivate(c.getId()).isSuccess());

        assertTrue(ctx.materialService().findById(c.getId()).getIsPrivate());
        assertFalse(Files.exists(plainPath), "明文副本必须被删除");

        var vault = ctx.repos().query(d -> d.security().findVaultByMaterial(c.getId()));
        assertNotNull(vault);
        assertEquals(24, vault.getIv().length(), "12 字节 IV 的十六进制应为 24 字符");

        byte[] cipher = ctx.fileStore().readEncrypted(vault.getEncryptedPath());
        assertFalse(new String(cipher, java.nio.charset.StandardCharsets.UTF_8).contains("普通话证书明文"),
                "密文中不应出现明文片段");
    }

    @Test
    @DisplayName("G. 锁定状态下读取私密内容被拒绝（对应 AC-04）")
    void readingPrivateWhileLockedIsDenied() {
        ctx.securityService().lock();

        Result<byte[]> r = ctx.securityService().readVault(materialA.getId());
        assertFalse(r.isSuccess());
        assertEquals("E202", r.getCode());

        Result<Path> materialize = ctx.materialService().materialize(materialA.getId());
        assertFalse(materialize.isSuccess());
        assertEquals("E202", materialize.getCode(), "未解锁时不得取出解密文件");
    }

    @Test
    @DisplayName("H. 解锁后读取私密内容，明文与原始内容一致")
    void readingPrivateAfterUnlockReturnsOriginal() {
        assertTrue(ctx.securityService().isUnlocked());

        Result<byte[]> r = ctx.securityService().readVault(materialA.getId());
        assertTrue(r.isSuccess());
        assertEquals(PLAINTEXT, new String(r.getData(), java.nio.charset.StandardCharsets.UTF_8));

        // 通过材料服务取出的临时文件也应可读
        Result<Path> materialized = ctx.materialService().materialize(materialA.getId());
        assertTrue(materialized.isSuccess());
        try {
            assertEquals(PLAINTEXT, Files.readString(materialized.getData()));
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    @DisplayName("I. 取消私密保护后明文写回原路径，私密记录被清理")
    void unmarkPrivateRestoresPlaintext() throws Exception {
        assertTrue(ctx.securityService().unmarkPrivate(materialB.getId()).isSuccess());

        Material after = ctx.materialService().findById(materialB.getId());
        assertFalse(after.getIsPrivate());
        Path plain = ctx.fileStore().resolve(after.getStoredPath());
        assertTrue(Files.exists(plain));
        assertEquals("成绩单明文内容", Files.readString(plain));
        assertNull(ctx.repos().query(d -> d.security().findVaultByMaterial(materialB.getId())));
    }
}
