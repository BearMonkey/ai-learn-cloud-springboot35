package org.monkey.agent.review.service.component.node;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.monkey.agent.review.rag.model.CategoryHit;
import org.monkey.agent.review.rag.model.CategoryProfile;
import org.monkey.agent.review.rag.service.CategoryKnowledgeService;
import org.monkey.agent.review.service.component.aiservice.CategoryJudgeService;
import org.monkey.agent.review.service.component.aiservice.CategoryScreener;
import org.monkey.agent.review.service.component.enums.Verdict;
import org.monkey.agent.review.service.component.llm.LlmInvoker;
import org.monkey.agent.review.service.component.llm.LlmInvokeException;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.CategoryCheckResult;
import org.monkey.agent.review.service.component.result.CategoryJudgeResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 类目审核节点：四层混合方案
 * 第一层规则筛查 → 第二层画像覆盖+向量检索 → 第三层 LLM 精判 → 第四层置信度兜底
 */
@Component
@Slf4j
public class CategoryCheckNode implements NodeAction<ReviewState> {

    // 第四层兜底阈值：PASS 方向严（错放行平台担责）、REJECT 方向宽（错杀商家重提可挽回）
    private static final double PASS_CONFIDENCE = 0.9;
    private static final double REJECT_CONFIDENCE = 0.7;

    // 第二层检索放行阈值：向量相似度 > 0.85 才配第二层放行
    private static final double SEARCH_PASS_SCORE = 0.85;

    @Autowired
    private CategoryScreener categoryScreener;

    @Autowired
    private CategoryKnowledgeService categoryKnowledgeService;

    @Autowired
    private CategoryJudgeService categoryJudgeService;

    @Autowired
    private LlmInvoker llmInvoker;

    @Override
    public Map<String, Object> apply(ReviewState state) throws Exception {
        log.info("[Node][CategoryCheckNode] apply");

        // ========== 第一层：规则筛查（只做驳回/转人工，无放行路径） ==========
        List<String> keywords = state.extractedKeywords();
        if (!CollUtil.isEmpty(keywords)) {
            CategoryScreener.Verdict screenVerdict = categoryScreener.screen(keywords, state.product().getCategoryMainId());
            if (screenVerdict == CategoryScreener.Verdict.REJECT) {
                // 群冲突：关键词命中其他主类目群词表——硬证据驳回
                log.info("[Node][CategoryCheckNode] screener reject");
                return Map.of(ReviewState.CATEGORY_RESULT,
                        CategoryCheckResult.reject(null, "关键词命中其他主类目群词表"));
            }
            if (screenVerdict == CategoryScreener.Verdict.ESCALATE) {
                // 高危类目：规则层无权放行，直接转人工
                log.info("[Node][CategoryCheckNode] screener escalate");
                return Map.of(ReviewState.CATEGORY_RESULT,
                        CategoryCheckResult.escalate("高危类目，需人工审核"));
            }
            // UNDECIDED：规则层无结论，继续第二层
        }

        // ========== 第二层：画像覆盖检查 + 向量检索 ==========
        // 所选类目画像未覆盖 → 转人工（检索和精判都无输入依据）
        CategoryProfile profile = categoryKnowledgeService.getProfile(state.product().getCategoryLeafId());
        log.info("[Node][CategoryCheckNode] selected category profile, result={}", JSONObject.toJSONString(profile));
        if (Objects.isNull(profile)) {
            // 转人工理由要区分原因：画像未覆盖 / LLM 无法判断——人工复核台按 reason 分流
            return Map.of(ReviewState.CATEGORY_RESULT,
                    CategoryCheckResult.escalate("类目画像未覆盖 leafId=" + state.product().getCategoryLeafId()));
        }

        // rag 检索：空命中 / top1 不是所选 / 分数不够 → 统一下放第三层精判
        List<CategoryHit> search = categoryKnowledgeService.search(keywords, 5);
        log.info("[Node][CategoryCheckNode] search, result={}", JSONObject.toJSONString(search));
        if (CollUtil.isNotEmpty(search)) {
            CategoryHit top1 = search.get(0);
            // 双层证据才放行：向量强命中 + 与所选类目一致
            if (Objects.equals(top1.getLeafId(), state.product().getCategoryLeafId())
                    && top1.getScore() > SEARCH_PASS_SCORE) {
                log.info("[Node][CategoryCheckNode] selected category in search, result={}", JSONObject.toJSONString(top1));
                return Map.of(ReviewState.CATEGORY_RESULT, CategoryCheckResult.pass());
            }
        }

        // ========== 第三层：LLM 精判（输入：商品原文 + 所选类目画像 + top-k 候选） ==========
        // 空命中时关键词可能提取不准，精判用商品原文 title+description，不依赖关键词
        /*String selectedCategoryText = profile.toLlmJudgeText();
        long startTime = System.currentTimeMillis();
        CategoryJudgeResult judge = categoryJudgeService.judge(
                state.title(), state.description(), selectedCategoryText, JSONObject.toJSONString(search));
        log.info("[Node][CategoryCheckNode] category judge, result={}, cost={}",
                JSONObject.toJSONString(judge), System.currentTimeMillis() - startTime);*/
        String selectedCategoryText = profile.toLlmJudgeText();
        long startTime = System.currentTimeMillis();
        CategoryJudgeResult judge;
        try {
            judge = llmInvoker.invoke("category-judge",
                    () -> categoryJudgeService.judge(state.title(), state.description(), selectedCategoryText, JSONObject.toJSONString(search)));
        } catch (LlmInvokeException e) {
            // 降级：LLM 服务不可用 → 该路转人工
            log.error("[Node][CategoryCheckNode] LLM 服务不可用，降级转人工", e);
            return Map.of(ReviewState.CATEGORY_RESULT, CategoryCheckResult.escalate("LLM 服务不可用：" + e.getMessage()));
        }
        log.info("[Node][CategoryCheckNode] category judge, result={}, cost={}",
                JSONObject.toJSONString(judge), System.currentTimeMillis() - startTime);

        // ========== 第四层：置信度兜底（裁决规则表） ==========
        // 归一化：verdict 白名单校验，非法值按 ESCALATED 处理（LLM 输出不可信）
        Verdict verdict = normalizeVerdict(judge.getVerdict());
        // 置信度防御：NaN/越界按 0 处理，自动落到转人工分支
        double confidence = validConfidence(judge.getConfidence());

        // PASS 方向：严格兜底（≥0.9 才通过）
        if (verdict == Verdict.APPROVED && confidence >= PASS_CONFIDENCE) {
            return Map.of(ReviewState.CATEGORY_RESULT, buildResult(Verdict.APPROVED, judge, profile));
        }
        // REJECT 方向：宽松兜底（≥0.7）+ 必须有建议类目（驳回的硬证据）
        if (verdict == Verdict.REJECTED
                && StrUtil.isNotBlank(judge.getSuggestedCategory())
                && confidence >= REJECT_CONFIDENCE) {
            return Map.of(ReviewState.CATEGORY_RESULT, buildResult(Verdict.REJECTED, judge, profile));
        }
        // 其余全部转人工：PASS 低置信 / REJECT 证据不足 / ESCALATE / 输出异常
        return Map.of(ReviewState.CATEGORY_RESULT,
                CategoryCheckResult.escalate(buildEscalateReason(verdict, judge)));
    }

