package org.monkey.agent.review.service.component.aiservice;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本审核第一层：规则筛查（词库 + 正则，零 LLM 成本）
 * 处理顺序：违禁品词 → 极限词（先挖排除上下文）→ 敏感信息，任一命中立即短路
 */
@Component
@Slf4j
public class TextRuleScreener {

    // 规则层判定：REJECT=命中硬规则驳回 / UNDECIDED=干净进入 LLM 层
    public enum Verdict { REJECT, UNDECIDED }

    // 命中详情：词库来源 + 命中词 + 驳回文案（人工复核台按 source 分流）
    @Data
    @AllArgsConstructor
    public static class Hit {
        private String source;   // 词库来源：banned 违禁品 / limit 极限词 / sensitive 敏感信息
        private String word;     // 命中的词或正则原文
        private String detail;   // 驳回理由原文
    }

    @Autowired
    private TextRuleRepository repository;

    /**
     * 规则筛查：返回 Hit 表示命中驳回，null 表示干净（进入 LLM 层）
     */
    public Hit screen(String title, String description) {
        String text = title + " " + description;

        // 1. 违禁品词：无条件驳回
        List<String> bannedHits = matchAll(repository.bannedTree(), text);
        if (!bannedHits.isEmpty()) {
            return new Hit("banned", bannedHits.get(0), "含违禁品词: " + bannedHits);
        }

        // 2. 极限词：先挖掉排除上下文（"第一章"里的"第一"是合法用法）
        String cleaned = text;
        for (String exclusion : repository.limitExclusions()) {
            cleaned = cleaned.replace(exclusion, " ");
        }
        List<String> limitHits = matchAll(repository.limitTree(), cleaned);
        if (!limitHits.isEmpty()) {
            return new Hit("limit", limitHits.get(0), "含广告法极限词: " + limitHits);
        }

        // 3. 敏感信息引流：正则
        for (Pattern pattern : repository.sensitivePatterns()) {
            if (pattern.matcher(text).find()) {
                return new Hit("sensitive", pattern.pattern(), "含引流信息: " + pattern.pattern());
            }
        }

        return null;
    }

    /**
     * DFA 匹配（加锁）：WordTree match 内部使用共享状态，并发调用同步——审核并发低，开销可忽略
     */
    private synchronized List<String> matchAll(cn.hutool.dfa.WordTree tree, String text) {
        return tree.matchAll(text);
    }

    /**
     * 违禁词参考文本：LLM 找变体时的对照词库（空格 join）
     */
    public String bannedWordsRef() {
        return String.join(" ", repository.bannedWords());
    }
}
