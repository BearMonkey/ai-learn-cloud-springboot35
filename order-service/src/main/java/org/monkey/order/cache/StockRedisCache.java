package org.monkey.order.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Redis 库存缓存操作 —— 下单时 Lua 原子预扣，取消时回补
 *
 * <p>order-service 只需要预扣（下单时）和回补（取消/超时时）功能。
 * 初始化和对账由 inventory-service 负责。
 *
 * @author monkey
 */
@Component
public class StockRedisCache {

    private static final Logger log = LoggerFactory.getLogger(StockRedisCache.class);

    // SKU 级别库存 Key 模板
    private static final String STOCK_KEY_FORMAT = "stock:sku:%d";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> lockScript;    // 预扣 Lua 脚本
    private final DefaultRedisScript<Long> releaseScript; // 回补 Lua 脚本

    @SuppressWarnings("unchecked")
    public StockRedisCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        // 加载预扣 Lua 脚本
        this.lockScript = new DefaultRedisScript<>();
        this.lockScript.setLocation(new ClassPathResource("lua/lock_stock.lua"));
        this.lockScript.setResultType(List.class);

        // 加载回补 Lua 脚本
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setLocation(new ClassPathResource("lua/release_stock.lua"));
        this.releaseScript.setResultType(Long.class);
    }

    /**
     * 预扣库存（Lua 原子操作）
     *
     * @param skuId    SKU ID
     * @param quantity 锁定数量
     * @return LockResult（success=true 表示预扣成功，含剩余可用库存）
     */
    public LockResult lockStock(Long skuId, int quantity) {
        String key = formatKey(skuId);
        List<Long> result = redisTemplate.execute(
                lockScript, Collections.singletonList(key), String.valueOf(quantity));
        if (result == null || result.isEmpty()) {
            log.error("SKU {} 锁定库存 Lua 返回空", skuId);
            return LockResult.fail(-1);
        }
        long code = result.get(0);
        long available = result.size() > 1 ? result.get(1) : -1;
        if (code == 1) {
            log.debug("SKU {} 预扣库存成功，剩余可用 {}", skuId, available);
            return LockResult.ok(available);
        } else {
            log.debug("SKU {} 预扣库存失败，当前可用 {}", skuId, available);
            return LockResult.fail(available);
        }
    }

    /**
     * 回补库存（Lua 原子操作）
     *
     * @param skuId    SKU ID
     * @param quantity 释放数量
     * @return true 回补成功，false 回补失败
     */
    public boolean releaseStock(Long skuId, int quantity) {
        String key = formatKey(skuId);
        Long result = redisTemplate.execute(
                releaseScript, Collections.singletonList(key), String.valueOf(quantity));
        boolean success = result != null && result == 1L;
        if (success) {
            log.debug("SKU {} 回补库存成功，数量 {}", skuId, quantity);
        } else {
            log.warn("SKU {} 回补库存失败，数量 {}", skuId, quantity);
        }
        return success;
    }

    // 组装 Redis Key
    private String formatKey(Long skuId) {
        return String.format(STOCK_KEY_FORMAT, skuId);
    }

    // -------- LockResult --------

    /**
     * 锁定库存结果
     */
    public static class LockResult {
        private final boolean success;
        private final long available;

        private LockResult(boolean success, long available) {
            this.success = success;
            this.available = available;
        }

        public static LockResult ok(long available) {
            return new LockResult(true, available);
        }

        public static LockResult fail(long available) {
            return new LockResult(false, available);
        }

        public boolean isSuccess() { return success; }
        public long getAvailable() { return available; }
    }
}
