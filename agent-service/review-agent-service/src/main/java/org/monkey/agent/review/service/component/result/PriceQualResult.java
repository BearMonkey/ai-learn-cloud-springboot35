package org.monkey.agent.review.service.component.result;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.monkey.agent.review.service.component.enums.Verdict;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class PriceQualResult implements Serializable, ReviewResult {
    private Verdict verdict;        // 三态结论
    private boolean qualificationSufficient; // 资质齐全
    private String priceNote;               // 价格异常说明
    private String qualNote;                // 资质缺失说明

    @Override
    public String getReason() {
        // 价格资质节点没有单一 detail 字段：用两个 note 拼成理由
        return "价格: " + priceNote + "; 资质: " + qualNote;
    }

    public static PriceQualResult pass() {
        return new PriceQualResult(Verdict.APPROVED, true, "", "");
    }
}
