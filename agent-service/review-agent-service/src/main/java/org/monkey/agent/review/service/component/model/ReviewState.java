package org.monkey.agent.review.service.component.model;

import cn.hutool.core.util.StrUtil;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.state.AgentState;
import org.monkey.agent.review.service.component.result.CategoryCheckResult;
import org.monkey.agent.review.service.component.result.ImageReviewResult;
import org.monkey.agent.review.service.component.result.PriceQualResult;
import org.monkey.agent.review.service.component.result.TextReviewResult;
import org.monkey.catalog.api.dto.ProductDTO;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
public class ReviewState extends AgentState {

    // ========== key 常量：相当于 Python TypedDict 的字段名 ==========

    // 1. 输入（invoke 时通过 map 传入）

    public static final String MSG = "msg";
    public static final String PRODUCT_ID = "productId";
    public static final String SELLER_ID = "sellerId";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String IMAGE_URLS = "imageUrls";
    public static final String CATEGORY = "category";
    public static final String PRICE = "price";
    public static final String SUBMIT_TIME = "submitTime";
    public static final String TRACE_ID = "traceId";
    public static final String START_TIME_MS = "startTimeMs";
    public static final String PRODUCT = "product";

    // 2. parse_input 节点产出
    public static final String EXTRACTED_KEYWORDS = "extractedKeywords";
    public static final String IMAGE_URLS_VALID = "imageUrlsValid";

    // 3. 三个并行分支的产出
    public static final String IMAGE_RESULT = "imageResult";
    public static final String TEXT_RESULT = "textResult";
    public static final String CATEGORY_RESULT = "categoryResult";

    // 4. price_qual_review 产出
    public static final String PRICE_QUAL_RESULT = "priceQualResult";

    // 5. aggregate_decision 产出
    public static final String FINAL_DECISION = "finalDecision";
    public static final String FINAL_SCORE = "finalScore";
    public static final String HAS_CRITICAL_ISSUE = "hasCriticalIssue";
    public static final String AGGREGATE_REASON = "aggregateReason";

    /**
     * 构造器签名必须是 Map——langgraph4j 把它当工厂用（ReviewState::new）
     */
    public ReviewState(Map<String, Object> initData) {
        super(initData);
    }

    // ========== 访问器：只有读，没有 setter ==========
    // 输入字段用 orElseThrow：缺了说明图入口拼装出错，属于 bug
    // 中间产出用 orElse(null)：并行分支间本来就互相读不到对方的产出

    public Long productId() {
        return this.<Long>value(PRODUCT_ID).orElseThrow(() -> new IllegalStateException("productId 缺失"));
    }

    public Long sellerId() {
        return this.<Long>value(SELLER_ID).orElseThrow(() -> new IllegalStateException("sellerId 缺失"));
    }

    public String title() {
        return this.<String>value(TITLE).orElse(null);
    }

    public String description() {
        return this.<String>value(DESCRIPTION).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<String> imageUrls() {
        return this.<List<String>>value(IMAGE_URLS).orElse(List.of());
    }

    public String category() {
        return this.<String>value(CATEGORY).orElse(null);
    }

    public BigDecimal price() {
        return this.<BigDecimal>value(PRICE).orElse(null);
    }

    public LocalDateTime submitTime() {
        return this.<LocalDateTime>value(SUBMIT_TIME).orElse(null);
    }

    public ProductDTO product() {
        return this.<ProductDTO>value(PRODUCT).orElse(null);
    }

    public String traceId() {
        return this.<String>value(TRACE_ID).orElse(null);
    }

    @SuppressWarnings("unchecked")
    public List<String> extractedKeywords() {
        return this.<List<String>>value(EXTRACTED_KEYWORDS).orElse(List.of());
    }

    @SuppressWarnings("unchecked")
    public List<String> imageUrlsValid() {
        return this.<List<String>>value(IMAGE_URLS_VALID).orElse(List.of());
    }

    public ImageReviewResult imageResult() {
        return this.<ImageReviewResult>value(IMAGE_RESULT).orElse(null);
    }

    public TextReviewResult textResult() {
        return this.<TextReviewResult>value(TEXT_RESULT).orElse(null);
    }

    public CategoryCheckResult categoryResult() {
        return this.<CategoryCheckResult>value(CATEGORY_RESULT).orElse(null);
    }

    public PriceQualResult priceQualResult() {
        return this.<PriceQualResult>value(PRICE_QUAL_RESULT).orElse(null);
    }

    public ReviewDecision finalDecision() {
        return this.<ReviewDecision>value(FINAL_DECISION).orElse(null);
    }

    public Double finalScore() {
        return this.<Double>value(FINAL_SCORE).orElse(null);
    }

    public Boolean hasCriticalIssue() {
        return this.<Boolean>value(HAS_CRITICAL_ISSUE).orElse(Boolean.FALSE);
    }

    public String aggregateReason() {
        return this.<String>value(AGGREGATE_REASON).orElse(null);
    }

    // ========== 图入口：MQ 消息 → 输入 map ==========

    /**
     * 从 MQ 事件构建图的输入 map——invoke(Map) 的入口是 map，不是 State 对象
     */
    public static Map<String, Object> inputFrom(ProductDTO product) {
        log.info("[State][inputFrom] product: {}", product);
        String traceId = MDC.get("traceId");
        if (Objects.isNull(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        // 用 HashMap 而不是 Map.of：超过 10 个键时 Map.of 不存在对应重载，且 HashMap 允许 null 值
        Map<String, Object> input = new HashMap<>();
        input.put(PRODUCT_ID, product.getId());
        input.put(SELLER_ID, product.getSellerId());
        input.put(TITLE, product.getTitle());
        input.put(DESCRIPTION, product.getDescription());
        input.put(IMAGE_URLS, StrUtil.isBlank(product.getImage()) ? Arrays.asList("") : List.of(product.getImage()));
        input.put(CATEGORY, product.getSkus());
        input.put(PRICE, product.getStatus());
        input.put(SUBMIT_TIME, product.getCreateTime());
        input.put(TRACE_ID, traceId);
        input.put(START_TIME_MS, System.currentTimeMillis());
        input.put(PRODUCT, product);
        return input;
    }
}