package com.campus.archive.service;

import com.campus.archive.dto.Result;
import com.campus.archive.entity.Category;

import java.util.List;

/**
 * 分类树管理。
 *
 * <p>分类是一棵用户可编辑的树（界面按 Windows 资源管理器的方式呈现），
 * 因此这里要回答三个问题：能不能建、改名重不重、删了里面的材料怎么办。</p>
 */
public interface CategoryService {

    /** 全部分类（扁平，含 parentId）。界面自行组装成树。 */
    List<Category> listAll();

    /** 某个分类及其全部后代的 ID。筛选时用它，才能做到"点父分类也能看到子分类的材料"。 */
    List<String> subtreeIds(String categoryId);

    /** 在指定父分类下新建子分类；parentId 为 null 表示建顶层分类。 */
    Result<Category> create(String parentId, String name);

    Result<Void> rename(String categoryId, String newName);

    /**
     * 删除分类。为避免出现"材料指向不存在的分类"，删除前会把其中的材料
     * 移动到父分类（顶层分类则移动到兜底分类「其他」）。
     */
    Result<Void> delete(String categoryId);

    /** 删除前给界面提示用：该分类下有多少材料、多少子分类。 */
    Counts counts(String categoryId);

    record Counts(int materials, int children) {
    }
}
