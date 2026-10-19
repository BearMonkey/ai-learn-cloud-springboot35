package org.monkey.agent.review.service.component.aiservice;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.service.component.model.MainGroup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;

/**
 * 类目筛查
 */
@Component
@Slf4j
public class CategoryScreener {
    public enum Verdict { REJECT, ESCALATE, UNDECIDED }

    @Autowired
    private CategoryRuleRepository categoryRuleRepository;

    @Value("${review.category.sensitive:}")
    private String sensitiveCategorieStr; // 敏感类目名单

    public Verdict screen(List<String> keywords, Integer selectedCategory) {

        log.info("[Node][CategoryScreener][screen] 第一层筛查: keywords={}，selectedCategory={}", keywords, selectedCategory);
        // 高危类目名单 → 转人工（先空表或放一个测试类目）
        List<String> sensitiveCategories = List.of();
        if (StrUtil.isNotBlank(sensitiveCategorieStr)) {
            sensitiveCategories = StrUtil.split(sensitiveCategorieStr, ",");

        }
        if (CollUtil.isNotEmpty(sensitiveCategories)
                && selectedCategory != null
                && sensitiveCategories.contains(String.valueOf(selectedCategory))) {
            return Verdict.ESCALATE;
        }

        // 群冲突：关键词命中其他群词表、所选类目不属于该群 → REJECT
        List<MainGroup> groups = categoryRuleRepository.groups();
        for (MainGroup group : groups) {
            if (Objects.equals(selectedCategory, group.getGroupCode())) {
                continue;   // 自己群不算冲突
            }
            for (String word : group.getKeywords()) {
                if (keywords.contains(word)) {
                    return Verdict.REJECT;
                }
            }
        }

        return Verdict.UNDECIDED;
    }
}
