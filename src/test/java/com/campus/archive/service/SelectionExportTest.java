package com.campus.archive.service;

import com.campus.archive.TestEnv;
import com.campus.archive.config.AppContext;
import com.campus.archive.dto.ExportResult;
import com.campus.archive.dto.MaterialQuery;
import com.campus.archive.dto.Result;
import com.campus.archive.dto.UploadOptions;
import com.campus.archive.entity.Material;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "我的档案"里的材料导出。
 *
 * <p>用户痛点很直接：材料在磁盘上是 UUID 命名的，想单独拿出来一份发给别人做不到。
 * 导出必须做到三件事——用<b>原始显示名</b>、内容<b>逐字节一致</b>、附清单可校验。</p>
 */
@DisplayName("材料导出：原始名 / 内容一致 / 私密保护")
class SelectionExportTest {

    private AppContext ctx;
    private Path staging;

    @BeforeEach
    void setUp() throws Exception {
        ctx = TestEnv.newContext("selection-export");
        staging = Files.createTempDirectory("archive-sel-src-");
    }

    @AfterEach
    void tearDown() {
        TestEnv.deleteRecursively(staging);
    }

    private UploadOptions opts() {
        return new UploadOptions();
    }

    private File file(String name, String content) {
        return TestEnv.sample(staging, name, content);
    }

    private List<Material> all() {
        return ctx.searchService().search(new MaterialQuery(), 1, 100).items();
    }

    private static String entry(Path zip, String name) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (name.equals(e.getName())) return new String(zis.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    @Test
    @DisplayName("A. 导出用原始显示名，内容与源文件逐字节一致")
    void exportKeepsOriginalNameAndContent() throws IOException {
        String body = "获奖证书正文 " + System.nanoTime();
        ctx.materialService().upload(List.of(file("数学建模获奖证书.pdf", body)), opts());
        Material m = all().get(0);

        Path out = Files.createTempDirectory("archive-sel-out-");
        Result<ExportResult> r = ctx.exportService().exportSelection(List.of(m.getId()), out);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(1, r.getData().fileCount());

        Path zip = r.getData().packagePath();
        assertTrue(Files.exists(zip));
        assertEquals(body, entry(zip, "数学建模获奖证书.pdf"), "压缩包内应使用原始显示名且内容一致");
        assertNotNull(entry(zip, "manifest.json"), "应附带清单");
    }

    @Test
    @DisplayName("B. 清单记录条数与 SHA-256，可用于校验完整性")
    void manifestCarriesChecksum() throws Exception {
        ctx.materialService().upload(List.of(file("成绩单.pdf", "abc"), file("证明.docx", "def")), opts());
        List<String> ids = all().stream().map(Material::getId).toList();

        Path out = Files.createTempDirectory("archive-sel-out2-");
        ExportResult r = ctx.exportService().exportSelection(ids, out).getData();

        assertEquals(2, r.fileCount());
        assertEquals(64, r.checksum().length(), "结果里应带 SHA-256");
        String manifest = entry(r.packagePath(), "manifest.json");
        assertTrue(manifest.contains("\"kind\": \"selection\""));
        assertTrue(manifest.contains("\"fileCount\": 2"));
        assertEquals(r.checksum(), com.campus.archive.infra.CryptoUtil.sha256Hex(r.packagePath()),
                "返回的校验和应与压缩包实际摘要一致");
    }

    @Test
    @DisplayName("C. 私密空间未解锁时拒绝导出私密材料，且不留下半成品压缩包")
    void lockedVaultBlocksExport() throws IOException {
        ctx.materialService().upload(List.of(file("身份证扫描件.png", "secret")), opts());
        Material m = all().get(0);

        ctx.securityService().initKey("123456".toCharArray());
        assertTrue(ctx.securityService().markPrivate(m.getId()).isSuccess());
        ctx.securityService().lock();

        Path out = Files.createTempDirectory("archive-sel-out3-");
        Result<ExportResult> r = ctx.exportService().exportSelection(List.of(m.getId()), out);

        assertFalse(r.isSuccess());
        assertEquals("E202", r.getCode());
        try (var files = Files.list(out)) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".zip")), "被拒绝时不应写出压缩包");
        }
    }

    @Test
    @DisplayName("D. 解锁后私密材料能正常导出，且内容是解密后的明文")
    void unlockedVaultExportsPlaintext() throws IOException {
        String body = "私密内容 " + System.nanoTime();
        ctx.materialService().upload(List.of(file("成绩单-私密.pdf", body)), opts());
        Material m = all().get(0);

        ctx.securityService().initKey("123456".toCharArray());
        assertTrue(ctx.securityService().markPrivate(m.getId()).isSuccess());

        Path out = Files.createTempDirectory("archive-sel-out4-");
        Result<ExportResult> r = ctx.exportService().exportSelection(List.of(m.getId()), out);

        assertTrue(r.isSuccess(), r.getMessage());
        assertEquals(body, entry(r.getData().packagePath(), "成绩单-私密.pdf"),
                "导出私密材料时应写入解密后的明文，而不是密文");
    }

    @Test
    @DisplayName("E. 空选择直接失败，不生成空压缩包")
    void emptySelectionFails() throws IOException {
        Path out = Files.createTempDirectory("archive-sel-out5-");
        Result<ExportResult> r = ctx.exportService().exportSelection(List.of(), out);
        assertFalse(r.isSuccess());
        assertEquals("E400", r.getCode());
        try (var files = Files.list(out)) {
            assertTrue(files.noneMatch(p -> p.toString().endsWith(".zip")));
        }
    }

    @Test
    @DisplayName("F. 同名材料导出时自动改名，不互相覆盖")
    void duplicateNamesDoNotOverwrite() throws Exception {
        Path a = Files.createTempDirectory("archive-dup-a-");
        Path b = Files.createTempDirectory("archive-dup-b-");
        File fa = TestEnv.sample(a, "同名材料.pdf", "来自A");
        File fb = TestEnv.sample(b, "同名材料.pdf", "来自B");
        ctx.materialService().upload(List.of(fa, fb), opts());
        assertEquals(2, all().size());

        Path out = Files.createTempDirectory("archive-sel-out6-");
        ExportResult r = ctx.exportService().exportSelection(all().stream().map(Material::getId).toList(), out)
                .getData();

        assertEquals(2, r.fileCount(), "两份同名材料都应被写入");
        java.util.Set<String> names = new java.util.HashSet<>();
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(r.packagePath()), StandardCharsets.UTF_8)) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) names.add(e.getName());
        }
        assertTrue(names.contains("同名材料.pdf"), "第一份保持原名");
        assertTrue(names.contains("同名材料(2).pdf"), "第二份应自动加序号，而不是覆盖");
        TestEnv.deleteRecursively(a);
        TestEnv.deleteRecursively(b);
    }
}
