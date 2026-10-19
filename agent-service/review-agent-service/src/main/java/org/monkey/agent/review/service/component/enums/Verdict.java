package org.monkey.agent.review.service.component.enums;

import lombok.Getter;

/**
 * 审核结果
 *
 * @author Monkey
 */
@Getter
public enum Verdict {
    /**
     * 通过
     */
    APPROVED,
    /**
     * 拒绝
     */
    REJECTED,
    /**
     * 转人工
     */
    ESCALATED
}
