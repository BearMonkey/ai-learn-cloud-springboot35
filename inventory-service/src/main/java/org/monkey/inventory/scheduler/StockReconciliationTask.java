package org.monkey.inventory.scheduler;

import org.monkey.inventory.cache.StockRedisCache;
import org.monkey.inventory.service.InventoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Redis ↔ DB 库存对账任务
 *
 * <p>每 5 分钟执行一次，按 SKU 聚合对比 Redis 缓存与 DB 库存。
 * 不一致时以 DB 为准校正 Redis。
 *
 * @author monkey
 */
@Component
public class StockReconciliationTask {

    private static final Logger log = LoggerFactory.getLogger(StockReconciliationTask.class);

    private final InventoryRepository inventoryRepository;
    private final StockRedisCache stockRedisCache;

    public StockReconciliationTask(InventoryRepository inventoryRepository,
                                   StockRedisCache stockRedisCache) {
        this.inventoryRepository = inventoryRepository;
        this.stockRedisCache = stockRedisCache;
    }

    /** 每 5 分钟执行对账 */
    @Scheduled(fixedDelay = 300_000)
    public void reconcile() {
        log.info("开始库存对账...");

        int fixed = 0;
        int alert = 0;

        List<Map<String, Object>> dbStocks = inventoryRepository.summaryBySku();
        for (Map<String, Object> row : dbStocks) {
            Long skuId = ((Number) row.get("sku_id")).longValue();
            int dbTotal = ((Number) row.get("total_stock")).intValue();
            int dbLocked = ((Number) row.get("locked_stock")).intValue();
            int dbAvailable = ((Number) row.get("available_stock")).intValue();

            StockRedisCache.StockCacheSnapshot cache = stockRedisCache.getSnapshot(skuId);
            if (cache == null) {
                // Redis 缺失 → 从 DB 重建（默认可用量 = total - locked）
                stockRedisCache.initStock(skuId, dbTotal);
                fixed++;
                continue;
            }

            // 三方对比（total / locked / available）
            if (cache.getTotal() != dbTotal
                    || cache.getLocked() != dbLocked
                    || cache.getAvailable() != dbAvailable) {
                // 不一致 → 以 DB 为准校正 Redis
                stockRedisCache.correctFromDb(skuId, dbTotal, dbLocked, dbAvailable, cache.getVersion());
                log.warn("SKU {} 对账不一致，已校正: Redis(total={},locked={},avail={}) DB(total={},locked={},avail={})",
                        skuId, cache.getTotal(), cache.getLocked(), cache.getAvailable(),
                        dbTotal, dbLocked, dbAvailable);
                alert++;
            }
        }

        log.info("对账完成: DB共{}条, 修复{}条, 告警{}条", dbStocks.size(), fixed, alert);
    }
}
