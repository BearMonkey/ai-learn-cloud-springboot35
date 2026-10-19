package org.monkey.inventory.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 库存 Redis 缓存操作 —— inventory 侧
 *
 * <p>与 order 侧的 StockRedisCache 不同，inventory 侧负责初始化库存缓存和对账修复。
 * 锁定/回补的 Lua 原子操作在 order 侧。
 *
 * @author monkey
 */
@Component
public class StockRedisCache {

    private static final Logger log = LoggerFactory.getLogger(StockRedisCache.class);

    // SKU 级别库存 Key 模板
    private static final String STOCK_KEY_FORMAT = "stock:sku:%d";

    // Hash 字段名
    private static final String FIELD_TOTAL = "total";
    private static final String FIELD_LOCKED = "locked";
    private static final String FIELD_AVAILABLE = "available";
    private static final String FIELD_VERSION = "version";

    private final StringRedisTemplate redisTemplate;

    public StockRedisCache(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 初始化 SKU 库存缓存
     *
     * @param skuId      SKU ID
     * @param totalStock 总库存
     */
    public void initStock(Long skuId, int totalStock) {
        String key = formatKey(skuId);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_TOTAL, String.valueOf(totalStock));
        fields.put(FIELD_LOCKED, "0");
        fields.put(FIELD_AVAILABLE, String.valueOf(totalStock));
        fields.put(FIELD_VERSION, "0");
        redisTemplate.opsForHash().putAll(key, fields);
        log.debug("初始化库存缓存: skuId={}, total={}", skuId, totalStock);
    }

    /**
     * 获取 SKU 库存缓存快照（用于对账）
     *
     * @param skuId SKU ID
     * @return StockCacheSnapshot，key 不存在则返回 null
     */
    public StockCacheSnapshot getSnapshot(Long skuId) {
        String key = formatKey(skuId);
        Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
        if (fields.isEmpty()) {
            return null;
        }
        int total = parseInt(fields.get(FIELD_TOTAL));
        int locked = parseInt(fields.get(FIELD_LOCKED));
        int available = parseInt(fields.get(FIELD_AVAILABLE));
        int version = parseInt(fields.get(FIELD_VERSION));
        return new StockCacheSnapshot(total, locked, available, version);
    }

    /**
     * 以 DB 数据校正 Redis 缓存（对账修复）
     */
    public void correctFromDb(Long skuId, int total, int locked, int available, int version) {
        String key = formatKey(skuId);
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_TOTAL, String.valueOf(total));
        fields.put(FIELD_LOCKED, String.valueOf(locked));
        fields.put(FIELD_AVAILABLE, String.valueOf(available));
        fields.put(FIELD_VERSION, String.valueOf(version));
        redisTemplate.opsForHash().putAll(key, fields);
        log.info("对账校正: skuId={}, total={}, locked={}, available={}", skuId, total, locked, available);
    }

    // 组装 Redis Key
    private String formatKey(Long skuId) {
        return String.format(STOCK_KEY_FORMAT, skuId);
    }

    // 安全转 int
    private int parseInt(Object val) {
        if (val == null) return 0;
        return Integer.parseInt(String.valueOf(val));
    }

    // -------- StockCacheSnapshot --------

    /** 库存缓存快照 */
    public static class StockCacheSnapshot {
        private final int total;
        private final int locked;
        private final int available;
        private final int version;

        public StockCacheSnapshot(int total, int locked, int available, int version) {
            this.total = total;
            this.locked = locked;
            this.available = available;
            this.version = version;
        }

        public int getTotal() { return total; }
        public int getLocked() { return locked; }
        public int getAvailable() { return available; }
        public int getVersion() { return version; }
    }
}
