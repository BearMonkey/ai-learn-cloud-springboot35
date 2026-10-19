package org.monkey.agent.review.service.component.aiservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TextRuleScreener 单测：七个场景覆盖三种规则和两条边界
 */
class TextRuleScreenerTest {

    private TextRuleScreener screener;

    @BeforeEach
    void setUp() {
        // 手动装配：不启动 Spring 上下文，Repository 直接 new + init
        TextRuleRepository repository = new TextRuleRepository();
        repository.init();
        screener = new TextRuleScreener();
        // ReflectionTestUtils：字段注入的 Bean 在单测里手动注入
        ReflectionTestUtils.setField(screener, "repository", repository);
    }

    @Test
    void screen_shouldRejectBannedWord() {
        TextRuleScreener.Hit hit = screener.screen("冰毒出售", "");
        assertThat(hit).isNotNull();
        assertThat(hit.getSource()).isEqualTo("banned");
        assertThat(hit.getDetail()).contains("冰毒");
    }

    @Test
    void screen_shouldNotKillToyGun() {
        // 歧义词"手枪"不在词库——玩具手枪是合法商品，不得误杀
        TextRuleScreener.Hit hit = screener.screen("玩具手枪 红外瞄准 适合3-12岁儿童", "");
        assertThat(hit).isNull();
    }

    @Test
    void screen_shouldRejectLimitWord() {
        TextRuleScreener.Hit hit = screener.screen("全网最低价促销", "");
        assertThat(hit).isNotNull();
        assertThat(hit.getSource()).isEqualTo("limit");
    }

    @Test
    void screen_shouldSkipLimitWordInExclusionContext() {
        // "第一章"是合法用法——排除上下文挖掉后极限词不再命中
        TextRuleScreener.Hit hit = screener.screen("第一章 商品介绍", "");
        assertThat(hit).isNull();
    }

    @Test
    void screen_shouldRejectWechatContact() {
        TextRuleScreener.Hit hit = screener.screen("详情加微信 abc123", "");
        assertThat(hit).isNotNull();
        assertThat(hit.getSource()).isEqualTo("sensitive");
    }

    @Test
    void screen_shouldRejectPhoneNumber() {
        TextRuleScreener.Hit hit = screener.screen("联系 13812345678", "");
        assertThat(hit).isNotNull();
        assertThat(hit.getSource()).isEqualTo("sensitive");
    }

    @Test
    void screen_shouldPassCleanText() {
        TextRuleScreener.Hit hit = screener.screen("宵宫立牌 官方直营", "");
        assertThat(hit).isNull();
    }
}