package com.campus.archive.pattern;

import com.campus.archive.dto.Suggestion;
import com.campus.archive.entity.FileType;
import com.campus.archive.pattern.suggest.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设计模式 ① 策略模式的验证。
 * 重点验证三件事：算法族可互换、上下文可运行期切换、新增算法不需要改动调用方。
 */
@DisplayName("设计模式① 策略模式：材料归类建议")
class SuggestionStrategyTest {

    private FileMeta meta(String name, FileType type) {
        return FileMeta.of(name, type, 1024);
    }

    @Test
    @DisplayName("关键词规则策略能按文件名给出分类")
    void keywordRuleMatchesCategory() {
        CategorySuggestionStrategy s = new KeywordRuleSuggestionStrategy();

        assertEquals("证书", s.suggest(meta("大学英语四级成绩报告单.pdf", FileType.PDF)).categoryName());
        assertEquals("竞赛", s.suggest(meta("数学建模大赛获奖证书.jpg", FileType.IMAGE)).categoryName());
        assertEquals("实践", s.suggest(meta("志愿服务时长证明.pdf", FileType.PDF)).categoryName());
        assertEquals("科研", s.suggest(meta("科研立项结题报告.docx", FileType.WORD)).categoryName());
    }

    @Test
    @DisplayName("命中不了关键词时返回空建议，而不是乱猜")
    void keywordRuleReturnsNoneWhenNoMatch() {
        CategorySuggestionStrategy s = new KeywordRuleSuggestionStrategy();
        Suggestion result = s.suggest(meta("abc123.bin", FileType.OTHER));
        assertFalse(result.hasCategory());
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("同一份输入换一个策略就换一套结果——算法族可互换")
    void strategiesAreInterchangeable() {
        FileMeta input = meta("IMG_20261012_143022.jpg", FileType.IMAGE);

        Suggestion byKeyword = new KeywordRuleSuggestionStrategy().suggest(input);
        Suggestion byPattern = new ExtensionPatternSuggestionStrategy().suggest(input);

        // 关键词策略认不出相机直出的文件名，类型策略则能给出弱建议 + 规范命名
        assertFalse(byKeyword.hasCategory());
        assertEquals("证书", byPattern.categoryName());
        assertNotNull(byPattern.suggestedName());
        assertTrue(byPattern.suggestedName().contains("2026-10-12"));
        assertTrue(byPattern.confidence() < 0.6, "弱建议的置信度应当明显偏低");
    }

    @Test
    @DisplayName("空对象策略：关闭智能建议时不产生任何副作用")
    void noopStrategyReturnsNone() {
        assertTrue(new NoopSuggestionStrategy().suggest(meta("大学英语四级.pdf", FileType.PDF)).isEmpty());
    }

    @Test
    @DisplayName("上下文可运行期切换策略，且暴露可选项供界面展示")
    void engineSwitchesStrategyAtRuntime() {
        SuggestionEngine engine = new SuggestionEngine();
        FileMeta input = meta("IMG_20261012_143022.jpg", FileType.IMAGE);

        assertEquals(3, engine.availableStrategies().size());
        assertFalse(engine.suggest(input).hasCategory());

        engine.setStrategyByName("类型与命名模式建议");
        assertEquals("类型与命名模式建议", engine.strategy().name());
        assertTrue(engine.suggest(input).hasCategory());

        engine.setStrategyByName("关闭智能建议");
        assertTrue(engine.suggest(input).isEmpty());

        // 未知名称不应改变当前策略
        engine.setStrategyByName("不存在的策略");
        assertEquals("关闭智能建议", engine.strategy().name());
    }

    @Test
    @DisplayName("新增一条关键词规则后立即生效——对扩展开放、对修改关闭")
    void newRuleTakesEffectWithoutTouchingCallers() {
        SuggestionEngine engine = new SuggestionEngine();
        FileMeta input = meta("职业规划认证材料.pdf", FileType.PDF);
        assertFalse(engine.suggest(input).hasCategory());

        // 答辩演示用：现场往规则表里加一条，调用方（MaterialService）一行都不用动
        KeywordRuleSuggestionStrategy keyword = (KeywordRuleSuggestionStrategy)
                engine.availableStrategies().get(0);
        keyword.rule("竞赛", "职业规划");

        assertEquals("竞赛", engine.suggest(input).categoryName());
    }
}
