//package org.monkey.agent.review.service.impl;
//
//import com.alibaba.fastjson.JSONObject;
//import lombok.extern.slf4j.Slf4j;
//import org.monkey.agent.review.service.ReviewService;
//import org.monkey.agent.review.service.component.ReviewGraphRunner;
//import org.monkey.agent.review.service.component.model.ReviewDecision;
//import org.monkey.agent.review.service.component.model.ReviewState;
//import org.monkey.catalog.api.dto.ProductDTO;
//import org.monkey.catalog.api.enums.ProductStatus;
//import org.monkey.catalog.api.feign.CatalogFeignClient;
//import org.monkey.common.core.dto.Result;
//import org.monkey.common.mq.dto.ProductEventMessage;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//
//import java.util.Objects;
//import java.util.Optional;
//
//@Service
//@Slf4j
//public class ReviewServiceImpl implements ReviewService {
//    @Autowired
//    private ReviewGraphRunner reviewGraphRunner;
//
//    @Autowired
//    private CatalogFeignClient catalogFeignClient;
//
//    @Override
//    public void handleProductAudit(ProductEventMessage msg) {
//        log.info("处理商品审核事件: eventId={}", msg.getEventId());
//        String eventId = msg.getEventId();
//        Long sellerId = msg.getSellerId();
//        Long productId = msg.getProductId();
//
//        // 查询商品信息，校验商品状态
//        log.info("查询商品信息，校验商品状态: productId={}", productId);
//        Result<ProductDTO> productById = catalogFeignClient.getProductById(productId);
//        log.info("查询商品信息，校验商品状态: productId={}, result={}", productId, JSONObject.toJSONString(productById));
//        if (productById == null || productById.getData() == null) {
//            return;
//        }
//        ProductDTO product = productById.getData();
//        if (product == null || product.getStatus() != ProductStatus.ON_REVIEW.getValue()) {
//            log.error("商品状态异常: productId={}, status={}", productId, Objects.isNull(product) ? null : product.getStatus());
//            return;
//        }
//        // 图只做决策
//        Optional<ReviewState> result = reviewGraphRunner.run(product);
//        ReviewDecision decision = result.map(ReviewState::finalDecision).orElse(null);
//        if (decision == null) {
//            log.error("审核图未产出裁决: sellerId={}, productId={}", sellerId, productId);
//            return;
//        }
//
//        // 副作用在图外：按裁决执行
//        switch (decision.getStatus()) {
//            // 审核通过，修改商品状态
//            case APPROVED -> {
//                catalogFeignClient.productAudit(sellerId, productId, ProductStatus.ON_SALE);
//            }
//            case REJECTED -> {
//                catalogFeignClient.productAudit(sellerId, productId, ProductStatus.REJECTED);
//                log.info("商品驳回: sellerId={}, productId={}, reason={}", sellerId, productId, decision.getReason());
//            }
//            case ESCALATED -> {
//                catalogFeignClient.productAudit(sellerId, productId, ProductStatus.HUMAN_REVIEW);
//                log.info("转人工审核: sellerId={}, productId={}, reason={}", sellerId, productId, decision.getReason());
//            }
//        }
//
//        // 中性收尾日志：带裁决状态，驳回/转人工时不误导排查
//        log.info("商品审核处理完成: eventId={}, sellerId={}, productId={}, decision={}", eventId, sellerId, productId, decision.getStatus());
//    }
//}

package org.monkey.agent.review.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.enums.ReviewCallbackStatusEnum;
import org.monkey.agent.review.enums.ReviewDecisionEnum;
import org.monkey.agent.review.model.ProductReviewRecord;
import org.monkey.agent.review.mapper.ProductReviewRecordMapper;
import org.monkey.agent.review.service.ReviewService;
import org.monkey.agent.review.service.component.ReviewGraphRunner;
import org.monkey.agent.review.service.component.model.ReviewDecision;
import org.monkey.agent.review.service.component.model.ReviewState;
import org.monkey.agent.review.service.component.result.ReviewResult;
import org.monkey.catalog.api.dto.ProductDTO;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.monkey.common.core.dto.Result;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

/**
 * 商品审核服务实现：消费简化后的主流程
 *
 * <p>流程：重入检查（eventId）→ 查商品 → 状态守卫（ON_REVIEW）→ 跑图 → 决策先行落库 → 回调 catalog
 *
 * <p>异常分类：技术失败（查商品异常/DB 不可用/图异常）自然上抛 → MQ 重投；
 * 业务失败（回调失败）置 FAILED 后正常返回，恢复全权交给定时补偿任务
 */
@Service
@Slf4j
public class ReviewServiceImpl implements ReviewService {

    @Autowired
    private ProductReviewRecordMapper reviewRecordMapper;

    @Autowired
    private ReviewGraphRunner reviewGraphRunner;

    @Autowired
    private CatalogFeignClient catalogFeignClient;

