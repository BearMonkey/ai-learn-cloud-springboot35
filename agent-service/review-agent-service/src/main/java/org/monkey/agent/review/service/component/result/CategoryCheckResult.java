package org.monkey.agent.review.service.component.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.monkey.agent.review.service.component.enums.Verdict;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
public class CategoryCheckResult implements Serializable, ReviewResult {
    private Verdict verdict;
    private String suggestedCategory;   // LLM 建议的类目（如果不匹配）
    private boolean needQualification;  // 该类目是否需要特殊资质
    private List<String> requiredDocs;  // 需要的资质证件列表
    private String detail;

    @Override
    public String getReason() {
        return detail;
    }

    public static CategoryCheckResult pass() {
        return new CategoryCheckResult(Verdict.APPROVED, null, false, List.of(), "类目匹配");
    }

    public static CategoryCheckResult reject(String suggestedCategory, String detail) {
        return new CategoryCheckResult(Verdict.REJECTED, suggestedCategory, false, List.of(), detail);
    }

    public static CategoryCheckResult escalate(String detail) {
        return new CategoryCheckResult(Verdict.ESCALATED, null, false, List.of(), detail);
    }
}
