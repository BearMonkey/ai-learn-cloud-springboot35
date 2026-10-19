package org.monkey.agent.review.service.component.config;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphStateException;
import org.bsc.langgraph4j.StateGraph;
import org.bsc.langgraph4j.action.AsyncNodeAction;
import org.monkey.agent.review.service.component.model.ReviewDecision;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.node.*;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.concurrent.CompletableFuture;


@Configuration
public class ReviewGraphConfig {

    // 审核专用线程池——与业务线程池隔离，并发度按并行分支数起步
    @Bean("reviewExecutor")
    public ThreadPoolTaskExecutor reviewExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("review-agent-");
        // 任务装饰器：提交任务时把父线程的 MDC 上下文拷贝到工作线程
        executor.setTaskDecorator(runnable -> {
            Map<String, String> context = MDC.getCopyOfContextMap();
            return () -> {
                try {
                    if (context != null) {
                        MDC.setContextMap(context);
                    }
                    runnable.run();
                } finally {
                    MDC.clear();   // 线程复用，必须清
                }
            };
        });
        executor.initialize();
        return executor;
    }


    @Bean
    public CompiledGraph<ReviewState> reviewGraph(
            ParseInputNode parseInput,
            ImageReviewNode imageReview,
            TextReviewNode textReview,
            CategoryCheckNode categoryCheck,
            PriceQualNode priceQual,
            AggregateDecisionNode aggregate,
            ApproveNode approve,
            RejectNode reject,
            EscalateNode escalate) throws GraphStateException {

        StateGraph<ReviewState> graph = new StateGraph<>(ReviewState::new);

        // 节点注册
        graph.addNode("parse_input", AsyncNodeAction.node_async(parseInput));
        graph.addNode("image_review", AsyncNodeAction.node_async(imageReview));
        graph.addNode("text_review", AsyncNodeAction.node_async(textReview));
        graph.addNode("category_check", AsyncNodeAction.node_async(categoryCheck));
        graph.addNode("price_qual_review", AsyncNodeAction.node_async(priceQual));
        graph.addNode("aggregate_decision", AsyncNodeAction.node_async(aggregate));
        graph.addNode("approve", AsyncNodeAction.node_async(approve));
        graph.addNode("reject", AsyncNodeAction.node_async(reject));
        graph.addNode("escalate", AsyncNodeAction.node_async(escalate));

        // 边：入口 → parse
        graph.addEdge(StateGraph.START, "parse_input");

        // 并行三条
        graph.addEdge("parse_input", "image_review");
        graph.addEdge("parse_input", "text_review");
        graph.addEdge("parse_input", "category_check");

        // 汇聚
        graph.addEdge("image_review", "price_qual_review");
        graph.addEdge("text_review", "price_qual_review");
        graph.addEdge("category_check", "price_qual_review");

        graph.addEdge("price_qual_review", "aggregate_decision");

        // 条件分支：只读聚合节点的三态结论，不再从 hasCriticalIssue/finalScore 反推
        graph.addConditionalEdges(
                "aggregate_decision",
                state -> {
                    ReviewDecision decision = state.finalDecision();
                    return switch (decision.getStatus()) {
                        case APPROVED -> CompletableFuture.completedFuture("approve");
                        case REJECTED -> CompletableFuture.completedFuture("reject");
                        case ESCALATED -> CompletableFuture.completedFuture("escalate");
                    };
                },
                Map.of(
                        "approve", "approve",
                        "reject", "reject",
                        "escalate", "escalate"
                )
        );

        // 叶子节点 → 结束
        graph.addEdge("approve", StateGraph.END);
        graph.addEdge("reject", StateGraph.END);
        graph.addEdge("escalate", StateGraph.END);

        return graph.compile();  // 编译校验：检查是否有孤立节点、是否所有节点都有出边
    }
}
