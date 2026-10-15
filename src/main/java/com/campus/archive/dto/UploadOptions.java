package com.campus.archive.dto;

/** 上传选项：全部可选，默认不强制用户填写任何字段（对应需求 UR-03 / FR-05）。 */
public class UploadOptions {

    /** 是否让策略引擎给出分类 / 命名建议，对应 FR-04。 */
    private boolean suggestCategory = true;
    /** 用户指定的目标分类；为空时采用建议分类，再退化为"其他"。 */
    private String categoryId;
    /** 逗号分隔的标签。 */
    private String tags;

    public boolean isSuggestCategory() { return suggestCategory; }
    public UploadOptions setSuggestCategory(boolean suggestCategory) { this.suggestCategory = suggestCategory; return this; }

    public String getCategoryId() { return categoryId; }
    public UploadOptions setCategoryId(String categoryId) { this.categoryId = categoryId; return this; }

    public String getTags() { return tags; }
    public UploadOptions setTags(String tags) { this.tags = tags; return this; }
}
