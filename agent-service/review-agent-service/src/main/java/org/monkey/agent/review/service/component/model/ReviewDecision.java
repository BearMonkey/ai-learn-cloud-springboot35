package org.monkey.agent.review.service.component.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.monkey.agent.review.service.component.enums.Verdict;

import java.io.Serializable;

@Data
@AllArgsConstructor
public class ReviewDecision implements Serializable {

    private Verdict status;
    private String reason;
    private double score;

    public static ReviewDecision approve(String reason, double score) {
        return new ReviewDecision(Verdict.APPROVED, reason, score);
    }
    public static ReviewDecision reject(String reason) {
        return new ReviewDecision(Verdict.REJECTED, reason, 0);
    }
    public static ReviewDecision escalate(String reason) {
        return new ReviewDecision(Verdict.ESCALATED, reason, 0);
    }
}
