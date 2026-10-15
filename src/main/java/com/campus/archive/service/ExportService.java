package com.campus.archive.service;

import com.campus.archive.dto.ExportResult;
import com.campus.archive.dto.Result;

import java.nio.file.Path;
import java.util.List;

/** 把用户选中的材料导出成压缩包。具体流程由 pattern.export 的模板方法实现。 */
public interface ExportService {

    /**
     * @param materialIds 选中的材料 ID
     * @param targetDir   导出到哪个目录
     */
    Result<ExportResult> exportSelection(List<String> materialIds, Path targetDir);
}
