package org.monkey.agent.review.service.component;

import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.RunnableConfig;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.catalog.api.dto.ProductDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class ReviewGraphRunner {
    @Autowired
    private CompiledGraph<ReviewState> reviewGraph;

    // 并行分支专用线程池：核心线程数 >= 分支数（当前 3）
    @Autowired
    private ThreadPoolTaskExecutor reviewExecutor;

    /**
     * 执行一次商品审核——返回图的最终裁决，副作用调用放在 Service 层
     */
    public Optional<ReviewState> run(ProductDTO product) {
        // 每次 invoke 构造一次 config：给并行节点挂线程池
        RunnableConfig config = RunnableConfig.builder()
                .addParallelNodeExecutor("parse_input", reviewExecutor)
                .build();
        return reviewGraph.invoke(ReviewState.inputFrom(product), config);
    }

}
