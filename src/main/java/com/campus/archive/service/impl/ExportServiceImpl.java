package com.campus.archive.service.impl;

import com.campus.archive.dto.ExportRequest;
import com.campus.archive.dto.ExportResult;
import com.campus.archive.dto.Result;
import com.campus.archive.pattern.export.ArchiveExporter;
import com.campus.archive.pattern.export.ExportException;
import com.campus.archive.service.ExportService;

import java.nio.file.Path;
import java.util.List;

public class ExportServiceImpl implements ExportService {

    private final ArchiveExporter selectionExporter;

    public ExportServiceImpl(ArchiveExporter selectionExporter) {
        this.selectionExporter = selectionExporter;
    }

    @Override
    public Result<ExportResult> exportSelection(List<String> materialIds, Path targetDir) {
        if (materialIds == null || materialIds.isEmpty()) {
            return Result.fail("E400", "请先在列表中选择要导出的材料");
        }
        try {
            ExportResult r = selectionExporter.export(ExportRequest.ofSelection(materialIds, targetDir));
            return Result.ok(r);
        } catch (ExportException e) {
            return Result.fail(e.getCode(), e.getMessage());
        }
    }
}
