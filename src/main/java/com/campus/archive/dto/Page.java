package com.campus.archive.dto;

import java.util.List;

/** 分页结果。 */
public record Page<T>(List<T> items, int total, int page, int size) {

    public static <T> Page<T> of(List<T> items) {
        return new Page<>(items, items.size(), 1, items.size());
    }

    public int totalPages() {
        return size <= 0 ? 1 : Math.max(1, (int) Math.ceil(total / (double) size));
    }

    public boolean isEmpty() {
        return items == null || items.isEmpty();
    }
}
