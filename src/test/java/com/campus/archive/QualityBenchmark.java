package com.campus.archive;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.MaterialQuery;
import com.campus.archive.dto.UploadOptions;
import com.campus.archive.entity.Material;
import com.campus.archive.infra.CryptoUtil;
import com.campus.archive.infra.FileStore;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 质量基准测试：为第 6 周的"架构质量分析与评估"提供实测数据。
 *
 * <p>本类不是单元测试（没有 @Test），而是一个可重复执行的小型基准程序，
 * 用真实的数据量与真实的调用路径测量性能、安全与可修改性相关的指标。
 * 运行方式：</p>
 *
 * <pre>
 * mvn -q test-compile
 * java -cp "target/classes;target/test-classes;target/cp.txt 里列出的依赖" \
 *      com.campus.archive.QualityBenchmark
 * </pre>
 */
public final class QualityBenchmark {

    private static final int[] SCALES = {3000, 20000, 50000};   // 三个规模点，用于观察检索耗时随数据量的变化趋势
    private static final int SEARCH_ROUNDS = 30;
    private static final int UPLOAD_FILES = 50;
    private static final int EXPORT_FILES = 100;

    private final List<String> lines = new ArrayList<>();

    public static void main(String[] args) throws Exception {
        QualityBenchmark b = new QualityBenchmark();
        b.run();
        // 结果同时写入 UTF-8 文件，便于归档到评估报告与汇报材料
        Path report = args.length > 0 ? Path.of(args[0]) : Path.of("quality-benchmark.txt");
        Files.writeString(report, String.join(System.lineSeparator(), b.lines), StandardCharsets.UTF_8);
        System.out.println();
        System.out.println("Benchmark report written to " + report.toAbsolutePath());
    }

