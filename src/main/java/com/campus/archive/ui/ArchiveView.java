package com.campus.archive.ui;

import com.campus.archive.config.AppContext;
import com.campus.archive.dto.ExportResult;
import com.campus.archive.dto.MaterialQuery;
import com.campus.archive.dto.Page;
import com.campus.archive.dto.Result;
import com.campus.archive.entity.Category;
import com.campus.archive.entity.FileType;
import com.campus.archive.entity.Material;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 我的档案：分类 / 标签 / 类型组合筛选 + 材料列表 + 生命周期操作。
 * 私密材料在未解锁时一律显示为「🔒 已加密」且不可预览（对应 AC-04）。
 */
public class ArchiveView extends BorderPane implements MainWindow.Refreshable {

    /** 树根「全部分类」的伪 ID，选中它表示不按分类过滤。 */
    private static final String ALL_ID = "__ALL__";
    /** 「回收站」伪 ID。它不是数据库里的真实分类。 */
    private static final String TRASH_ID = "__TRASH__";

    private final AppContext ctx;
    private final TreeView<Category> categoryTree = new TreeView<>();
    private final ComboBox<String> typeFilter = new ComboBox<>();
    private final CheckBox privateOnly = new CheckBox("只看私密材料");
    private final TextField keywordField = new TextField();
    private final TableView<Material> table = new TableView<>();
    private final Label statusLabel = UiKit.label("", "muted");

    // 需要随"当前是不是回收站"切换状态的按钮
    private final Button renameBtn = new Button();
    private final Button moveBtn = new Button();
    private final Button tagBtn = new Button();
    private final Button privateBtn = new Button();
    private final Button restoreBtn = new Button();
    private final Button deleteBtn = new Button();
    private final Button refreshBtn = new Button("刷新");
    private final Button newCatBtn = new Button("新建子分类");
    private final Button renameCatBtn = new Button("重命名分类");
    private final Button deleteCatBtn = new Button("删除分类");

    public ArchiveView(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("content");
        build();
        refresh();
    }

