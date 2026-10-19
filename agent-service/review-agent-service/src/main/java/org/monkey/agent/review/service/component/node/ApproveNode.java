package org.monkey.agent.review.service.component.node;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Slf4j
public class ApproveNode implements NodeAction<ReviewState> {
    @Override
    public Map<String, Object> apply(ReviewState state) throws Exception {
        log.info("[Node][ApproveNode] apply");
        return Map.of(ReviewState.FINAL_SCORE, 1.0d,
                ReviewState.HAS_CRITICAL_ISSUE, false);
    }
}
