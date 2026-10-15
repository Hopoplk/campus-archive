package com.campus.archive.entity;

import java.time.LocalDateTime;

/** 回收站记录：支持误删恢复，并记住材料被删除前所在的分类。 */
public class TrashRecord {

    private String id;
    private String materialId;
    private LocalDateTime deletedAt;
    private String originCategoryId;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getMaterialId() { return materialId; }
    public void setMaterialId(String materialId) { this.materialId = materialId; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    public String getOriginCategoryId() { return originCategoryId; }
    public void setOriginCategoryId(String originCategoryId) { this.originCategoryId = originCategoryId; }
}
