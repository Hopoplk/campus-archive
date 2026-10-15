package com.campus.archive.pattern.suggest;

import com.campus.archive.dto.Suggestion;

/**
 * 策略实现 C：不识别。
 *
 * <p>空对象（Null Object）形式的策略，供用户关闭智能建议时使用，
 * 使调用方无需写 if (suggestEnabled) 分支。</p>
 */
public class NoopSuggestionStrategy implements CategorySuggestionStrategy {

    @Override
    public String name() {
        return "关闭智能建议";
    }

    @Override
    public Suggestion suggest(FileMeta meta) {
        return Suggestion.NONE;
    }
}
