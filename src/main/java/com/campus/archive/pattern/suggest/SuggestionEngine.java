package com.campus.archive.pattern.suggest;

import com.campus.archive.dto.Suggestion;

import java.util.List;

/**
 * 【设计模式 ① 策略模式】上下文（Context）。
 *
 * <p>持有当前策略，并把"选择哪个算法"这件事从业务逻辑中抽出来。
 * 策略可随时替换：界面下拉框切换、配置项加载、或将来按材料类型自动选择。</p>
 *
 * <p>注意这里没有工厂、没有 if-else：新增策略时本类完全不需要修改。</p>
 */
public class SuggestionEngine {

    private final List<CategorySuggestionStrategy> available;
    private CategorySuggestionStrategy strategy;

    public SuggestionEngine() {
        this.available = List.of(
                new ExtensionPatternSuggestionStrategy(),
                new NoopSuggestionStrategy());
        this.strategy = available.get(0);
    }

    /** 运行期切换策略。 */
    public void setStrategy(CategorySuggestionStrategy strategy) {
        if (strategy != null) this.strategy = strategy;
    }

    public void setStrategyByName(String name) {
        available.stream()
                .filter(s -> s.name().equals(name))
                .findFirst()
                .ifPresent(this::setStrategy);
    }

    public CategorySuggestionStrategy strategy() {
        return strategy;
    }

    public List<CategorySuggestionStrategy> availableStrategies() {
        return available;
    }

    /** 委托给当前策略。上下文只做转发，不掺业务判断。 */
    public Suggestion suggest(FileMeta meta) {
        Suggestion s = strategy.suggest(meta);
        return s == null ? Suggestion.NONE : s;
    }
}
