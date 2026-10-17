package com.campus.archive.pattern;

import com.campus.archive.dto.Suggestion;
import com.campus.archive.entity.FileType;
import com.campus.archive.pattern.suggest.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 设计模式 ① 策略模式的验证。
 * 重点验证三件事：算法族可互换、上下文可运行期切换、关闭建议等价于空对象。
 */
@DisplayName("设计模式① 策略模式：材料归类建议")
class SuggestionStrategyTest {

    private FileMeta meta(String name, FileType type) {
        return FileMeta.of(name, type, 1024);
    }

    @Test
    @DisplayName("类型与命名模式策略：图片归入证书，并规范化相机直出的文件名")
    void patternStrategySuggestsByFileType() {
        CategorySuggestionStrategy s = new ExtensionPatternSuggestionStrategy();

        Suggestion img = s.suggest(meta("IMG_20261012_143022.jpg", FileType.IMAGE));
        assertEquals("证书", img.categoryName());
        assertNotNull(img.suggestedName());
        assertTrue(img.suggestedName().contains("2026-10-12"));
        assertTrue(img.confidence() < 0.6, "弱建议的置信度应当明显偏低");

        assertEquals("其他", s.suggest(meta("大学英语四级成绩报告单.pdf", FileType.PDF)).categoryName(),
                "PDF 无法仅凭类型判断归属，只会落到最笼统的分类");
    }

    @Test
    @DisplayName("判断不出类型时返回空建议，而不是乱猜")
    void returnsNoneWhenNothingCanBeInferred() {
        Suggestion result = new ExtensionPatternSuggestionStrategy().suggest(meta("abc123.bin", FileType.OTHER));
        assertFalse(result.hasCategory());
        assertTrue(result.isEmpty());
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

        assertEquals(2, engine.availableStrategies().size());
        assertEquals("类型与命名模式建议", engine.strategy().name(), "默认策略应为类型与命名模式建议");
        assertTrue(engine.suggest(input).hasCategory());

        engine.setStrategyByName("关闭智能建议");
        assertTrue(engine.suggest(input).isEmpty());

        // 未知名称不应改变当前策略
        engine.setStrategyByName("不存在的策略");
        assertEquals("关闭智能建议", engine.strategy().name());

        engine.setStrategyByName("类型与命名模式建议");
        assertTrue(engine.suggest(input).hasCategory());
    }
}