    @Override
    public void handleProductAudit(ProductEventMessage msg) {
        log.info("处理商品审核事件: eventId={}", msg.getEventId());
        String eventId = msg.getEventId();
        Long productId = msg.getProductId();

        // 1. 重入检查：同一事件已处理过 → 直接返回 ack，恢复由定时补偿负责
        if (existsByEventId(eventId)) {
            log.info("审计记录已存在（MQ 重投），跳过: eventId={}", eventId);
            return;
        }

        // 2. 查询商品信息（Feign 失败是技术失败，异常自然上抛 → MQ 重投）
        Result<ProductDTO> productById = catalogFeignClient.getProductById(productId);
        if (productById == null || productById.getData() == null) {
            log.info("商品不存在，跳过: productId={}", productId);
            return;
        }
        ProductDTO product = productById.getData();

        // 3. 状态守卫：非 ON_REVIEW = 生产者重复事件，跳过
        if (product.getStatus() == null || product.getStatus() != ProductStatus.ON_REVIEW.getValue()) {
            log.info("商品状态非审核中（生产者重复事件），跳过: productId={}, status={}", productId, product.getStatus());
            return;
        }

        // 4. 跑审核图（图只做决策，是幂等纯计算）
        long startMs = System.currentTimeMillis();
        Optional<ReviewState> result = reviewGraphRunner.run(product);
        ReviewDecision decision = result.map(ReviewState::finalDecision).orElse(null);
        if (decision == null) {
            // 聚合节点兜底保证非空，理论不可达
            log.error("审核图未产出裁决: sellerId={}, productId={}", msg.getSellerId(), productId);
            return;
        }
        long graphCostMs = System.currentTimeMillis() - startMs;

        // 5. 决策先行落库（幂等键 uk_event_id；冲突 = 并发同消息，跳过）
        try {
            reviewRecordMapper.insert(buildRecord(msg, product, decision, result.get(), graphCostMs));
        } catch (DuplicateKeyException e) {
            log.info("并发同消息，审计记录已存在，跳过: eventId={}", eventId);
            return;
        }

        // 6. 回调 catalog（业务失败：置 FAILED 后正常返回，不抛异常）
        try {
            catalogFeignClient.productAudit(product.getSellerId(), productId, toProductStatus(decision));
            updateCallbackStatus(eventId, ReviewCallbackStatusEnum.SUCCESS.getCode());
        } catch (FeignException e) {
            log.error("回调 catalog 失败（业务失败，交定时补偿）: eventId={}", eventId, e);
            updateCallbackStatus(eventId, ReviewCallbackStatusEnum.FAILURE.getCode());
        }

        // 中性收尾日志：带裁决状态
        log.info("商品审核处理完成: eventId={}, productId={}, decision={}", eventId, productId, decision.getStatus());
    }

    /**
     * 按事件ID查审计记录是否存在（幂等第一道防线）
     */
    private boolean existsByEventId(String eventId) {
        Long count = reviewRecordMapper.selectCount(new LambdaQueryWrapper<ProductReviewRecord>()
                .eq(ProductReviewRecord::getEventId, eventId));
        return count != null && count > 0;
    }

    /**
     * 组装审计记录：决策先行落库，回调结果由本类/补偿任务后续回写
     */
    private ProductReviewRecord buildRecord(ProductEventMessage msg, ProductDTO product,
                                           ReviewDecision decision, ReviewState state, long graphCostMs) {
        ProductReviewRecord record = new ProductReviewRecord();
        record.setEventId(msg.getEventId());
        record.setProductId(product.getId());
        record.setSellerId(product.getSellerId());
        record.setTraceId(msg.getTraceId());
        record.setDecision(ReviewDecisionEnum.getCodeByValue(decision.getStatus().name()));
        record.setDecisionReason(decision.getReason());
        record.setNodeResults(buildNodeResults(state));
        record.setGraphCostMs(graphCostMs);
        record.setCallbackStatus(ReviewCallbackStatusEnum.PENDING.getCode());
        record.setCallbackRetryCount(0);
        return record;
    }

    /**
     * 四路节点结论拼 JSON：审计追溯与评测归因用
     */
    private String buildNodeResults(ReviewState state) {
        JSONObject nodeResults = new JSONObject();
        putNode(nodeResults, "image", state.imageResult());
        putNode(nodeResults, "text", state.textResult());
        putNode(nodeResults, "category", state.categoryResult());
        putNode(nodeResults, "priceQual", state.priceQualResult());
        return nodeResults.toJSONString();
    }

    /**
     * 单路节点结论写入：null 表示节点异常未产出，按无结论记录
     */
    private void putNode(JSONObject target, String key, ReviewResult nodeResult) {
        JSONObject item = new JSONObject();
        if (nodeResult == null) {
            item.put("verdict", "NO_RESULT");
            item.put("reason", "节点无结果");
        } else {
            item.put("verdict", nodeResult.getVerdict().name());
            item.put("reason", nodeResult.getReason());
        }
        target.put(key, item);
    }

    /**
     * 裁决 → catalog 商品状态：APPROVED→在售，REJECTED→驳回，ESCALATED→人工审核
     */
    private ProductStatus toProductStatus(ReviewDecision decision) {
        return switch (decision.getStatus()) {
            case APPROVED -> ProductStatus.ON_SALE;
            case REJECTED -> ProductStatus.REJECTED;
            case ESCALATED -> ProductStatus.HUMAN_REVIEW;
        };
    }

    /**
     * 回写回调状态（按事件ID定位）
     */
    private void updateCallbackStatus(String eventId, int status) {
        ProductReviewRecord update = new ProductReviewRecord();
        update.setCallbackStatus(status);
        reviewRecordMapper.update(update, new LambdaQueryWrapper<ProductReviewRecord>()
                .eq(ProductReviewRecord::getEventId, eventId));
    }
}