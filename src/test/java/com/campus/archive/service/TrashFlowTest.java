package com.campus.archive.service;

import com.campus.archive.TestEnv;
import com.campus.archive.config.AppContext;
import com.campus.archive.dto.MaterialQuery;
import com.campus.archive.dto.Page;
import com.campus.archive.dto.UploadOptions;
import com.campus.archive.entity.Material;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回收站分类的查询语义与生命周期。
 *
 * <p>界面上"回收站"被做成了左侧分类树里的一个分类项，对应这里的
 * {@link MaterialQuery#setOnlyTrashed(boolean)}：只看已删除的材料，按删除时间倒序。
 * 普通分类则只看未删除的材料——两边必须互斥，不能出现同一材料在两处同时可见。</p>
 *
 * <p>每个用例使用独立的临时数据目录，互不影响、不依赖执行顺序。</p>
 */
@DisplayName("回收站分类：查询语义与恢复/永久删除")
class TrashFlowTest {

    private AppContext ctx;
    private Path staging;

    @BeforeEach
    void setUp() throws Exception {
        ctx = TestEnv.newContext("trash");
        staging = Files.createTempDirectory("archive-trash-src-");
    }

    @AfterEach
    void tearDown() {
        TestEnv.deleteRecursively(staging);
    }

    private File file(String name) {
        return TestEnv.sample(staging, name, "内容 " + name + " " + System.nanoTime());
    }

    private void upload(String... names) {
        List<File> files = java.util.Arrays.stream(names).map(this::file).toList();
        var r = ctx.materialService().upload(files, new UploadOptions());
        assertEquals(names.length, r.getData().getSuccessCount(), "上传应全部成功：" + r.getData().summaryText());
    }

    private Material find(String keyword) {
        return ctx.searchService().search(new MaterialQuery().setKeyword(keyword), 1, 5).items().get(0);
    }

    private List<Material> normal() {
        return ctx.searchService().search(new MaterialQuery(), 1, 100).items();
    }

    private List<Material> trash() {
        return ctx.searchService().search(new MaterialQuery().setOnlyTrashed(true), 1, 100).items();
    }

    @Test
    @DisplayName("A. 普通分类与回收站分类互斥：同一材料不会同时出现在两边")
    void activeAndTrashAreMutuallyExclusive() {
        upload("回收站测试-甲.pdf", "回收站测试-乙.pdf", "回收站测试-丙.pdf");
        assertEquals(3, normal().size(), "上传后三份都应在普通分类里");
        assertTrue(trash().isEmpty(), "刚上传的材料不应出现在回收站");

        Material a = find("甲");
        assertTrue(ctx.materialService().softDelete(List.of(a.getId())).isSuccess());

        assertEquals(2, normal().size(), "删掉一份后普通分类应剩两份");
        assertEquals(1, trash().size(), "回收站里应有且只有一份");
        assertEquals(a.getId(), trash().get(0).getId());
        assertTrue(normal().stream().noneMatch(m -> m.getId().equals(a.getId())),
                "已删除的材料不得再出现在普通分类");
    }

    @Test
    @DisplayName("B. 回收站按删除时间倒序，最近删的排最前")
    void trashIsOrderedByDeletedAtDesc() throws Exception {
        upload("回收站测试-乙.pdf", "回收站测试-丙.pdf");
        Material b = find("乙");
        Material c = find("丙");

        assertTrue(ctx.materialService().softDelete(List.of(b.getId())).isSuccess());
        Thread.sleep(1100);   // 删除时间字段精度到秒，隔开一秒才能验证排序
        assertTrue(ctx.materialService().softDelete(List.of(c.getId())).isSuccess());

        List<Material> t = trash();
        assertEquals(2, t.size());
        assertEquals(c.getId(), t.get(0).getId(), "后删的丙应排在前面");
        assertEquals(b.getId(), t.get(1).getId());
    }

    @Test
    @DisplayName("C. 从回收站恢复后回到原分类，且不再出现在回收站")
    void restoreReturnsToOriginCategory() {
        upload("回收站测试-乙.pdf", "回收站测试-丙.pdf");
        Material b = find("乙");
        ctx.materialService().softDelete(List.of(b.getId()));
        Material trashed = ctx.materialService().findById(b.getId());
        String originCategory = trashed.getCategoryId();

        assertTrue(ctx.materialService().restore(List.of(b.getId())).isSuccess());

        Material after = ctx.materialService().findById(b.getId());
        assertFalse(after.isTrashed(), "恢复后不应再带删除标记");
        assertEquals(originCategory, after.getCategoryId(), "应回到原分类");
        assertTrue(trash().isEmpty(), "回收站应已清空");
        assertEquals(2, normal().size(), "普通分类应恢复为两份");
    }

    @Test
    @DisplayName("D. 永久删除后数据库与磁盘都不再保留")
    void purgeRemovesPermanently() {
        upload("回收站测试-丙.pdf");
        Material c = find("丙");
        Path stored = ctx.fileStore().resolve(c.getStoredPath());
        ctx.materialService().softDelete(List.of(c.getId()));
        assertTrue(Files.exists(stored), "永久删除之前磁盘文件应存在");

        assertTrue(ctx.materialService().purge(List.of(c.getId())).isSuccess());

        assertNull(ctx.materialService().findById(c.getId()), "数据库记录应被删除");
        assertFalse(Files.exists(stored), "磁盘文件也应被移除");
        assertTrue(trash().isEmpty(), "回收站应清空");
        assertTrue(normal().isEmpty());
    }

    @Test
    @DisplayName("E. 回收站计数与首页统计口径一致，分页 total 与列表长度一致")
    void trashCountMatchesStats() {
        upload("回收站测试-丁.pdf", "回收站测试-戊.pdf");
        Material d = find("丁");
        ctx.materialService().softDelete(List.of(d.getId()));

        assertEquals(1, ctx.materialService().listTrashed().size());
        assertEquals(1L, ((Number) ctx.searchService().stats().get("trashed")).longValue());
        Page<Material> page = ctx.searchService().search(new MaterialQuery().setOnlyTrashed(true), 1, 100);
        assertEquals(1, page.total(), "分页 total 应与列表长度一致");
        assertEquals(1, page.items().size());
    }
}
