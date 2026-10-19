package org.monkey.agent.review.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 1=APPROVED/2=REJECTED/3=ESCALATED'
 */
@Getter
@AllArgsConstructor
public enum ReviewDecisionEnum {

    APPROVED(1, "APPROVED"),
    REJECTED(2, "REJECTED"),
    ESCALATED(3, "ESCALATED");

    private final int code;
    private final String value;

    // 根据value获取code
    public static int getCodeByValue(String value) {
        for (ReviewDecisionEnum decision : ReviewDecisionEnum.values()) {
            if (decision.getValue().equals(value)) {
                return decision.getCode();
            }
        }
        throw new IllegalArgumentException("Invalid value: " + value);
    }

    public static String getValueByCode(Integer code) {
        for (ReviewDecisionEnum decision : ReviewDecisionEnum.values()) {
            if (decision.getCode() == code) {
                return decision.getValue();
            }
        }
        throw new IllegalArgumentException("Invalid code: " + code);
    }
}
