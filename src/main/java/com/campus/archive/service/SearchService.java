package com.campus.archive.service;

import com.campus.archive.dto.MaterialQuery;
import com.campus.archive.dto.Page;
import com.campus.archive.entity.Material;

import java.util.List;
import java.util.Map;

/** 检索与统计服务（对应需求 FR-10 ~ FR-13、AC-03）。 */
public interface SearchService {

    Page<Material> search(MaterialQuery query, int page, int size);

    List<Material> recent(int limit);

    /** 统计概览：材料总数、私密数、分类分布、回收站数量。 */
    Map<String, Object> stats();
}
