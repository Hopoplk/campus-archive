package com.campus.archive.pattern.suggest;

import com.campus.archive.dto.Suggestion;
import com.campus.archive.entity.FileType;

/**
 * 策略实现（默认）：按文件类型与命名模式给出弱建议，并顺带规范显示名。
 *
 * <p>只回答"它大概属于哪一类、该叫什么名字"，不解析文件名语义，因此给出的是低置信度弱建议。
 * 例如手机直出的 IMG_20261012_143022.jpg 会被建议命名为「材料照片 2026-10-12.jpg」。</p>
 */
public class ExtensionPatternSuggestionStrategy implements CategorySuggestionStrategy {

    @Override
    public String name() {
        return "类型与命名模式建议";
    }

    @Override
    public Suggestion suggest(FileMeta meta) {
        String base = meta.baseName();
        String category = guessCategory(meta.fileType());
        String better = normalizeName(base, meta.fileType());

        String reason = "按文件类型推断";
        double confidence = 0.35;

        boolean looksLikeCameraDump = base.matches("(?i)^(img|dsc|screenshot|微信图片|photo)[-_ ].*");
        if (looksLikeCameraDump) {
            reason = "按文件类型推断（疑似相机/截图直出，建议规范命名）";
            confidence = 0.5;
        }

        if (category == null && better == null) return Suggestion.NONE;
        return new Suggestion(category, better, reason, confidence);
    }

    private String guessCategory(FileType type) {
        if (type == null) return null;
        return switch (type) {
            case IMAGE -> "证书";       // 学生手里的图片绝大多数是证书/奖状照片
            case PDF -> "其他";         // PDF 可能是成绩单、论文、证明，无法仅凭类型判断
            default -> null;
        };
    }

    /** 把 IMG_20261012_143022 之类还原成可读的显示名；识别不出规律就返回 null 表示不改名。 */
    private String normalizeName(String base, FileType type) {
        var m = java.util.regex.Pattern.compile("(20\\d{2})[-_]?(\\d{2})[-_]?(\\d{2})").matcher(base);
        if (m.find()) {
            String date = m.group(1) + "-" + m.group(2) + "-" + m.group(3);
            String prefix = switch (type == null ? FileType.OTHER : type) {
                case IMAGE -> "材料照片";
                case PDF -> "材料文档";
                default -> "材料";
            };
            return prefix + " " + date;
        }
        return null;
    }
}
