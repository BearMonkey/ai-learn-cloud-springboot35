package org.monkey.agent.review.service.component.node;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.JSONPObject;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.monkey.agent.review.service.component.aiservice.KeywordExtractor;
import org.monkey.agent.review.service.component.llm.LlmInvoker;
import org.monkey.agent.review.service.component.llm.LlmInvokeException;
import org.monkey.agent.review.service.component.model.keywords;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.PriceQualResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Slf4j
public class ParseInputNode implements NodeAction<ReviewState> {

    @Autowired
    private KeywordExtractor keywordExtractor;

    @Autowired
    private LlmInvoker llmInvoker;


    @Override
    public Map<String, Object> apply(ReviewState state) throws Exception {
        log.info("[Node][ParseInputNode] apply");
        return Map.of(
                ReviewState.IMAGE_URLS_VALID, imageUrlParse(state),
                ReviewState.EXTRACTED_KEYWORDS, parseKeyword(state)
        );
    }

    private List<String> parseKeyword(ReviewState state) {
        List<String> keywords;
        try {
            // LlmInvoker：瞬时错误重试，耗尽抛 LlmInvokeException → 走下方空列表兜底
            keywords result = llmInvoker.invoke("parse-input",
                    () -> keywordExtractor.extract(state.title(), state.description()));
            keywords = result.getKeywords();
        } catch (Exception e) {
            // 兜底：LLM 调用失败不中断图，降级为空列表（后续节点有 orElse 兜底）
            log.warn("[Node][ParseInputNode][parseKeyword] LLM 提词失败，降级为空列表，", e);
            keywords = List.of();
        }
        log.info("[Node][ParseInputNode][parseKeyword] 提取关键词: {}", keywords);
        return keywords;
    }

    /**
     * 解析图片 URL
     */
    private List<String> imageUrlParse(ReviewState state) {
        List<String> valid = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String url : state.imageUrls()) {
            if (url == null || !url.startsWith("http")) {
                continue;
            }
            if (seen.add(url) && valid.size() < 5) {
                valid.add(url);
            }
        }
        log.info("[Node][ParseInputNode][imageUrlParse] 解析图片地址: {}", JSONObject.toJSONString(valid));
        return valid;
    }
}
