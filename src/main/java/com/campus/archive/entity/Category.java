package com.campus.archive.entity;

/**
 * 材料分类，对应 category 表。
 *
 * <p>分类是一棵树：{@code parentId} 为 null 表示顶层分类。界面按 Windows 资源管理器的
 * 方式展示，用户可自行新增子分类、重命名、删除。</p>
 */
public class Category {

    private String id;
    private String name;
    private String parentId;
    private int sort;

    public Category() {
    }

    public Category(String id, String name, int sort) {
        this(id, name, null, sort);
    }

    public Category(String id, String name, String parentId, int sort) {
        this.id = id;
        this.name = name;
        this.parentId = parentId;
        this.sort = sort;
    }

    public boolean isRoot() {
        return parentId == null || parentId.isBlank();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getParentId() { return parentId; }
    public void setParentId(String parentId) { this.parentId = parentId; }

    public int getSort() { return sort; }
    public void setSort(int sort) { this.sort = sort; }

    @Override
    public String toString() { return name; }
}
