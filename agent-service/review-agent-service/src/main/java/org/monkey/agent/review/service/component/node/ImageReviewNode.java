package org.monkey.agent.review.service.component.node;

import lombok.extern.slf4j.Slf4j;
import org.bsc.langgraph4j.action.NodeAction;
import org.monkey.agent.review.service.component.model.ReviewDecision;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.ImageReviewResult;
import org.springframework.stereotype.Component;

import java.util.Map;


@Component
@Slf4j
public class ImageReviewNode implements NodeAction<ReviewState> {
    @Override
    public Map<String, Object> apply(ReviewState state) throws Exception {
        log.info("[Node][ImageReviewNode] apply");
        return Map.of(ReviewState.IMAGE_RESULT, ImageReviewResult.pass());
    }
}
