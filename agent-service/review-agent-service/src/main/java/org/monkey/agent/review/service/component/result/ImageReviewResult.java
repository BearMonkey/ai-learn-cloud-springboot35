package org.monkey.agent.review.service.component.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.monkey.agent.review.service.component.enums.Verdict;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class ImageReviewResult implements Serializable, ReviewResult {
    private Verdict verdict;
    private List<String> violations;    // 违规描述，如"含敏感图片"
    private String detail;             // LLM 返回的原始分析

    @Override
    public String getReason() {
        return detail;
    }

    public static ImageReviewResult pass() {
        return new ImageReviewResult(Verdict.APPROVED, List.of(), "图片审核通过");
    }
    public static ImageReviewResult reject(List<String> v, String d) {
        return new ImageReviewResult(Verdict.REJECTED, v, d);
    }
    public static ImageReviewResult escalate(List<String> v, String d) {
        return new ImageReviewResult(Verdict.ESCALATED, v, d);
    }
}