package com.campus.archive.pattern.export;

import com.campus.archive.dto.ExportItem;
import com.campus.archive.dto.ExportRequest;
import com.campus.archive.dto.ExportResult;
import com.campus.archive.infra.CryptoUtil;
import com.campus.archive.infra.MiniJson;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 【设计模式 ② 模板方法模式 Template Method】导出流程骨架。
 *
 * <p>为什么在这里用模板方法：系统有两种导出——"选定材料导出"和"全量档案备份"。它们的
 * <b>流程骨架完全相同</b>（校验目标 → 收集条目 → 检查私密解锁 → 打包 → 写清单 → 算校验和 → 收尾），
 * 区别只在于"收集哪些条目"和"是否需要额外写入清单内容"。若各写一份，两个流程会重复约 80 行代码，
 * 且将来修改打包方式（例如换成 tar.gz）必须改两处，极易漏改。</p>
 *
 * <p>模板方法把不变的骨架固定在 {@link #export} 中（并声明为 final，防止子类破坏流程），
 * 只把变化的步骤开放为抽象方法或钩子。</p>
 *
 * <p>本模板的步骤划分：</p>
 * <ol>
 *   <li>{@link #export} —— 模板方法，固定算法骨架，final 不可覆盖</li>
 *   <li>{@link #collect} —— <b>抽象</b>步骤，子类必须实现（收集哪些条目）</li>
 *   <li>{@link #emptyMessage} / {@link #packageKind} —— <b>抽象</b>步骤，子类提供差异化文案与类型</li>
 *   <li>{@link #validate} / {@link #requiresUnlockedVault} / {@link #extraManifestJson} / {@link #afterExport}
 *       —— <b>钩子</b>，提供默认空实现，子类按需覆盖</li>
 * </ol>
 */
public abstract class AbstractArchiveExporter implements ArchiveExporter {

    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    protected final ExportContext ctx;

    protected AbstractArchiveExporter(ExportContext ctx) {
        this.ctx = ctx;
    }

    /* ============================ 模板方法 ============================ */

    /**
     * 导出流程骨架。声明为 final：子类只能填充各个步骤，不能改动流程顺序，
     * 这正是模板方法模式的核心约束。
     */
    public final ExportResult export(ExportRequest request) {
        validate(request);

        List<ExportItem> collected = collect(request);
        if (collected == null || collected.isEmpty()) {
            throw new ExportException("E400", emptyMessage());
        }

        List<ExportItem> valid = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (ExportItem item : collected) {
            if (item.isValid()) valid.add(item);
            else skipped.add(item.displayName());
        }
        if (valid.isEmpty()) {
            throw new ExportException("E400", "所有条目都已失效，没有可导出的内容");
        }

        guardVault(request, valid);

        Path target = request.getTargetDir();
        Path zipPath = target.resolve(fileName(request));
        Set<String> usedNames = new HashSet<>();
        int written = 0;

        try {
            Files.createDirectories(target);
            try (OutputStream fos = Files.newOutputStream(zipPath);
                 ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
                for (ExportItem item : valid) {
                    byte[] content;
                    try {
                        content = ctx.contentReader().read(item);
                    } catch (IOException | IllegalStateException e) {
                        // 单个文件读不出来，只跳过它，不中断整包导出
                        skipped.add(item.displayName());
                        continue;
                    }
                    zos.putNextEntry(new ZipEntry(uniqueName(usedNames, item.displayName())));
                    zos.write(content);
                    zos.closeEntry();
                    written++;
                }
                writeManifest(zos, request, valid, skipped, written);
            }
        } catch (IOException e) {
            throw new ExportException("E401", "无法写入所选位置，请更换目录", e);
        }

        String checksum;
        long bytes;
        try {
            checksum = CryptoUtil.sha256Hex(zipPath);
            bytes = Files.size(zipPath);
        } catch (IOException e) {
            throw new ExportException("E401", "导出文件校验失败", e);
        }

        ExportResult result = new ExportResult(zipPath, written, skipped, checksum, bytes);
        afterExport(result, request);
        return result;
    }

    /* ============================ 抽象步骤 ============================ */

    /** 收集本次导出要打包的条目。这是子类唯一必须回答的核心问题。 */
    protected abstract List<ExportItem> collect(ExportRequest request);

    /** 没有任何条目时的错误提示。 */
    protected abstract String emptyMessage();

    /** 清单里记录的包类型，如 package / backup。 */
    protected abstract String packageKind();

    /* ============================ 钩子方法 ============================ */

    /** 钩子：导出前的校验，默认只检查目标目录。 */
    protected void validate(ExportRequest request) {
        Path target = request.getTargetDir();
        if (target == null) throw new ExportException("E401", "请先选择导出位置");
        try {
            Files.createDirectories(target);
        } catch (IOException e) {
            throw new ExportException("E401", "无法创建导出目录：" + target, e);
        }
        if (!Files.isWritable(target)) {
            throw new ExportException("E401", "目标目录不可写，请更换目录");
        }
    }

    /** 钩子：本次导出是否要求私密空间处于已解锁状态。默认不需要。 */
    protected boolean requiresUnlockedVault(ExportRequest request) {
        return false;
    }

    /** 钩子：写入清单时附加的字段，默认无。 */
    protected Map<String, Object> extraManifest(ExportRequest request) {
        return Map.of();
    }

    /** 钩子：打包完成后的收尾动作，默认什么都不做。 */
    protected void afterExport(ExportResult result, ExportRequest request) {
        // 默认空实现
    }

    /* ============================ 私有实现 ============================ */

    private void guardVault(ExportRequest request, List<ExportItem> items) {
        boolean hasPrivate = items.stream().anyMatch(ExportItem::isPrivate);
        if (!hasPrivate) return;
        if (requiresUnlockedVault(request) && !ctx.vaultGate().isUnlocked()) {
            throw new ExportException("E202", "包含私密材料，请先解锁私密空间再导出");
        }
    }

    private String fileName(ExportRequest request) {
        return request.getLabel() + "-" + LocalDateTime.now().format(STAMP) + ".zip";
    }

    /** 压缩包内的条目名去重，避免同名文件互相覆盖。 */
    private String uniqueName(Set<String> used, String displayName) {
        String name = sanitize(displayName);
        if (used.add(name)) return name;
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String ext = dot > 0 ? name.substring(dot) : "";
        int i = 2;
        while (!used.add(base + "(" + i + ")" + ext)) i++;
        return base + "(" + i + ")" + ext;
    }

    private String sanitize(String raw) {
        if (raw == null || raw.isBlank()) return "未命名文件";
        return raw.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private void writeManifest(ZipOutputStream zos, ExportRequest request,
                               List<ExportItem> items, List<String> skipped, int written) throws IOException {
        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("kind", packageKind());
        manifest.put("label", request.getLabel());
        manifest.put("exportedAt", LocalDateTime.now().toString());
        manifest.put("appVersion", "1.0.0");
        manifest.put("checksumAlgorithm", "SHA-256");
        manifest.put("fileCount", written);

        List<Object> files = new ArrayList<>();
        for (ExportItem it : items) {
            Map<String, Object> f = new LinkedHashMap<>();
            f.put("name", it.displayName());
            f.put("size", it.size());
            f.put("private", it.isPrivate());
            files.add(f);
        }
        manifest.put("files", files);
        if (!skipped.isEmpty()) manifest.put("skipped", skipped);
        manifest.putAll(extraManifest(request));

        zos.putNextEntry(new ZipEntry("manifest.json"));
        zos.write(MiniJson.write(manifest).getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }
}
