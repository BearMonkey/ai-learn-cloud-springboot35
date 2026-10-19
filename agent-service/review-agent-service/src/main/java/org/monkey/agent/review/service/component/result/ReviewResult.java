package org.monkey.agent.review.service.component.result;

import org.monkey.agent.review.service.component.enums.Verdict;

/**
 * 审核节点结果接口：所有审核节点结果统一暴露三态结论 + 理由，供聚合节点统一消费
 * 聚合节点面向接口编程——将来新增审核维度（如资质审核节点）时，聚合逻辑零改动
 */
public interface ReviewResult {

    // 三态结论：APPROVED 通过 / REJECTED 驳回 / ESCALATED 转人工
    Verdict getVerdict();

    // 节点结论理由：给人工复核台看的原文
    String getReason();
}
