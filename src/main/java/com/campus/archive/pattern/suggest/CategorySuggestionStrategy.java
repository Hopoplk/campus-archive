package com.campus.archive.pattern.suggest;

import com.campus.archive.dto.Suggestion;

/**
 * 【设计模式 ① 策略模式 Strategy】归类与命名建议算法族。
 *
 * <p>为什么在这里用策略模式：需求 FR-04 要求"根据文件名、类型及可用内容信息建议材料名称和分类"，
 * 而《需求分析报告》把 OCR、自动分类列为 Could 级增强功能、《架构设计》1.4 节要求它们"只预留扩展接口"。
 * 这意味着"怎么建议"是一个会持续替换、增加、甚至按用户开关关闭的算法族，
 * 正是策略模式的适用场景。</p>
 *
 * <p>收益：MaterialService 只依赖本接口，新增识别算法（例如 OCR 识别）时只需新增一个实现类，
 * 业务层与界面层都不需要改动，符合开闭原则。</p>
 */
public interface CategorySuggestionStrategy {

    /** 策略名称，展示在界面下拉框中，让用户看到"当前用的是哪种识别方式"。 */
    String name();

    /**
     * 给出建议。实现必须是纯函数：不修改入参、不访问数据库、不抛异常，
     * 无法给出建议时返回 {@link Suggestion#NONE}。
     */
    Suggestion suggest(FileMeta meta);
}
