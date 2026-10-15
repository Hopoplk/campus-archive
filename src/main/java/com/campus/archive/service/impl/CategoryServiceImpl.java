package com.campus.archive.service.impl;

import com.campus.archive.config.Repositories;
import com.campus.archive.dto.Result;
import com.campus.archive.entity.Category;
import com.campus.archive.service.CategoryService;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;

public class CategoryServiceImpl implements CategoryService {

    /** 顶层分类被删时，其中的材料挪到这个内置兜底分类。 */
    private static final String FALLBACK_NAME = "其他";

    private final Repositories repos;

    public CategoryServiceImpl(Repositories repos) {
        this.repos = repos;
    }

    @Override
    public List<Category> listAll() {
        return repos.query(d -> d.category().findAll());
    }

    /**
     * 广度优先收集自身与全部后代。
     * 分类树规模很小（几十个节点），在内存里遍历比为它写递归 CTE 更直观、更好测。
     */
    @Override
    public List<String> subtreeIds(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) return List.of();
        List<Category> all = listAll();

        List<String> ids = new ArrayList<>();
        Deque<String> queue = new ArrayDeque<>();
        queue.add(categoryId);
        while (!queue.isEmpty()) {
            String cur = queue.poll();
            if (ids.contains(cur)) continue;   // 防数据异常造成的环
            ids.add(cur);
            for (Category c : all) {
                if (cur.equals(c.getParentId())) queue.add(c.getId());
            }
        }
        return ids;
    }

    @Override
    public Result<Category> create(String parentId, String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) return Result.fail("E300", "分类名不能为空");
        if (clean.length() > 20) return Result.fail("E300", "分类名不能超过 20 个字符");

        String pid = (parentId == null || parentId.isBlank()) ? null : parentId;
        if (pid != null && repos.query(d -> d.category().findById(pid)) == null) {
            return Result.fail("E300", "父分类不存在，可能已被删除");
        }
        if (repos.query(d -> d.category().countSiblingWithName(clean, pid)) > 0) {
            return Result.fail("E300", "同一层级下已经有叫「" + clean + "」的分类了");
        }

        Category c = new Category(UUID.randomUUID().toString(), clean, pid,
                repos.query(d -> d.category().nextSort(pid)));
        repos.query(d -> d.category().insert(c));
        return Result.ok(c);
    }

    @Override
    public Result<Void> rename(String categoryId, String newName) {
        String clean = newName == null ? "" : newName.trim();
        if (clean.isEmpty()) return Result.fail("E300", "分类名不能为空");
        if (clean.length() > 20) return Result.fail("E300", "分类名不能超过 20 个字符");

        Category cur = repos.query(d -> d.category().findById(categoryId));
        if (cur == null) return Result.fail("E300", "分类不存在，可能已被删除");
        if (clean.equals(cur.getName())) return Result.ok();

        if (repos.query(d -> d.category().countSiblingWithName(clean, cur.getParentId())) > 0) {
            return Result.fail("E300", "同一层级下已经有叫「" + clean + "」的分类了");
        }
        repos.query(d -> d.category().rename(categoryId, clean));
        return Result.ok();
    }

    @Override
    public Result<Void> delete(String categoryId) {
        Category cur = repos.query(d -> d.category().findById(categoryId));
        if (cur == null) return Result.fail("E300", "分类不存在，可能已被删除");
        if (FALLBACK_NAME.equals(cur.getName()) && cur.isRoot()) {
            return Result.fail("E300", "「其他」是内置兜底分类，不能删除");
        }
        if (repos.query(d -> d.category().countChildren(categoryId)) > 0) {
            return Result.fail("E300", "该分类下还有子分类，请先删除或移走它们");
        }

        // 材料不能悬空：优先并入父分类，顶层分类则并入「其他」
        String target = cur.getParentId();
        if (target == null) {
            Category fallback = repos.query(d -> d.category().findByName(FALLBACK_NAME));
            target = fallback == null ? null : fallback.getId();
        }
        if (target == null) {
            return Result.fail("E300", "找不到可接收材料的分类，请先恢复「其他」分类");
        }

        final String to = target;
        repos.query(d -> {
            d.category().moveMaterials(categoryId, to);
            return d.category().delete(categoryId);
        });
        return Result.ok();
    }

    @Override
    public Counts counts(String categoryId) {
        return new Counts(
                repos.query(d -> d.category().countMaterials(categoryId)),
                repos.query(d -> d.category().countChildren(categoryId)));
    }
}
