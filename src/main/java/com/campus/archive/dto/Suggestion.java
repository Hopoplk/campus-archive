package com.campus.archive.dto;

/**
 * 归类与命名建议。由策略模式（pattern.suggest 包）产出，对应需求 FR-04。
 *
 * @param categoryName  建议的一级分类名，null 表示不给建议
 * @param suggestedName 建议的显示名，null 表示保持原文件名
 * @param reason        给出建议的依据，展示给用户，用户可自行忽略
 * @param confidence    0~1 的置信度，低于阈值时界面不默认勾选
 */
public record Suggestion(String categoryName, String suggestedName, String reason, double confidence) {

    public static final Suggestion NONE = new Suggestion(null, null, "无建议", 0);

    public boolean hasCategory() {
        return categoryName != null && !categoryName.isBlank();
    }

    public boolean isEmpty() {
        return !hasCategory() && (suggestedName == null || suggestedName.isBlank());
    }

    public String displayText() {
        if (isEmpty()) return "无建议";
        StringBuilder sb = new StringBuilder();
        if (hasCategory()) sb.append("分类：").append(categoryName);
        if (suggestedName != null && !suggestedName.isBlank()) {
            if (sb.length() > 0) sb.append("；");
            sb.append("命名：").append(suggestedName);
        }
        sb.append("（").append(reason).append("）");
        return sb.toString();
    }
}