    private void build() {
        // ---------------- 左侧分类树 ----------------
        categoryTree.setPrefWidth(180);
        categoryTree.setShowRoot(true);
        categoryTree.setCellFactory(v -> new TreeCell<>() {
            @Override
            protected void updateItem(Category item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("");
                    return;
                }
                int depth = depthOf(getTreeItem());
                setText(item.getName());
                // 层级徽标：顶层为 0，与上传窗口保持一致
                setGraphic(depth < 0 ? null : UiKit.depthBadge(depth));
                // 回收站那一项用醒目一点的颜色，和真实分类区分开
                setStyle(TRASH_ID.equals(item.getId())
                        ? "-fx-text-fill:#c0392b; -fx-font-weight:bold;" : "");
            }
        });
        categoryTree.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            updateActionState();
            reload();
        });
        installCategoryContextMenu();

        typeFilter.getItems().add("全部类型");
        for (FileType t : FileType.values()) typeFilter.getItems().add(typeName(t));
        typeFilter.getSelectionModel().selectFirst();
        typeFilter.setOnAction(e -> reload());

        privateOnly.setOnAction(e -> reload());

        keywordField.setPromptText("搜索全库文件名、分类或标签…");
        keywordField.setOnAction(e -> reload());

        refreshBtn.getStyleClass().addAll("button", "ghost-button");
        refreshBtn.setOnAction(e -> refresh());

        Button clear = new Button("清除筛选");
        clear.getStyleClass().addAll("button", "ghost-button");
        clear.setOnAction(e -> {
            keywordField.clear();
            typeFilter.getSelectionModel().selectFirst();
            privateOnly.setSelected(false);
            selectTreeNode(ALL_ID);
            reload();
        });

        for (Button b : new Button[]{newCatBtn, renameCatBtn, deleteCatBtn}) {
            b.getStyleClass().addAll("button", "ghost-button");
            b.setMaxWidth(Double.MAX_VALUE);
        }
        newCatBtn.setOnAction(e -> onNewCategory());
        renameCatBtn.setOnAction(e -> onRenameCategory());
        deleteCatBtn.setOnAction(e -> onDeleteCategory());

        // 竖排：分类面板只有 230px 宽，三个按钮横着放会被截断成"重命..."
        VBox catOps = new VBox(6, newCatBtn, renameCatBtn, deleteCatBtn);

        VBox filters = new VBox(10,
                UiKit.label("我的档案", "card-title"),
                UiKit.label("分类（右键可新建/重命名/删除）", "muted"), categoryTree, catOps,
                UiKit.label("类型", "muted"), typeFilter,
                privateOnly, clear);
        filters.getStyleClass().add("card");
        filters.setPrefWidth(230);

        // ---------------- 中间表格 ----------------
        TableColumn<Material, String> nameCol = new TableColumn<>("文件名");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getIsPrivate() ? "🔒 " + c.getValue().getFileName() : c.getValue().getFileName()));
        nameCol.setPrefWidth(260);

        TableColumn<Material, String> catCol = new TableColumn<>("分类");
        catCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCategoryName()));
        catCol.setPrefWidth(70);

        TableColumn<Material, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(typeName(c.getValue().getFileType())));
        typeCol.setPrefWidth(60);

        TableColumn<Material, String> sizeCol = new TableColumn<>("大小");
        sizeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().sizeText()));
        sizeCol.setPrefWidth(70);

        TableColumn<Material, String> tagCol = new TableColumn<>("标签");
        tagCol.setCellValueFactory(c -> new SimpleStringProperty(
                c.getValue().getTagText() == null ? "" : c.getValue().getTagText()));
        tagCol.setPrefWidth(140);

        TableColumn<Material, String> timeCol = new TableColumn<>("上传时间");
        timeCol.setCellValueFactory(c -> new SimpleStringProperty(UiKit.fmt(c.getValue().getCreatedAt())));
        timeCol.setPrefWidth(130);

        table.getColumns().setAll(List.of(nameCol, catCol, typeCol, sizeCol, tagCol, timeCol));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(UiKit.label("没有符合条件的材料", "muted"));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // ---------------- 操作按钮 ----------------
        Button preview = action("预览 / 下载", this::onPreview);
        Button export = action("导出材料", this::onExport);

        renameBtn.setText("重命名");
        renameBtn.getStyleClass().addAll("button", "ghost-button");
        renameBtn.setOnAction(e -> onRename());

        moveBtn.setText("移动分类");
        moveBtn.getStyleClass().addAll("button", "ghost-button");
        moveBtn.setOnAction(e -> onMove());

        tagBtn.setText("编辑标签");
        tagBtn.getStyleClass().addAll("button", "ghost-button");
        tagBtn.setOnAction(e -> onTags());

        privateBtn.setText("设为私密 / 取消");
        privateBtn.getStyleClass().addAll("button", "ghost-button");
        privateBtn.setOnAction(e -> onTogglePrivate());

        restoreBtn.setText("恢复到原位置");
        restoreBtn.getStyleClass().addAll("button", "ghost-button");
        restoreBtn.setOnAction(e -> onRestore());

        deleteBtn.setText("删除");
        deleteBtn.getStyleClass().addAll("button", "ghost-button");
        deleteBtn.setOnAction(e -> onDelete());

        HBox actions = new HBox(8, preview, export, renameBtn, moveBtn, tagBtn, privateBtn, restoreBtn, deleteBtn);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(10, 0, 0, 0));

        HBox searchRow = new HBox(8, keywordField, refreshBtn, statusLabel);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(keywordField, Priority.ALWAYS);

        VBox center = new VBox(10, searchRow, table, actions);
        center.getStyleClass().add("card");
        VBox.setVgrow(table, Priority.ALWAYS);

        setLeft(filters);
        setCenter(center);
        BorderPane.setMargin(filters, new Insets(0, 14, 0, 0));
    }

    private Button action(String text, Runnable r) {
        Button b = new Button(text);
        b.getStyleClass().addAll("button", "ghost-button");
        b.setOnAction(e -> r.run());
        return b;
    }

    private static String typeName(FileType t) {
        if (t == null) return "—";
        return switch (t) {
            case PDF -> "PDF";
            case WORD -> "Word";
            case IMAGE -> "图片";
            case EXCEL -> "表格";
            default -> "其他";
        };
    }

    @Override
    public void refresh() {
        rebuildTree();
        reload();
    }

    /**
     * 重建左侧分类树。结构固定为：
     * <pre>
     *   全部分类
     *   ├── 用户自建/内置的分类（可再嵌套，层级不限）
     *   └── 🗑 回收站 (N)
     * </pre>
     * 重建后尽量保留原来的选中项，避免"新建一个子分类，列表就跳回全部分类"。
     */
    private void rebuildTree() {
        String keepId = selectedNodeId();

        List<Category> flat = ctx.categoryService().listAll();
        Map<String, TreeItem<Category>> nodes = new LinkedHashMap<>();
        for (Category c : flat) {
            nodes.put(c.getId(), new TreeItem<>(c));
        }

        TreeItem<Category> root = new TreeItem<>(new Category(ALL_ID, "全部分类", 0));
        root.setExpanded(true);
        for (Category c : flat) {
            TreeItem<Category> node = nodes.get(c.getId());
            TreeItem<Category> parent = c.isRoot() ? root : nodes.get(c.getParentId());
            // 父节点缺失（数据异常）时挂到根上，保证任何分类都可见
            (parent == null ? root : parent).getChildren().add(node);
        }
        for (TreeItem<Category> node : nodes.values()) node.setExpanded(true);

        int trashCount = ctx.materialService().listTrashed().size();
        root.getChildren().add(new TreeItem<>(new Category(TRASH_ID, "🗑 回收站 (" + trashCount + ")", 999)));

        categoryTree.setRoot(root);
        selectTreeNode(keepId == null ? ALL_ID : keepId);
        updateActionState();
    }

    /** 选中指定 ID 的节点；找不到就退回到「全部分类」。 */
    private void selectTreeNode(String id) {
        TreeItem<Category> root = categoryTree.getRoot();
        if (root == null) return;
        TreeItem<Category> hit = findNode(root, id);
        categoryTree.getSelectionModel().select(hit == null ? root : hit);
    }

    private TreeItem<Category> findNode(TreeItem<Category> node, String id) {
        if (node.getValue() != null && node.getValue().getId().equals(id)) return node;
        for (TreeItem<Category> child : node.getChildren()) {
            TreeItem<Category> hit = findNode(child, id);
            if (hit != null) return hit;
        }
        return null;
    }

    /** 层级编号：根「全部分类」与回收站不参与编号，真实分类从顶层 0 开始。 */
    private static int depthOf(TreeItem<Category> item) {
        if (item == null || item.getValue() == null) return -1;
        String id = item.getValue().getId();
        if (ALL_ID.equals(id) || TRASH_ID.equals(id)) return -1;
        int depth = 0;
        for (TreeItem<Category> p = item.getParent(); p != null && p.getValue() != null; p = p.getParent()) {
            if (!ALL_ID.equals(p.getValue().getId())) depth++;
        }
        return depth;
    }

    private Category selectedCategory() {
        TreeItem<Category> item = categoryTree.getSelectionModel().getSelectedItem();
        return item == null ? null : item.getValue();
    }

    private String selectedNodeId() {
        Category c = selectedCategory();
        return c == null ? null : c.getId();
    }

    /** 当前是否停在"回收站"节点下。 */
    private boolean isTrashView() {
        Category c = selectedCategory();
        return c != null && TRASH_ID.equals(c.getId());
    }

    /** 当前选中的是不是伪节点（全部分类 / 回收站），这类节点不能改名或删除。 */
    private boolean isPseudoNode() {
        Category c = selectedCategory();
        return c == null || ALL_ID.equals(c.getId()) || TRASH_ID.equals(c.getId());
    }

    private void installCategoryContextMenu() {
        MenuItem miNew = new MenuItem("新建子分类");
        miNew.setOnAction(e -> onNewCategory());
        MenuItem miRename = new MenuItem("重命名");
        miRename.setOnAction(e -> onRenameCategory());
        MenuItem miDelete = new MenuItem("删除分类");
        miDelete.setOnAction(e -> onDeleteCategory());

        ContextMenu menu = new ContextMenu(miNew, miRename, miDelete);
        menu.setOnShowing(e -> {
            boolean pseudo = isPseudoNode();
            miNew.setDisable(pseudo);
            miRename.setDisable(pseudo);
            miDelete.setDisable(pseudo);
        });
        categoryTree.setContextMenu(menu);
    }

    /**
     * 按钮状态随当前节点切换：
     * 普通分类下只能"删除"（移入回收站）；回收站下才能"永久删除"和"恢复"，且不允许再编辑。
     */
    private void updateActionState() {
        boolean trash = isTrashView();

        renameBtn.setDisable(trash);
        moveBtn.setDisable(trash);
        tagBtn.setDisable(trash);
        privateBtn.setDisable(trash);

        restoreBtn.setVisible(trash);
        restoreBtn.setManaged(trash);

        deleteBtn.setText(trash ? "永久删除" : "删除");
        deleteBtn.getStyleClass().removeAll("ghost-button", "danger-button");
        deleteBtn.getStyleClass().add(trash ? "danger-button" : "ghost-button");

        boolean pseudo = isPseudoNode();
        renameCatBtn.setDisable(pseudo);
        deleteCatBtn.setDisable(pseudo);
        // 「全部分类」和「回收站」下面不能再挂子分类，避免把伪节点做成真实父节点
        newCatBtn.setDisable(TRASH_ID.equals(selectedNodeId()));
    }

    public void applyKeyword(String kw) {
        keywordField.setText(kw);
        reload();
    }

    private void reload() {
        MaterialQuery q = buildQuery();
        Page<Material> page = ctx.searchService().search(q, 1, 500);
        table.setItems(FXCollections.observableArrayList(page.items()));

        String kw = keywordField.getText().trim();
        if (isTrashView()) {
            statusLabel.setText(kw.isEmpty()
                    ? "回收站内共 " + page.total() + " 项（在此删除将永久销毁）"
                    : "回收站内搜索「" + kw + "」：共 " + page.total() + " 项");
        } else if (!kw.isEmpty()) {
            statusLabel.setText("全库搜索「" + kw + "」：共 " + page.total() + " 条（已忽略分类筛选）");
        } else {
            Category c = selectedCategory();
            String scope = (c == null || ALL_ID.equals(c.getId())) ? "全部分类" : c.getName() + "（含子分类）";
            statusLabel.setText(scope + "：共 " + page.total() + " 条");
        }
    }

    /**
     * 组装查询条件。
     *
     * <p>一条重要规则：<b>只要输入了关键词，就按全库搜索</b>，不再受左侧分类筛选限制——
     * 用户输入关键词时想找的是"这份东西在哪"，而不是"当前分类里有没有"。
     * 回收站是个例外：它本身就是一个明确的范围，在里面搜索仍在回收站内进行。</p>
     */
    private MaterialQuery buildQuery() {
        MaterialQuery q = new MaterialQuery();
        Category c = selectedCategory();
        boolean trash = c != null && TRASH_ID.equals(c.getId());
        boolean hasKeyword = !keywordField.getText().isBlank();

        if (trash) {
            q.setOnlyTrashed(true);   // 排序由 MaterialQuery 默认按删除时间倒序
        } else if (!hasKeyword && c != null && !ALL_ID.equals(c.getId())) {
            // 点父分类也要能看到子分类里的材料，所以按整棵子树筛
            q.setCategoryIds(ctx.categoryService().subtreeIds(c.getId()));
        }

        int ti = typeFilter.getSelectionModel().getSelectedIndex();
        if (ti > 0) q.setFileType(FileType.values()[ti - 1]);
        if (privateOnly.isSelected()) q.setOnlyPrivate(true);
        if (hasKeyword) q.setKeyword(keywordField.getText().trim());
        return q;
    }

    /* ======================== 分类树的增删改 ======================== */

    private void onNewCategory() {
        if (TRASH_ID.equals(selectedNodeId())) {
            UiKit.warn("回收站下不能新建分类");
            return;
        }
        Category parent = selectedCategory();
        boolean topLevel = parent == null || ALL_ID.equals(parent.getId());
        String parentId = topLevel ? null : parent.getId();

        UiKit.prompt("新建分类", topLevel ? "将建在顶层" : "将建在「" + parent.getName() + "」下", "")
                .ifPresent(name -> {
                    Result<Category> r = ctx.categoryService().create(parentId, name);
                    if (r.isSuccess()) {
                        String newId = r.getData().getId();
                        rebuildTree();
                        selectTreeNode(newId);
                        refresh();
                        statusLabel.setText("已新建分类「" + r.getData().getName() + "」");
                    } else {
                        UiKit.error("[" + r.getCode() + "] " + r.getMessage());
                    }
                });
    }

    private void onRenameCategory() {
        Category c = selectedCategory();
        if (c == null || isPseudoNode()) {
            UiKit.warn("请先在左侧选择一个分类");
            return;
        }
        UiKit.prompt("重命名分类", "新名称（不超过 20 个字符）", c.getName())
                .ifPresent(name -> {
                    Result<Void> r = ctx.categoryService().rename(c.getId(), name);
                    if (r.isSuccess()) {
                        rebuildTree();
                        statusLabel.setText("分类已改名为「" + name.trim() + "」");
                    } else {
                        UiKit.error("[" + r.getCode() + "] " + r.getMessage());
                    }
                });
    }

    private void onDeleteCategory() {
        Category c = selectedCategory();
        if (c == null || isPseudoNode()) {
            UiKit.warn("请先在左侧选择一个分类");
            return;
        }

        var counts = ctx.categoryService().counts(c.getId());
        if (counts.children() > 0) {
            UiKit.warn("「" + c.getName() + "」下还有 " + counts.children() + " 个子分类。\n"
                    + "请先删除或移走它们，否则删掉父分类会让子分类无处依附。");
            return;
        }

        String moveHint;
        if (c.isRoot()) {
            moveHint = "其中的材料将移动到「其他」分类。";
        } else {
            Category parent = ctx.repos().query(d -> d.category().findById(c.getParentId()));
            moveHint = "其中的材料将移动到父分类「" + (parent == null ? "其他" : parent.getName()) + "」。";
        }
        String msg = counts.materials() == 0
                ? "确定删除分类「" + c.getName() + "」吗？"
                : "「" + c.getName() + "」下还有 " + counts.materials() + " 份材料。\n" + moveHint + "\n确定删除吗？";
        if (!UiKit.confirm("删除分类", msg)) return;

        Result<Void> r = ctx.categoryService().delete(c.getId());
        if (r.isSuccess()) {
            rebuildTree();
            refresh();
            statusLabel.setText("已删除分类「" + c.getName() + "」");
        } else {
            UiKit.error("[" + r.getCode() + "] " + r.getMessage());
        }
    }

    private List<Material> selected() {
        return table.getSelectionModel().getSelectedItems();
    }

    private Material single() {
        Material m = table.getSelectionModel().getSelectedItem();
        if (m == null) UiKit.warn("请先在列表中选择一条材料");
        return m;
    }

    /* ============================ 操作 ============================ */

    private void onPreview() {
        Material m = single();
        if (m == null) return;
        Result<Path> r = ctx.materialService().materialize(m.getId());
        if (!r.isSuccess()) {
            UiKit.error(r.getMessage());
            return;
        }
        Path path = r.getData();
        if (m.getFileType() == FileType.IMAGE) {
            showImage(path, m.getFileName());
            return;
        }
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(path.toFile());
                return;
            }
        } catch (Exception ignored) {
            // 无桌面环境时退化为提示路径
        }
        UiKit.info("已导出到本地文件：\n" + path);
    }

    private void showImage(Path path, String title) {
        try (var in = Files.newInputStream(path)) {
            ImageView iv = new ImageView(new Image(in));
            iv.setPreserveRatio(true);
            iv.setFitWidth(560);
            Dialog<Void> d = new Dialog<>();
            d.setTitle(title);
            d.getDialogPane().setContent(iv);
            d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
            d.showAndWait();
        } catch (Exception e) {
            UiKit.error("无法预览该图片：" + e.getMessage());
        }
    }

    private void onRename() {
        Material m = single();
        if (m == null) return;
        UiKit.prompt("重命名", "新的显示名（不会改动磁盘文件）", m.getFileName())
                .ifPresent(n -> report(ctx.materialService().rename(m.getId(), n)));
    }

    private void onMove() {
        Material m = single();
        if (m == null) return;
        List<Category> cats = ctx.repos().query(x -> x.category().findAll());
        Category current = cats.stream()
                .filter(c -> c.getId().equals(m.getCategoryId()))
                .findFirst()
                .orElse(cats.get(0));
        ChoiceDialog<Category> d = new ChoiceDialog<>(current, cats);
        d.setTitle("移动分类");
        d.setHeaderText(null);
        d.setContentText("目标分类");
        d.showAndWait().ifPresent(c -> report(ctx.materialService().moveToCategory(m.getId(), c.getId())));
    }

    private void onTags() {
        Material m = single();
        if (m == null) return;
        String current = m.getTagText() == null ? "" : m.getTagText().replace(" / ", ",");
        UiKit.prompt("编辑标签", "多个标签用逗号分隔", current)
                .ifPresent(v -> report(ctx.materialService().setTags(m.getId(), v)));
    }

    private void onTogglePrivate() {
        Material m = single();
        if (m == null) return;

        if (m.getIsPrivate()) {
            if (!UiKit.confirm("取消私密保护", "取消后该材料将恢复为明文存放，其他程序也可能读取到。\n确定继续吗？")) return;
            if (!PrivateUnlockDialog.ensureUnlocked(ctx)) return;
            report(ctx.securityService().unmarkPrivate(m.getId()));
        } else {
            if (!PrivateUnlockDialog.ensureUnlocked(ctx)) return;
            if (!UiKit.confirm("设置私密", "该材料将使用 AES-256-GCM 加密后移入私密存储区，\n"
                    + "未通过安全密钥验证前不可查看。\n确定继续吗？")) return;
            report(ctx.securityService().markPrivate(m.getId()));
        }
        reload();
    }

    /**
     * 同一个「删除」按钮，行为取决于当前停在哪个分类：
     * <ul>
     *   <li>普通分类下：<b>移入回收站</b>——可恢复，因此不打断用户，只在状态栏提示；</li>
     *   <li>回收站分类下：<b>永久删除</b>——不可恢复，必须二次确认。</li>
     * </ul>
     * 这样"能不能永久删除"这件事被收敛到回收站这一个入口，避免在普通分类里误删。
     */
    private void onDelete() {
        List<Material> sel = selected();
        if (sel.isEmpty()) {
            UiKit.warn(isTrashView() ? "请先在列表中选择要永久删除的材料" : "请先在列表中选择要删除的材料");
            return;
        }
        List<String> ids = sel.stream().map(Material::getId).toList();

        if (isTrashView()) {
            if (!UiKit.confirm("永久删除",
                    "这些材料将被彻底销毁，无法恢复，文件也会从磁盘移除。\n共 " + sel.size() + " 项，确定继续吗？")) {
                return;
            }
            Result<?> r = ctx.materialService().purge(ids);
            if (r.isSuccess()) {
                UiKit.info("已永久删除 " + sel.size() + " 项");
            } else {
                UiKit.error("[" + r.getCode() + "] " + r.getMessage());
            }
        } else {
            Result<?> r = ctx.materialService().softDelete(ids);
            if (r.isSuccess()) {
                // 可恢复的操作，用状态栏提示即可，不弹窗打断
                statusLabel.setText("已将 " + sel.size() + " 项移入回收站");
            } else {
                UiKit.error("[" + r.getCode() + "] " + r.getMessage());
            }
        }
        refresh();
    }

    private void onRestore() {
        List<Material> sel = selected();
        if (sel.isEmpty()) {
            UiKit.warn("请先在列表中选择要恢复的材料");
            return;
        }
        Result<?> r = ctx.materialService().restore(sel.stream().map(Material::getId).toList());
        if (r.isSuccess()) {
            statusLabel.setText("已恢复 " + sel.size() + " 项到原分类");
        } else {
            UiKit.error("[" + r.getCode() + "] " + r.getMessage());
        }
        refresh();
    }

    /**
     * 把选中的材料导出成一个压缩包。
     * 压缩包内使用<b>原始显示名</b>，并附带 manifest.json 清单（含 SHA-256 校验和），
     * 这样用户拿到的是一个能直接交出去、也能验证完整性的归档。
     */
    private void onExport() {
        List<Material> sel = selected();
        if (sel.isEmpty()) {
            UiKit.warn("请先在列表中选择要导出的材料");
            return;
        }
        // 选中的材料里可能混有私密材料，先确认私密空间已解锁，避免导出到一半失败
        boolean hasPrivate = sel.stream().anyMatch(m -> Boolean.TRUE.equals(m.getIsPrivate()));
        if (hasPrivate && !PrivateUnlockDialog.ensureUnlocked(ctx)) return;

        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择导出位置");
        File dir = chooser.showDialog(getScene() == null ? null : getScene().getWindow());
        if (dir == null) return;

        Result<ExportResult> r = ctx.exportService()
                .exportSelection(sel.stream().map(Material::getId).toList(), dir.toPath());
        if (!r.isSuccess()) {
            UiKit.error("[" + r.getCode() + "] " + r.getMessage());
            return;
        }
        ExportResult result = r.getData();
        StringBuilder msg = new StringBuilder("已导出 " + result.fileCount() + " 份材料");
        msg.append("\n\n压缩包：").append(result.packagePath().getFileName());
        msg.append("\n保存位置：").append(result.packagePath().getParent());
        msg.append("\n大小：").append(String.format("%.1f KB", result.bytes() / 1024.0));
        msg.append("\n校验码：").append(result.checksum() == null ? "-"
                : result.checksum().substring(0, Math.min(16, result.checksum().length())));
        if (!result.skipped().isEmpty()) {
            msg.append("\n\n⚠ 有 ").append(result.skipped().size()).append(" 项已失效被跳过：");
            msg.append(String.join("、", result.skipped()));
        }
        UiKit.info(msg.toString());

        // 导出后直接帮用户打开所在文件夹，省得自己去找
        try {
            Desktop.getDesktop().open(result.packagePath().getParent().toFile());
        } catch (Exception ignored) {
            // 打不开资源管理器不影响导出结果
        }
    }

    private void report(Result<?> r) {
        if (r.isSuccess()) {
            UiKit.info(r.getMessage());
        } else {
            UiKit.error("[" + r.getCode() + "] " + r.getMessage());
        }
    }
}