    /**
     * verdict 归一化：LLM 输出白名单校验，非法值按 ESCALATED 处理
     */
    private Verdict normalizeVerdict(String rawVerdict) {
        if ("PASS".equalsIgnoreCase(rawVerdict)) {
            return Verdict.APPROVED;
        }
        if ("REJECT".equalsIgnoreCase(rawVerdict)) {
            return Verdict.REJECTED;
        }
        if ("ESCALATE".equalsIgnoreCase(rawVerdict)) {
            return Verdict.ESCALATED;
        }
        // 非法值：LLM 输出不可信，宁可转人工
        log.warn("[Node][CategoryCheckNode] LLM 输出非法 verdict: {}", rawVerdict);
        return Verdict.ESCALATED;
    }

    /**
     * 置信度防御：NaN/越界按 0 处理，自动落到转人工分支
     */
    private double validConfidence(double rawConfidence) {
        if (Double.isNaN(rawConfidence) || rawConfidence < 0 || rawConfidence > 1) {
            return 0;
        }
        return rawConfidence;
    }

    /**
     * 转人工理由分流：人工复核台按 reason 前缀分类处理
     */
    private String buildEscalateReason(Verdict verdict, CategoryJudgeResult judge) {
        if (verdict == Verdict.APPROVED) {
            // 精判说通过但置信度不足
            return "精判置信度不足 " + judge.getConfidence() + "：" + judge.getReason();
        }
        if (verdict == Verdict.REJECTED) {
            if (StrUtil.isBlank(judge.getSuggestedCategory())) {
                // 驳回说不出建议类目：证据不足
                return "驳回证据不足：suggestedCategory 缺失，LLM 理由：" + judge.getReason();
            }
            // 有建议类目但置信度不够
            return "驳回置信度不足 " + judge.getConfidence() + "：" + judge.getReason();
        }
        // ESCALATED：LLM 自己说无法判断（含 verdict 非法值场景）
        return "LLM 无法判断或输出异常：" + judge.getReason();
    }

    /**
     * 拼装最终结果：资质字段从画像带出（规则数据，不信任 LLM）
     */
    private CategoryCheckResult buildResult(Verdict verdict, CategoryJudgeResult judge, CategoryProfile profile) {
        return new CategoryCheckResult(
                verdict,                                // 三态结论
                judge.getSuggestedCategory(),           // 建议类目（REJECT 时有值，给商家改类目重提用）
                isNeedQualification(profile),           // 需资质：由画像 qualification 字段判断
                profile.getRequiredDocs(),              // 证件清单：画像数据直接带出
                judge.getReason());                     // 理由：LLM 判断原文
    }

    /**
     * 是否需要特殊资质：画像 qualification 字段是"无需特殊资质"就是不需要
     */
    private boolean isNeedQualification(CategoryProfile profile) {
        String qualification = profile.getQualification();
        return StrUtil.isNotBlank(qualification) && !"无需特殊资质".equals(qualification);
    }
}
