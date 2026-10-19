package org.monkey.agent.review.service.component.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.monkey.agent.review.service.component.enums.Verdict;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class TextReviewResult implements Serializable, ReviewResult {
    private Verdict verdict;
    private List<String> bannedWordsHit;   // 命中的违禁词
    private List<String> riskPhrases;      // 风险短语（不一定是违禁词但可疑）
    private String detail;

    @Override
    public String getReason() {
        return detail;
    }

    public static TextReviewResult pass() {
        return new TextReviewResult(Verdict.APPROVED, List.of(), List.of(), "文本审核通过");
    }
}
