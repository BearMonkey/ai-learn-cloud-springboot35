package org.monkey.agent.review.service.component.node;

import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.monkey.agent.review.service.component.aiservice.TextJudgeService;
import org.monkey.agent.review.service.component.aiservice.TextRuleScreener;
import org.monkey.agent.review.service.component.enums.Verdict;
import org.monkey.agent.review.service.component.llm.LlmInvoker;
import org.monkey.agent.review.service.component.llm.LlmInvokeException;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.TextJudgeResult;
import org.monkey.agent.review.service.component.result.TextReviewResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class TextReviewNode implements NodeAction<ReviewState> {

    // 兜底阈值（与类目链一致）：PASS 方向严（错放行平台担责）、REJECT 方向宽（错杀商家重提可挽回）
    private static final double PASS_CONFIDENCE = 0.9;
    private static final double REJECT_CONFIDENCE = 0.7;

    // 这里注入 langchain4j 的 ChatLanguageModel / AiServices

    @Autowired
    private TextRuleScreener textRuleScreener;

    @Autowired
    private TextJudgeService textJudgeService;

    @Autowired
    private LlmInvoker llmInvoker;

    @Override
    public Map<String, Object> apply(ReviewState state) {
        log.info("[Node][TextReviewNode] apply");
        // 读：走访问器
        String title = state.title();
        String description = state.description();

        // ========== 第一层：规则层（词库 + 正则，零 LLM 成本） ==========
        TextRuleScreener.Hit hit = textRuleScreener.screen(title, description);
        if (hit != null) {
            log.info("[Node][TextReviewNode] rule reject: {}", hit);
            return Map.of(ReviewState.TEXT_RESULT, buildRejectResult(hit));
        }

        // ========== 第二层：LLM 变体识别 ==========
//        TextJudgeResult judge = textJudgeService.judge(title, description, textRuleScreener.bannedWordsRef());
//        log.info("[Node][TextReviewNode] judge: {}", judge);
        TextJudgeResult judge;
        try {
            judge = llmInvoker.invoke("text-judge",
                    () -> textJudgeService.judge(title, description, textRuleScreener.bannedWordsRef()));
        } catch (LlmInvokeException e) {
            // 降级：LLM 服务不可用 → 该路转人工（聚合节点按 ESCALATED 兜底）
            log.error("[Node][TextReviewNode] LLM 服务不可用，降级转人工", e);
            return Map.of(ReviewState.TEXT_RESULT,
                    new TextReviewResult(Verdict.ESCALATED, List.of(), List.of(), "LLM 服务不可用：" + e.getMessage()));
        }
        log.info("[Node][TextReviewNode] judge: {}", judge);

        // ========== 第三层：置信度兜底（裁决规则表） ==========
        // 归一化：verdict 白名单校验，非法值按 ESCALATED 处理
        Verdict verdict = normalizeVerdict(judge.getVerdict());
        // 置信度防御：NaN/越界按 0 处理，自动落到转人工分支
        double confidence = validConfidence(judge.getConfidence());

        // PASS 方向：严格兜底（≥0.9 才通过）
        if (verdict == Verdict.APPROVED && confidence >= PASS_CONFIDENCE) {
            return Map.of(ReviewState.TEXT_RESULT,
                    new TextReviewResult(Verdict.APPROVED, List.of(), judge.getRiskPhrases(), judge.getReason()));
        }
        // REJECT 方向：宽松兜底（≥0.7）+ 必须有变体证据（riskPhrases 非空）
        if (verdict == Verdict.REJECTED && CollUtil.isNotEmpty(judge.getRiskPhrases()) && confidence >= REJECT_CONFIDENCE) {
            // 变体词并入 bannedWordsHit：规则层和 LLM 层的命中证据合并
            return Map.of(ReviewState.TEXT_RESULT,
                    new TextReviewResult(Verdict.REJECTED, judge.getRiskPhrases(), judge.getRiskPhrases(), judge.getReason()));
        }
        // 其余全部转人工：PASS 低置信 / REJECT 证据不足 / ESCALATE / 输出异常
        return Map.of(ReviewState.TEXT_RESULT,
                new TextReviewResult(Verdict.ESCALATED, List.of(), judge.getRiskPhrases(), buildEscalateReason(verdict, judge)));
    }

    /**
     * 规则层驳回结果：bannedWordsHit 放命中词，detail 放驳回文案
     */
    private TextReviewResult buildRejectResult(TextRuleScreener.Hit hit) {
        return new TextReviewResult(Verdict.REJECTED, List.of(hit.getWord()), List.of(), hit.getDetail());
    }

    /**
     * verdict 归一化：LLM 输出白名单校验，非法值按 ESCALATED 处理（包可见 static 便于单测）
     */
    static Verdict normalizeVerdict(String rawVerdict) {
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
        log.warn("[Node][TextReviewNode] LLM 输出非法 verdict: {}", rawVerdict);
        return Verdict.ESCALATED;
    }

    /**
     * 置信度防御：NaN/越界按 0 处理，自动落到转人工分支
     */
    static double validConfidence(double rawConfidence) {
        if (Double.isNaN(rawConfidence) || rawConfidence < 0 || rawConfidence > 1) {
            return 0;
        }
        return rawConfidence;
    }

    /**
     * 转人工理由分流：人工复核台按 reason 前缀分类处理
     */
    private String buildEscalateReason(Verdict verdict, TextJudgeResult judge) {
        if (verdict == Verdict.APPROVED) {
            // 精判说通过但置信度不足
            return "精判置信度不足 " + judge.getConfidence() + "：" + judge.getReason();
        }
        if (verdict == Verdict.REJECTED) {
            // 驳回但说不出变体证据
            return "驳回证据不足：riskPhrases 缺失，LLM 理由：" + judge.getReason();
        }
        // ESCALATED：LLM 自己说无法判断（含输出非法值场景）
        return "LLM 无法判断或输出异常：" + judge.getReason();
    }
}
