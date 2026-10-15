package com.campus.archive.pattern.export;

import com.campus.archive.dto.ExportItem;
import com.campus.archive.dto.ExportRequest;
import com.campus.archive.entity.Material;
import com.campus.archive.entity.Schedule;
import com.campus.archive.infra.MiniJson;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板方法的具体实现 B：全量档案备份（对应需求 UR-09 / AC-10）。
 *
 * <p>与 A 的差异恰好体现了模板方法的收益：骨架一行没改，只换了"收集什么"——
 * 这里收集全部材料，并额外把日程导出成 schedule.json 一并打包，
 * 再通过 extraManifestJson 钩子把日程条数写进清单。</p>
 */
public class FullBackupExporter extends AbstractArchiveExporter {

    private Path scheduleTemp;

    public FullBackupExporter(ExportContext ctx) {
        super(ctx);
    }

    @Override
    protected List<ExportItem> collect(ExportRequest request) {
        List<Material> all = ctx.allMaterials().apply(new com.campus.archive.dto.MaterialQuery());

        List<ExportItem> out = new ArrayList<>(all.size() + 1);
        for (Material m : all) {
            if (m.getIsPrivate() && !request.isIncludePrivate()) continue;
            out.add(new ExportItem(m.getId(), m.getFileName(), m.getStoredPath(), m.getFileSize(), m.getIsPrivate()));
        }
        out.add(scheduleEntry());
        return out;
    }

    /** 把日程表导出成虚拟条目，跟着档案一起迁移。 */
    private ExportItem scheduleEntry() {
        List<Schedule> schedules = ctx.schedules().get();
        List<Object> rows = new ArrayList<>();
        for (Schedule s : schedules) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", s.getTitle());
            row.put("dueTime", s.getDueTime() == null ? "" : s.getDueTime().toString());
            row.put("remindAt", s.getRemindAt() == null ? "" : s.getRemindAt().toString());
            row.put("status", String.valueOf(s.getStatus()));
            row.put("remark", s.getRemark() == null ? "" : s.getRemark());
            rows.add(row);
        }
        String json = MiniJson.write(rows);
        try {
            scheduleTemp = Files.createTempFile("archive-schedule-", ".json");
            Files.writeString(scheduleTemp, json, StandardCharsets.UTF_8);
            scheduleTemp.toFile().deleteOnExit();
        } catch (IOException e) {
            throw new ExportException("E401", "日程导出失败", e);
        }
        return new ExportItem(null, "schedule.json", scheduleTemp.toAbsolutePath().toString(),
                json.getBytes(StandardCharsets.UTF_8).length, false);
    }

    @Override
    protected String emptyMessage() {
        return "个人档案中还没有任何材料，无法备份";
    }

    @Override
    protected String packageKind() {
        return "backup";
    }

    @Override
    protected boolean requiresUnlockedVault(ExportRequest request) {
        return request.isIncludePrivate();
    }

    @Override
    protected Map<String, Object> extraManifest(ExportRequest request) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("scheduleCount", ctx.schedules().get().size());
        extra.put("includePrivate", request.isIncludePrivate());
        return extra;
    }

    @Override
    protected void afterExport(com.campus.archive.dto.ExportResult result, ExportRequest request) {
        if (scheduleTemp != null) {
            try {
                Files.deleteIfExists(scheduleTemp);
            } catch (IOException ignored) {
                // 临时文件已登记 deleteOnExit，删不掉不影响导出结果
            }
        }
    }

    @Override
    public String describe() {
        return "全量档案备份";
    }
}