    private void run() throws Exception {
        Path home = Files.createTempDirectory("archive-bench-");
        Path staging = Files.createTempDirectory("archive-bench-src-");
        AppContext ctx = AppContext.forTesting(home);

        out("======================================================================");
        out(" 架构质量基准测试  ——  大学生个人学业档案与证书日程管理系统 MVP 原型");
        out(" 运行环境：JDK " + System.getProperty("java.version")
                + " / " + System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        out(" CPU 核心数：" + Runtime.getRuntime().availableProcessors()
                + "　最大堆：" + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        out(" 数据目录：" + home);
        out("======================================================================");

        int seeded = 0;
        for (int scale : SCALES) {
            seed(ctx, seeded, scale - seeded);
            measureSearch(ctx, scale + " 条");
            seeded = scale;
        }
        measureUpload(ctx, staging);
        measureCrypto(ctx);
        measurePrivateFlow(ctx, staging);
        measureExport(ctx);
        measureBackup(ctx);

        out("");
        out("测试完成。以上数值为单次运行的实测值，用于质量属性场景的响应度量。");
    }

    /* ---------------- 1. 播种 ---------------- */

    private void seed(AppContext ctx, int from, int count) {
        section("A. 数据播种（模拟真实使用两三年的档案规模）");
        long t0 = System.nanoTime();

        List<String> cats = ctx.repos().query(d -> d.category().findAll())
                .stream().map(c -> c.getId()).toList();

        String[] prefixes = {"大学英语四级成绩报告单", "全国大学生数学建模竞赛获奖证书", "计算机二级合格证书",
                "志愿服务时长证明", "毕业实习鉴定表", "科研立项结题报告", "普通话等级证书",
                "互联网+创新创业大赛获奖证书", "国家励志奖学金证书", "学业成绩单"};
        String[] exts = {".pdf", ".jpg", ".png", ".docx"};

        List<Material> batch = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            int idx = from + i;
            String name = prefixes[idx % prefixes.length] + "-" + (2020 + idx % 7) + "-"
                    + String.format("%05d", idx) + exts[idx % exts.length];
            Material m = new Material(name, "seed/" + UUID.randomUUID(),
                    com.campus.archive.entity.FileType.of(name), 1024L * (1 + idx % 500),
                    String.format("%064x", (long) idx * 2654435761L));
            m.setId(UUID.randomUUID().toString());
            m.setCategoryId(cats.get(idx % cats.size()));
            m.setIsPrivate(false);
            m.setCreatedAt(LocalDateTime.now().minusDays(idx % 900));
            batch.add(m);
        }
        // 单事务批量写入，避免逐条提交带来的额外开销
        ctx.repos().tx(d -> {
            for (Material m : batch) d.material().insert(m);
            return null;
        });

        long ms = (System.nanoTime() - t0) / 1_000_000;
        out(String.format("  新播种材料记录 %d 条，耗时 %d ms（%.2f ms/条，单事务批量写入）",
                count, ms, ms / (double) count));
    }

    /* ---------------- 2. 检索 ---------------- */

    private void measureSearch(AppContext ctx, String scaleLabel) {
        section("B. 检索响应（质量属性：性能）　当前规模：" + scaleLabel);

        String[] keywords = {"四级", "数学建模", "证书", "志愿服务", "结题报告", "普通话"};
        List<Long> all = new ArrayList<>();

        for (String kw : keywords) {
            MaterialQuery q = new MaterialQuery().setKeyword(kw);
            ctx.searchService().search(q, 1, 50);          // 预热
            long t0 = System.nanoTime();
            int rounds = SEARCH_ROUNDS / keywords.length;
            int total = 0;
            for (int i = 0; i < rounds; i++) {
                total = ctx.searchService().search(q, 1, 50).total();
            }
            long each = (System.nanoTime() - t0) / rounds;
            all.add(each);
            out(String.format("  关键词「%s」：命中 %5d 条，单次 %.2f ms", kw, total, each / 1e6));
        }

        out(String.format("  关键词检索 P50 = %.2f ms，最大 = %.2f ms",
                percentile(all, 50) / 1e6, all.stream().mapToLong(Long::longValue).max().orElse(0) / 1e6));

        MaterialQuery combo = new MaterialQuery();
        combo.setCategoryId(ctx.repos().query(d -> d.category().findByName("证书")).getId());
        combo.setFileType(com.campus.archive.entity.FileType.PDF);
        long t1 = System.nanoTime();
        int n = 0;
        for (int i = 0; i < 20; i++) n = ctx.searchService().search(combo, 1, 50).total();
        out(String.format("  组合筛选（分类=证书 且 类型=PDF）：命中 %5d 条，单次 %.2f ms",
                n, (System.nanoTime() - t1) / 20 / 1e6));

        long t2 = System.nanoTime();
        for (int i = 0; i < 20; i++) ctx.searchService().recent(8);
        out(String.format("  首页「最近添加」查询：单次 %.2f ms", (System.nanoTime() - t2) / 20 / 1e6));
    }

    /* ---------------- 3. 上传 ---------------- */

    private void measureUpload(AppContext ctx, Path staging) throws Exception {
        section("C. 批量上传（质量属性：性能 + 可靠性）");

        File dir = staging.resolve("upload").toFile();
        dir.mkdirs();
        List<File> files = new ArrayList<>();
        long bytes = 0;
        for (int i = 0; i < UPLOAD_FILES; i++) {
            File f = new File(dir, "批量上传测试材料-" + i + ".pdf");
            byte[] data = new byte[64 * 1024];         // 每份 64 KB，50 份共约 3.2 MB
            for (int j = 0; j < data.length; j++) data[j] = (byte) ((i * 31 + j) & 0xFF);  // 每份内容唯一
            Files.write(f.toPath(), data);
            files.add(f);
            bytes += data.length;
        }

        long t0 = System.nanoTime();
        var result = ctx.materialService().upload(files, new UploadOptions());
        long ms = (System.nanoTime() - t0) / 1_000_000;

        out(String.format("  批量上传 %d 个文件（合计 %d KB）：总耗时 %d ms，平均 %.2f ms/个",
                UPLOAD_FILES, bytes / 1024, ms, ms / (double) UPLOAD_FILES));
        out(String.format("  成功 %d 个，失败 %d 个", result.getData().getSuccessCount(), result.getData().getFailCount()));

        // 重复文件检测：把同一个文件再传一次
        long d0 = System.nanoTime();
        var dup = ctx.materialService().upload(List.of(files.get(0), files.get(1)), new UploadOptions());
        out(String.format("  重复上传同一批文件中的 2 个：耗时 %d ms，其中被识别为疑似重复 %d 个",
                (System.nanoTime() - d0) / 1_000_000, dup.getData().getDuplicateWarnings().size()));

        int created = 0;
        var page = ctx.searchService().search(new MaterialQuery().setKeyword("批量上传测试材料"), 1, 100);
        for (Material m : page.items()) {
            if (Files.exists(ctx.fileStore().resolve(m.getStoredPath()))) created++;
        }
        out(String.format("  落盘校验：%d/%d 个文件真实存在于 files/ 目录", created, page.items().size()));
    }

    /* ---------------- 4. 加密 ---------------- */

    private void measureCrypto(AppContext ctx) {
        section("D. 安全机制开销（质量属性：安全性 + 性能）");

        char[] key = "benchmark-key-123456".toCharArray();

        long t0 = System.nanoTime();
        byte[] salt = CryptoUtil.randomBytes(16);
        byte[] derived = null;
        for (int i = 0; i < 5; i++) derived = CryptoUtil.deriveKey(key, salt, CryptoUtil.KDF_ROUNDS);
        out(String.format("  PBKDF2-HMAC-SHA256（%d 轮）派生 256 位密钥：单次 %.0f ms",
                CryptoUtil.KDF_ROUNDS, (System.nanoTime() - t0) / 5 / 1e6));

        byte[] iv = CryptoUtil.randomBytes(12);
        for (int mb : new int[]{1, 10}) {
            byte[] plain = new byte[mb * 1024 * 1024];
            java.util.Arrays.fill(plain, (byte) 7);

            long e0 = System.nanoTime();
            byte[] cipher = CryptoUtil.encrypt(plain, derived, iv);
            long enc = System.nanoTime() - e0;

            long d0 = System.nanoTime();
            CryptoUtil.decrypt(cipher, derived, iv);
            long dec = System.nanoTime() - d0;

            out(String.format("  AES-256-GCM 处理 %2d MB：加密 %5.0f ms（%.0f MB/s），解密 %5.0f ms（%.0f MB/s）",
                    mb, enc / 1e6, mb / (enc / 1e9), dec / 1e6, mb / (dec / 1e9)));
        }
    }

    /* ---------------- 5. 私密流程 ---------------- */

    private void measurePrivateFlow(AppContext ctx, Path staging) throws Exception {
        section("E. 私密材料完整流程（质量属性：安全性）");

        // 先设置安全密钥（首次使用私密空间的必经步骤）
        boolean inited = ctx.securityService().initKey("benchmark-key-123456".toCharArray()).isSuccess();
        out(String.format("  首次设置安全密钥（生成 16 字节盐 + PBKDF2 派生 + 写库）：%s", inited ? "成功" : "失败"));

        File f = staging.resolve("私密基准材料.pdf").toFile();
        Files.write(f.toPath(), "基准测试用私密材料内容".getBytes(StandardCharsets.UTF_8));
        ctx.materialService().upload(List.of(f), new UploadOptions());
        Material m = ctx.searchService().search(new MaterialQuery().setKeyword("私密基准材料"), 1, 5).items().get(0);

        long t0 = System.nanoTime();
        boolean ok = ctx.securityService().markPrivate(m.getId()).isSuccess();
        out(String.format("  「设为私密」端到端（读明文 → AES 加密 → 写密文 → 事务写库 → 删除明文）：%.0f ms，结果=%s",
                (System.nanoTime() - t0) / 1e6, ok ? "成功" : "失败"));

        FileStore fs = ctx.fileStore();
        out(String.format("  明文副本是否已删除：%s", !Files.exists(fs.resolve(m.getStoredPath())) ? "是" : "否"));
        out(String.format("  元数据是否标记为私密：%s",
                ctx.materialService().findById(m.getId()).getIsPrivate() ? "是" : "否"));

        // 锁定后读取
        ctx.securityService().lock();
        var locked = ctx.securityService().readVault(m.getId());
        out(String.format("  锁定状态下读取私密内容：%s（错误码 %s）",
                locked.isSuccess() ? "异常放行" : "按预期拒绝", locked.getCode()));

        // 错误密钥
        long t1 = System.nanoTime();
        boolean bad = ctx.securityService().unlock("wrong-key".toCharArray()).isSuccess();
        out(String.format("  错误密钥校验：%.0f ms，结果=%s（累计失败 %d 次）",
                (System.nanoTime() - t1) / 1e6, bad ? "异常通过" : "按预期拒绝", ctx.securityService().failCount()));

        // 正确密钥
        long t2 = System.nanoTime();
        boolean good = ctx.securityService().unlock("benchmark-key-123456".toCharArray()).isSuccess();
        out(String.format("  正确密钥解锁：%.0f ms，结果=%s", (System.nanoTime() - t2) / 1e6, good ? "成功" : "失败"));

        // 解密读取
        long t3 = System.nanoTime();
        var read = ctx.securityService().readVault(m.getId());
        out(String.format("  解锁后解密读取：%.0f ms，结果=%s，内容一致=%s",
                (System.nanoTime() - t3) / 1e6, read.isSuccess() ? "成功" : "失败",
                read.isSuccess() && new String(read.getData(), StandardCharsets.UTF_8).contains("私密材料内容") ? "是" : "否"));

        // 密文篡改检测
        var vault = ctx.repos().query(d -> d.security().findVaultByMaterial(m.getId()));
        Path cipherPath = fs.vaultRoot().resolve(vault.getEncryptedPath());
        byte[] cipher = Files.readAllBytes(cipherPath);
        cipher[cipher.length / 2] ^= 0x5A;
        Files.write(cipherPath, cipher);
        var tampered = ctx.securityService().readVault(m.getId());
        out(String.format("  篡改密文后读取：%s（错误码 %s）",
                tampered.isSuccess() ? "异常放行" : "按预期拒绝", tampered.getCode()));
    }

    /* ---------------- 6. 导出 ---------------- */

    private void measureExport(AppContext ctx) throws Exception {
        section("F. 选定材料导出（质量属性：性能 + 可修改性）");

        var all = ctx.searchService().search(new MaterialQuery().setKeyword("批量上传测试材料"), 1, EXPORT_FILES);
        List<String> ids = all.items().stream().map(Material::getId).toList();

        Path out = Files.createTempDirectory("archive-bench-export-");
        long t0 = System.nanoTime();
        var r = ctx.exportService().exportSelection(ids, out);
        long ms = (System.nanoTime() - t0) / 1_000_000;

        if (r.isSuccess()) {
            out(String.format("  导出 %d 个文件：耗时 %d ms，包大小 %d KB，校验和 %s…",
                    r.getData().fileCount(), ms, r.getData().bytes() / 1024,
                    r.getData().checksum().substring(0, 16)));
        } else {
            out("  导出失败：" + r.getMessage());
        }
    }

    /* ---------------- 7. 备份 ---------------- */

    private void measureBackup(AppContext ctx) throws Exception {
        section("G. 全量备份导出（质量属性：可靠性 + 性能）");
        Path out = Files.createTempDirectory("archive-bench-backup-");
        long t0 = System.nanoTime();
        var r = ctx.backupService().exportAll(out, false);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        if (r.isSuccess()) {
            out(String.format("  全量备份 %d 个文件：耗时 %d ms，包大小 %d KB",
                    r.getData().fileCount(), ms, r.getData().bytes() / 1024));
        } else {
            out("  备份失败：" + r.getMessage());
        }

        // 导入往返
        AppContext target = AppContext.forTesting(Files.createTempDirectory("archive-bench-import-"));
        long t1 = System.nanoTime();
        var imp = target.backupService().importFrom(r.getData().packagePath());
        out(String.format("  跨档案导入：耗时 %d ms，结果=%s",
                (System.nanoTime() - t1) / 1_000_000, imp.isSuccess() ? imp.getData() : imp.getMessage()));
    }

    /* ---------------- 工具 ---------------- */

    private void section(String title) {
        out("");
        out("---- " + title + " " + "-".repeat(Math.max(0, 62 - title.length())));
    }

    private void out(String s) {
        lines.add(s);
        System.out.println(s);
    }

    private static long percentile(List<Long> values, int p) {
        List<Long> sorted = new ArrayList<>(values);
        sorted.sort(Long::compareTo);
        int idx = Math.min(sorted.size() - 1, Math.max(0, (int) Math.ceil(p / 100.0 * sorted.size()) - 1));
        return sorted.get(idx);
    }
}
