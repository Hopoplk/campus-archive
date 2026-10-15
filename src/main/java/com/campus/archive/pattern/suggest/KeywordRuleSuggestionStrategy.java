package com.campus.archive.pattern.suggest;

import com.campus.archive.dto.Suggestion;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 策略实现 A：关键词规则建议（系统默认策略）。
 *
 * <p>规则表可在运行时追加，答辩演示时可以现场加一条规则，界面立刻生效，
 * 直观说明"算法族与调用方解耦"这一策略模式的价值。</p>
 */
public class KeywordRuleSuggestionStrategy implements CategorySuggestionStrategy {

    /** 分类名 → 该分类的命中关键词。顺序即匹配优先级。 */
    private final Map<String, List<String>> rules = new LinkedHashMap<>();

    public KeywordRuleSuggestionStrategy() {
        // 注意：规则按"从具体到笼统"排列，先判断是不是竞赛/科研/实习/实践/获奖，
        // 都不是才落到最笼统的"证书"。否则「数学建模大赛获奖证书」会被 "证书" 抢先命中。
        rule("竞赛", "竞赛", "大赛", "比赛", "contest", "建模", "挑战杯", "互联网+", "创新创业", "蓝桥", "acm", "省赛", "国赛");
        rule("科研", "论文", "专利", "软著", "科研", "立项", "课题", "期刊", "项目书");
        rule("实习", "实习", "见习", "intern", "在职", "工作证明");
        rule("实践", "志愿", "实践", "公益", "服务", "支教", "活动", "证明");
        rule("获奖", "奖状", "获奖", "奖学金", "荣誉", "优秀", "三好", "标兵");
        rule("证书", "证书", "四级", "六级", "cet", "普通话", "计算机二级", "资格", "雅思", "托福", "驾照");
    }

    public final void rule(String category, String... keywords) {
        List<String> list = rules.computeIfAbsent(category, k -> new ArrayList<>());
        for (String k : keywords) list.add(k.toLowerCase());
    }

    /** 暴露只读视图，供界面展示"当前生效的规则表"。 */
    public Map<String, List<String>> ruleTable() {
        return Map.copyOf(rules);
    }

    @Override
    public String name() {
        return "关键词规则建议（默认）";
    }

    @Override
    public Suggestion suggest(FileMeta meta) {
        String haystack = meta.lowerName();
        if (meta.textContent() != null && !meta.textContent().isBlank()) {
            haystack = haystack + " " + meta.textContent().toLowerCase();
        }
        for (Map.Entry<String, List<String>> e : rules.entrySet()) {
            for (String kw : e.getValue()) {
                if (haystack.contains(kw)) {
                    return new Suggestion(e.getKey(), null, "文件名/正文命中关键词「" + kw + "」", 0.85);
                }
            }
        }
        return Suggestion.NONE;
    }
}
