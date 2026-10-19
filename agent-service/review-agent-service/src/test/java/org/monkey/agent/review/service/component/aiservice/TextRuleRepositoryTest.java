package org.monkey.agent.review.service.component.aiservice;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextRuleRepository 单测：验证三个词库文件和正则加载正确
 */
class TextRuleRepositoryTest {

    @Test
    void init_shouldLoadAllRuleFiles() {
        TextRuleRepository repository = new TextRuleRepository();
        repository.init();
        // 违禁品词、极限词、排除上下文、正则都加载到
        assertThat(repository.bannedWords()).contains("枪支弹药", "冰毒");
        assertThat(repository.limitWords()).contains("销量第一", "全网最低");
        assertThat(repository.limitExclusions()).contains("第一章");
        assertThat(repository.sensitivePatterns()).isNotEmpty();
    }

    @Test
    void init_shouldSkipCommentAndBlankLines() {
        TextRuleRepository repository = new TextRuleRepository();
        repository.init();
        // # 开头注释行和空行不算词
        assertThat(repository.bannedWords()).noneMatch(word -> word.startsWith("#"));
        assertThat(repository.bannedWords()).noneMatch(String::isBlank);
    }
}