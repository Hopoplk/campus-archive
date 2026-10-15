package com.campus.archive.pattern.export;

import com.campus.archive.dto.ExportItem;
import com.campus.archive.dto.ExportRequest;
import com.campus.archive.entity.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 模板方法的具体实现 A：把用户在"我的档案"里选中的材料导出成一个压缩包。
 *
 * <p>与 B（全量备份）的差异恰好体现模板方法的收益：骨架一行没改，只换了"收集什么"——
 * 这里按用户选定的 ID 收集，既不是全部、也不是某个预定义集合。</p>
 */
public class SelectionExportExporter extends AbstractArchiveExporter {

    public SelectionExportExporter(ExportContext ctx) {
        super(ctx);
    }

    @Override
    protected List<ExportItem> collect(ExportRequest request) {
        List<String> ids = request.getMaterialIds();
        if (ids == null || ids.isEmpty()) return List.of();

        List<ExportItem> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            Material m = ctx.materialById().apply(id);
            if (m == null) continue;
            out.add(new ExportItem(m.getId(), m.getFileName(), m.getStoredPath(),
                    m.getFileSize(), m.getIsPrivate()));
        }
        return out;
    }

    @Override
    protected String emptyMessage() {
        return "请先在列表中选择要导出的材料";
    }

    @Override
    protected String packageKind() {
        return "selection";
    }

    /** 选中的材料里可能混有私密材料，导出前必须解锁，否则读取会被拒绝。 */
    @Override
    protected boolean requiresUnlockedVault(ExportRequest request) {
        return true;
    }

    @Override
    public String describe() {
        return "选定材料导出";
    }
}
