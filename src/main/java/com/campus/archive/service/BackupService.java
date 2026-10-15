package com.campus.archive.service;

import com.campus.archive.dto.ExportResult;
import com.campus.archive.dto.Result;

import java.nio.file.Path;

/** 备份与迁移服务（对应需求 UR-09、AC-10）。 */
public interface BackupService {

    /** 全量导出，内部委托给模板方法模式下的备份导出器（模式 ②）。 */
    Result<ExportResult> exportAll(Path targetDir, boolean includePrivate);

    /** 从备份包导入；整包在单个事务内完成，失败不破坏既有数据。 */
    Result<String> importFrom(Path packagePath);
}
