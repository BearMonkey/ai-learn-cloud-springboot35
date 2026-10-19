package org.monkey.agent.review.service.component.node;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.monkey.agent.review.service.component.enums.Verdict;
import org.monkey.agent.review.service.component.model.ReviewDecision;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.ReviewResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 聚合决策节点：合并四个审核节点的三态结论，产出最终 ReviewDecision
 * 合并优先级：REJECTED（硬证据）> ESCALATED（证据不足）> APPROVED（全部通过才通过）
 */
@Component
@Slf4j
public class AggregateDecisionNode implements NodeAction<ReviewState> {

    // 节点结论包装：节点名 + 结论 + 理由——聚合判断和 reason 拼接共用
    @Data
    @AllArgsConstructor
    private static class NodeResult {
        private String nodeName;   // 节点名：图片审核/文本审核/类目审核/价格资质
        private Verdict verdict;   // 该节点三态结论
        private String reason;     // 该节点结论理由
    }

    @Override
    public Map<String, Object> apply(ReviewState state) throws Exception {
        log.info("[Node][AggregateDecisionNode] apply");

        // 收集四个节点的结论：result 为 null = 节点异常没产出，按 ESCALATED 兜底（宁可转人工）
        List<NodeResult> nodes = List.of(
                nodeOf("图片审核", state.imageResult()),
                nodeOf("文本审核", state.textResult()),
                nodeOf("类目审核", state.categoryResult()),
                nodeOf("价格资质", state.priceQualResult()));
        log.info("[Node][AggregateDecisionNode] nodes={}", nodes);

        // 规则 1：存在驳回 → 整体驳回（硬证据优先，即使同时存在转人工项）
        if (hasAny(nodes, Verdict.REJECTED)) {
            return Map.of(ReviewState.FINAL_DECISION,
                    ReviewDecision.reject(buildReason(nodes, Verdict.REJECTED, "驳回")));
        }
        // 规则 2：无驳回但存在转人工 → 整体转人工
        if (hasAny(nodes, Verdict.ESCALATED)) {
            return Map.of(ReviewState.FINAL_DECISION,
                    ReviewDecision.escalate(buildReason(nodes, Verdict.ESCALATED, "转人工")));
        }
        // 规则 3：全部通过
        return Map.of(ReviewState.FINAL_DECISION, ReviewDecision.approve("全部审核通过", 1.0));
    }

    /**
     * 包装节点结论：result 为 null = 节点异常没产出 → 按 ESCALATED 兜底
     */
    private NodeResult nodeOf(String nodeName, ReviewResult result) {
        if (result == null) {
            return new NodeResult(nodeName, Verdict.ESCALATED, "节点无结果");
        }
        return new NodeResult(nodeName, result.getVerdict(), result.getReason());
    }

    /**
     * 是否存在指定结论的节点
     */
    private boolean hasAny(List<NodeResult> nodes, Verdict target) {
        for (NodeResult node : nodes) {
            if (node.getVerdict() == target) {
                return true;
            }
        }
        return false;
    }

    /**
     * 拼接指定结论节点的理由："驳回项: 文本审核(命中违禁词xx); 类目审核(类目不匹配)"
     */
    private String buildReason(List<NodeResult> nodes, Verdict target, String action) {
        List<String> parts = new ArrayList<>();
        for (NodeResult node : nodes) {
            if (node.getVerdict() == target) {
                parts.add(node.getNodeName() + "(" + node.getReason() + ")");
            }
        }
        return action + "项: " + String.join("; ", parts);
    }
}
