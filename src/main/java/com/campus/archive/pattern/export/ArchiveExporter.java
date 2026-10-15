package com.campus.archive.pattern.export;

import com.campus.archive.dto.ExportRequest;
import com.campus.archive.dto.ExportResult;

/** 导出器统一契约。上层（ExportService / BackupService）只认识这个接口。 */
public interface ArchiveExporter {

    ExportResult export(ExportRequest request);

    String describe();
}
