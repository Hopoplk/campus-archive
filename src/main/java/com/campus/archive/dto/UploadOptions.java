package com.campus.archive.dto;

/** 上传选项。分类由调用方显式给出，未给出时由服务层落到兜底分类。 */
public class UploadOptions {

    private String categoryId;
    private String tags;

    public String getCategoryId() { return categoryId; }
    public UploadOptions setCategoryId(String categoryId) { this.categoryId = categoryId; return this; }

    public String getTags() { return tags; }
    public UploadOptions setTags(String tags) { this.tags = tags; return this; }
}
