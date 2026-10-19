package org.monkey.agent.review.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import feign.FeignException;
import lombok.extern.slf4j.Slf4j;
import org.monkey.agent.review.enums.ReviewCallbackStatusEnum;
import org.monkey.agent.review.enums.ReviewDecisionEnum;
import org.monkey.agent.review.model.ProductReviewRecord;
import org.monkey.agent.review.mapper.ProductReviewRecordMapper;
import org.monkey.catalog.api.enums.ProductStatus;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 回调补偿任务：回调恢复的唯一机制
 *
 * <p>每分钟扫描待回调（PENDING）与回调失败（FAILED）且未超重试上限、超过重放窗口的记录，
 * 重放回调：成功置 SUCCESS；失败计数 +1，满 5 次置 ABANDONED 转人工
 *
 * <p>恢复延迟上界约 6 分钟（扫描周期 1 分钟 + 重放窗口 5 分钟），审核业务分钟级容忍度内
 *
 * @author monkey
 */
@Component
@Slf4j
public class CallbackCompensationTask {

    // 最大自动重试次数：满 5 次放弃自动补偿，转人工介入
    private static final int MAX_RETRY_COUNT = 5;

    // 重放窗口（分钟）：刚写入的记录可能回调在途，扫早了会抢跑
    private static final long REPLAY_WINDOW_MINUTES = 5;

    // 单轮扫描上限：避免一次补偿风暴打垮 catalog
    private static final int BATCH_SIZE = 50;

    @Autowired
    private ProductReviewRecordMapper reviewRecordMapper;

    @Autowired
    private CatalogFeignClient catalogFeignClient;

    /**
     * 每分钟执行：扫描待补偿记录并重放回调
     */
    @Scheduled(fixedDelayString = "${review.compensate.interval-ms:60000}")
    public void compensate() {
        // 重放窗口：update_time 早于 5 分钟，避开正在途的回调
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(REPLAY_WINDOW_MINUTES);
        LambdaQueryWrapper<ProductReviewRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(ProductReviewRecord::getCallbackStatus, ReviewCallbackStatusEnum.PENDING, ReviewCallbackStatusEnum.FAILURE)
                .lt(ProductReviewRecord::getCallbackRetryCount, MAX_RETRY_COUNT)
                .lt(ProductReviewRecord::getUpdateTime, cutoff)
                .last("limit " + BATCH_SIZE);
        List<ProductReviewRecord> records = reviewRecordMapper.selectList(wrapper);
        if (records.isEmpty()) {
            return;
        }
        log.info("[Compensate] 待补偿记录数: {}", records.size());
        for (ProductReviewRecord record : records) {
            replayCallback(record);
        }
    }

    /**
     * 重放单条记录的回调：记录自包含（productId/sellerId/decision），直接重放无需判断"该补什么"
     */
    private void replayCallback(ProductReviewRecord record) {
        try {
            catalogFeignClient.productAudit(record.getSellerId(), record.getProductId(), toProductStatus(ReviewDecisionEnum.getValueByCode(record.getDecision())));
            updateRecord(record.getId(), ReviewCallbackStatusEnum.SUCCESS.getCode(), 0);
            log.info("[Compensate] 重放成功: recordId={}, productId={}", record.getId(), record.getProductId());
        } catch (FeignException e) {
            int newCount = record.getCallbackRetryCount() + 1;
            if (newCount >= MAX_RETRY_COUNT) {
                // 放弃自动补偿：置 ABANDONED，error 日志转人工
                updateRecord(record.getId(), ReviewCallbackStatusEnum.ABANDONED.getCode(), newCount);
                log.error("[Compensate] 重试 {} 次仍失败，放弃自动补偿转人工: recordId={}, productId={}",
                        MAX_RETRY_COUNT, record.getId(), record.getProductId(), e);
            } else {
                // 保持 FAILED，下轮继续扫描
                updateRecord(record.getId(), ReviewCallbackStatusEnum.FAILURE.getCode(), newCount);
                log.warn("[Compensate] 重放失败（第 {} 次）: recordId={}", newCount, record.getId(), e);
            }
        }
    }

    /**
     * 裁决字符串 → catalog 商品状态：未知值宁可转人工
     */
    private ProductStatus toProductStatus(String decision) {
        return switch (decision) {
            case "APPROVED" -> ProductStatus.ON_SALE;
            case "REJECTED" -> ProductStatus.REJECTED;
            default -> ProductStatus.HUMAN_REVIEW;
        };
    }

    /**
     * 回写回调状态与重试计数
     */
    private void updateRecord(Long id, int status, int retryCount) {
        ProductReviewRecord update = new ProductReviewRecord();
        update.setId(id);
        update.setCallbackStatus(status);
        update.setCallbackRetryCount(retryCount);
        reviewRecordMapper.updateById(update);
    }
}