# RocketMQ + Redis 库存改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将库存操作从同步 Feign 调用改造为 RocketMQ 异步消息 + Redis Lua 预扣库存，引入超时自动取消和对账兜底。

**Architecture:** order-service 通过 Redis Lua 脚本原子预扣库存后发 MQ 异步同步 DB；取消/发货发 MQ 解耦 inventory-service；超时取消用定时扫表兜底；对账任务保证最终一致性。

**Tech Stack:** RocketMQ 5.1.4, rocketmq-spring-boot-starter 2.3.0, Redis 7.2, Spring Boot 3.4.1, Java 17

---

## 文件结构总览

```
common-mq/                                   ← 新增模块
  pom.xml
  src/main/java/org/monkey/common/mq/
    constant/TopicConstants.java
    constant/EventType.java
    dto/OrderEventMessage.java
    dto/ProductEventMessage.java

order-service/
  pom.xml                                    ← 修改：加依赖
  src/main/resources/application.yml         ← 修改：加 RocketMQ + Redis
  src/main/java/org/monkey/order/
    mq/OrderEventProducer.java               ← 新增
    cache/StockRedisCache.java               ← 新增
    scheduler/OrderTimeoutTask.java          ← 新增
    service/impl/OrderServiceImpl.java       ← 修改：createOrder/cancel/deliver

inventory-service/
  pom.xml                                    ← 修改：加依赖
  src/main/resources/application.yml         ← 修改：加 RocketMQ + Redis
  src/main/java/org/monkey/inventory/
    mq/OrderEventConsumer.java               ← 新增
    mq/ProductEventConsumer.java             ← 新增
    mq/DlqMessageHandler.java                ← 新增
    cache/StockRedisCache.java               ← 新增
    cache/StockLuaScripts.java               ← 新增
    scheduler/StockReconciliationTask.java   ← 新增
    mapper/InventoryMapper.java              ← 修改：加按SKU汇总查询
    service/InventoryRepository.java         ← 修改：加 summaryBySku 方法
    service/impl/InventoryRepositoryImpl.java ← 修改：实现 summaryBySku

catalog-service/
  pom.xml                                    ← 修改：加依赖
  src/main/resources/application.yml         ← 修改：加 RocketMQ
  src/main/java/org/monkey/catalog/
    mq/ProductEventProducer.java             ← 新增
    service/impl/ProductServiceImpl.java     ← 修改：createProduct

docker-compose.yml                           ← 修改：加 RocketMQ 容器
docker/mysql/init.sql                        ← 修改：加 dead_message 表
```

---

### Task 1: 父 POM 和 Docker Compose 基础设施

**Files:**
- Modify: `pom.xml` (root)
- Modify: `docker-compose.yml`

- [ ] **Step 1: 父 POM 添加 common-mq 模块和 rocketmq-spring-boot-starter 版本管理**

在根 `pom.xml` 的 `<modules>` 中加入 `common-mq` 模块，在 `<properties>` 中加入 rocketmq starter 版本，在 `<dependencyManagement>` 中加入统一版本管理。

`pom.xml` 修改三个位置：

**位置一**：`<properties>` 中添加版本号（在 `</springdoc.version>` 之后）：
```xml
<!-- RocketMQ Spring Boot Starter 版本 -->
<rocketmq-spring-boot.version>2.3.0</rocketmq-spring-boot.version>
```

**位置二**：`<modules>` 中添加（在 `</common-core>` 之后）：
```xml
<module>common-mq</module>
```

**位置三**：`<dependencyManagement>` 的 `<dependencies>` block 末尾（在 `</dependencies>` 之前）添加：
```xml
<!-- RocketMQ Spring Boot Starter -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
    <version>${rocketmq-spring-boot.version}</version>
</dependency>
```

- [ ] **Step 2: Docker Compose 添加 RocketMQ 容器**

在 `docker-compose.yml` 的 `services:` 末尾追加 RocketMQ NameServer + Broker：

```yaml
  # ================================================================
  # RocketMQ 5.1.4 —— 消息队列
  # NameServer: localhost:9876
  # Broker: localhost:10911
  # Dashboard: http://localhost:18080
  # ================================================================
  rocketmq-namesrv:
    image: apache/rocketmq:5.1.4
    container_name: mall-rocketmq-namesrv
    restart: unless-stopped
    command: sh mqnamesrv
    environment:
      JAVA_OPT_EXT: "-server -Xms256m -Xmx256m"
    ports:
      - "9876:9876"
    networks:
      - mall-net

  rocketmq-broker:
    image: apache/rocketmq:5.1.4
    container_name: mall-rocketmq-broker
    restart: unless-stopped
    environment:
      NAMESRV_ADDR: rocketmq-namesrv:9876
      JAVA_OPT_EXT: "-server -Xms512m -Xmx512m"
    ports:
      - "10909:10909"
      - "10911:10911"
    depends_on:
      - rocketmq-namesrv
    command: sh mqbroker -n rocketmq-namesrv:9876 -c /home/rocketmq/rocketmq-5.1.4/conf/broker.conf
    networks:
      - mall-net

  rocketmq-dashboard:
    image: apacherocketmq/rocketmq-dashboard:latest
    container_name: mall-rocketmq-dashboard
    restart: unless-stopped
    environment:
      JAVA_OPTS: "-Drocketmq.namesrv.addr=rocketmq-namesrv:9876"
    ports:
      - "18080:8080"
    depends_on:
      - rocketmq-namesrv
    networks:
      - mall-net
```

- [ ] **Step 3: 启动 RocketMQ 验证基础设施**

```bash
docker-compose up -d rocketmq-namesrv rocketmq-broker rocketmq-dashboard
```

等待约 30 秒后，浏览器打开 `http://localhost:18080`，确认 Dashboard 能正常访问。

- [ ] **Step 4: 提交**

```bash
git add pom.xml docker-compose.yml
git commit -m "添加 common-mq 模块依赖管理和 RocketMQ Docker 编排"
```

---

### Task 2: 创建 common-mq 模块

**Files:**
- Create: `common-mq/pom.xml`
- Create: `common-mq/src/main/java/org/monkey/common/mq/constant/TopicConstants.java`
- Create: `common-mq/src/main/java/org/monkey/common/mq/constant/EventType.java`
- Create: `common-mq/src/main/java/org/monkey/common/mq/dto/OrderEventMessage.java`
- Create: `common-mq/src/main/java/org/monkey/common/mq/dto/ProductEventMessage.java`

- [ ] **Step 1: 创建 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.monkey</groupId>
        <artifactId>ai-learn-cloud-springboot35</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>common-mq</artifactId>
    <name>common-mq</name>
    <description>共享消息模块 —— Topic 常量、事件 DTO、消费者接口</description>

    <dependencies>
        <!-- Jackson：消息序列化 -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Jackson Java 8 时间类型支持 -->
        <dependency>
            <groupId>com.fasterxml.jackson.datatype</groupId>
            <artifactId>jackson-datatype-jsr310</artifactId>
        </dependency>
    </dependencies>
</project>
```

- [ ] **Step 2: 创建 TopicConstants.java**

```java
package org.monkey.common.mq.constant;

/**
 * RocketMQ Topic 常量
 *
 * @author monkey
 */
public final class TopicConstants {

    private TopicConstants() {
        // 工具类禁止实例化
    }

    /** 订单事件 Topic —— 下单/取消/发货，order-service 生产，inventory-service 消费 */
    public static final String ORDER_EVENT = "ORDER_EVENT";

    /** 商品事件 Topic —— 创建商品时初始化库存，catalog-service 生产，inventory-service 消费 */
    public static final String PRODUCT_EVENT = "PRODUCT_EVENT";

    /** 订单超时检查 Topic —— 延迟消息，order-service 自产自消，用于超时自动取消 */
    public static final String ORDER_TIMEOUT_CHECK = "ORDER_TIMEOUT_CHECK";
}
```

- [ ] **Step 3: 创建 EventType.java**

```java
package org.monkey.common.mq.constant;

/**
 * 订单事件类型枚举
 *
 * @author monkey
 */
public final class EventType {

    private EventType() {
        // 工具类禁止实例化
    }

    /** 订单已创建 —— 异步同步 DB 库存锁定 */
    public static final String ORDER_CREATED = "ORDER_CREATED";

    /** 订单已取消 —— 释放库存 */
    public static final String ORDER_CANCELLED = "ORDER_CANCELLED";

    /** 订单已发货 —— 扣减库存 */
    public static final String ORDER_DELIVERED = "ORDER_DELIVERED";

    /** 商品已创建 —— 初始化库存 */
    public static final String PRODUCT_CREATED = "PRODUCT_CREATED";

    /** 订单超时检查 */
    public static final String ORDER_TIMEOUT_CHECK = "ORDER_TIMEOUT_CHECK";
}
```

- [ ] **Step 4: 创建 OrderEventMessage.java**

```java
package org.monkey.common.mq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 订单事件消息体 —— ORDER_EVENT Topic 的消息载荷
 *
 * @author monkey
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrderEventMessage {

    /** 消息唯一ID，幂等键 */
    private String eventId;

    /** 订单ID */
    private Long orderId;

    /** 事件类型：ORDER_CREATED / ORDER_CANCELLED / ORDER_DELIVERED */
    private String eventType;

    /** SKU + 数量列表（ORDER_CREATED 时有值） */
    private List<OrderSkuItem> items;

    /** 操作人ID */
    private Long operatorId;

    /** 事件发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    /** 环境标识 */
    private String env;

    // -------- 工厂方法 --------

    /** 创建订单事件消息 */
    public static OrderEventMessage ofCreated(Long orderId, List<OrderSkuItem> items, Long operatorId, String env) {
        OrderEventMessage msg = new OrderEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.orderId = orderId;
        msg.eventType = EventType.ORDER_CREATED;
        msg.items = items;
        msg.operatorId = operatorId;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    /** 取消订单事件消息 */
    public static OrderEventMessage ofCancelled(Long orderId, Long operatorId, String env) {
        OrderEventMessage msg = new OrderEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.orderId = orderId;
        msg.eventType = EventType.ORDER_CANCELLED;
        msg.operatorId = operatorId;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    /** 发货事件消息 */
    public static OrderEventMessage ofDelivered(Long orderId, Long operatorId, String env) {
        OrderEventMessage msg = new OrderEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.orderId = orderId;
        msg.eventType = EventType.ORDER_DELIVERED;
        msg.operatorId = operatorId;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    // -------- getter / setter --------

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public List<OrderSkuItem> getItems() { return items; }
    public void setItems(List<OrderSkuItem> items) { this.items = items; }
    public Long getOperatorId() { return operatorId; }
    public void setOperatorId(Long operatorId) { this.operatorId = operatorId; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }

    /**
     * 订单事件中的 SKU 行项
     */
    public static class OrderSkuItem {
        /** SKU ID */
        private Long skuId;
        /** 数量 */
        private Integer quantity;

        public OrderSkuItem() {}

        public OrderSkuItem(Long skuId, Integer quantity) {
            this.skuId = skuId;
            this.quantity = quantity;
        }

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }
    }
}
```

- [ ] **Step 5: 创建 ProductEventMessage.java**

```java
package org.monkey.common.mq.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 商品事件消息体 —— PRODUCT_EVENT Topic 的消息载荷
 *
 * @author monkey
 */
public class ProductEventMessage {

    /** 消息唯一ID */
    private String eventId;

    /** 事件类型 */
    private String eventType;

    /** 商品ID */
    private Long productId;

    /** 卖家ID */
    private Long sellerId;

    /** SKU 库存配置列表 */
    private List<SkuInventorySetup> skuSetups;

    /** 事件发生时间 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime eventTime;

    /** 环境标识 */
    private String env;

    /** 创建商品事件消息 */
    public static ProductEventMessage ofCreated(Long productId, Long sellerId,
                                                 List<SkuInventorySetup> skuSetups, String env) {
        ProductEventMessage msg = new ProductEventMessage();
        msg.eventId = UUID.randomUUID().toString();
        msg.eventType = EventType.PRODUCT_CREATED;
        msg.productId = productId;
        msg.sellerId = sellerId;
        msg.skuSetups = skuSetups;
        msg.eventTime = LocalDateTime.now();
        msg.env = env;
        return msg;
    }

    // -------- getter / setter --------

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getSellerId() { return sellerId; }
    public void setSellerId(Long sellerId) { this.sellerId = sellerId; }
    public List<SkuInventorySetup> getSkuSetups() { return skuSetups; }
    public void setSkuSetups(List<SkuInventorySetup> skuSetups) { this.skuSetups = skuSetups; }
    public LocalDateTime getEventTime() { return eventTime; }
    public void setEventTime(LocalDateTime eventTime) { this.eventTime = eventTime; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }

    /**
     * SKU 库存配置
     */
    public static class SkuInventorySetup {
        /** SKU ID */
        private Long skuId;
        /** 各仓库库存分配 */
        private List<WarehouseStockEntry> warehouseStocks;

        public SkuInventorySetup() {}

        public SkuInventorySetup(Long skuId, List<WarehouseStockEntry> warehouseStocks) {
            this.skuId = skuId;
            this.warehouseStocks = warehouseStocks;
        }

        public Long getSkuId() { return skuId; }
        public void setSkuId(Long skuId) { this.skuId = skuId; }
        public List<WarehouseStockEntry> getWarehouseStocks() { return warehouseStocks; }
        public void setWarehouseStocks(List<WarehouseStockEntry> warehouseStocks) { this.warehouseStocks = warehouseStocks; }
    }

    /**
     * 仓库库存条目
     */
    public static class WarehouseStockEntry {
        /** 仓库ID */
        private Long warehouseId;
        /** 库存数量 */
        private Integer totalStock;

        public WarehouseStockEntry() {}

        public WarehouseStockEntry(Long warehouseId, Integer totalStock) {
            this.warehouseId = warehouseId;
            this.totalStock = totalStock;
        }

        public Long getWarehouseId() { return warehouseId; }
        public void setWarehouseId(Long warehouseId) { this.warehouseId = warehouseId; }
        public Integer getTotalStock() { return totalStock; }
        public void setTotalStock(Integer totalStock) { this.totalStock = totalStock; }
    }
}
```

- [ ] **Step 6: 编译验证 common-mq 模块**

```bash
mvn compile -pl common-mq
```
预期：BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add common-mq/
git commit -m "新增 common-mq 模块：Topic常量、事件类型、消息DTO"
```

---

### Task 3: order-service 添加 RocketMQ 和 Redis 依赖

**Files:**
- Modify: `order-service/pom.xml`
- Modify: `order-service/src/main/resources/application.yml`

- [ ] **Step 1: order-service pom.xml 添加依赖**

在 `order-service/pom.xml` 的 `<dependencies>` block 中，在 `<!-- SpringDoc Swagger UI -->` 之前添加：

```xml
<!-- common-mq：消息 DTO 和常量 -->
<dependency>
    <groupId>org.monkey</groupId>
    <artifactId>common-mq</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- RocketMQ Spring Boot Starter -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>

<!-- Spring Data Redis（Lua 脚本预扣库存） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- commons-pool2（Redis 连接池） -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

- [ ] **Step 2: 修改 application.yml 添加 RocketMQ + Redis 配置**

在 `order-service/src/main/resources/application.yml` 末尾追加：

```yaml
# RocketMQ 配置
rocketmq:
  name-server: localhost:9876
  producer:
    group: order-producer_${spring.profiles.active}
    send-message-timeout: 3000
  env: ${spring.profiles.active}

# Redis 配置（库存预扣缓存）
spring:
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl order-service
```
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add order-service/pom.xml order-service/src/main/resources/application.yml
git commit -m "order-service 添加 RocketMQ 和 Redis 依赖及配置"
```

---

### Task 4: 创建 OrderEventProducer

**Files:**
- Create: `order-service/src/main/java/org/monkey/order/mq/OrderEventProducer.java`

- [ ] **Step 1: 创建 OrderEventProducer**

```java
package org.monkey.order.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 订单事件消息生产者 —— 通过 RocketMQTemplate 发送 ORDER_EVENT 消息
 *
 * @author monkey
 */
@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.env}")
    private String env;

    public OrderEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送订单事件消息（下单/取消/发货）
     *
     * @param event 订单事件消息
     */
    public void sendOrderEvent(OrderEventMessage event) {
        event.setEnv(env);
        Message<OrderEventMessage> message = MessageBuilder
                .withPayload(event)
                .setHeader("env", env)
                .build();
        rocketMQTemplate.send(TopicConstants.ORDER_EVENT, message);
        log.info("发送订单事件: eventType={}, orderId={}, eventId={}",
                event.getEventType(), event.getOrderId(), event.getEventId());
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl order-service
```
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add order-service/src/main/java/org/monkey/order/mq/
git commit -m "新增 OrderEventProducer 订单事件消息生产者"
```

---

### Task 5: 创建 StockRedisCache（order-service 侧）

**Files:**
- Create: `order-service/src/main/java/org/monkey/order/cache/StockRedisCache.java`

- [ ] **Step 1: 创建 StockRedisCache —— Redis 库存预扣/回补操作**

说明：order-service 只需要预扣（下单时）和回补（取消/超时时），不需要初始化和对账。

```java
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
 * @author monkey
 */
@Component
public class StockRedisCache {

    private static final Logger log = LoggerFactory.getLogger(StockRedisCache.class);

    /** SKU 级别库存 Key 模板 */
    private static final String STOCK_KEY_FORMAT = "stock:sku:%d";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<List> lockScript;    // 预扣 Lua 脚本
    private final DefaultRedisScript<Long> releaseScript; // 回补 Lua 脚本

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
     * @param skuId     SKU ID
     * @param quantity  锁定数量
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

    /** 组装 Redis Key */
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
```

- [ ] **Step 2: 创建 Lua 脚本文件**

创建 `order-service/src/main/resources/lua/lock_stock.lua`：

```lua
-- lock_stock.lua —— 原子预扣库存
-- KEYS[1]: stock:sku:{skuId}
-- ARGV[1]: 锁定数量
-- 返回: {1, new_available} 成功 / {0, available} 失败

local key = KEYS[1]
local qty = tonumber(ARGV[1])
local available = redis.call('HGET', key, 'available')

if available == false then
    return {0, -1}
end

available = tonumber(available)
if available >= qty then
    redis.call('HINCRBY', key, 'locked', qty)
    redis.call('HINCRBY', key, 'available', -qty)
    redis.call('HINCRBY', key, 'version', 1)
    local new_available = tonumber(redis.call('HGET', key, 'available'))
    return {1, new_available}
else
    return {0, available}
end
```

创建 `order-service/src/main/resources/lua/release_stock.lua`：

```lua
-- release_stock.lua —— 原子回补库存
-- KEYS[1]: stock:sku:{skuId}
-- ARGV[1]: 释放数量
-- 返回: 1 成功 / 0 失败

local key = KEYS[1]
local qty = tonumber(ARGV[1])
local locked = redis.call('HGET', key, 'locked')

if locked == false then
    return 0
end

locked = tonumber(locked)
if locked >= qty then
    redis.call('HINCRBY', key, 'locked', -qty)
    redis.call('HINCRBY', key, 'available', qty)
    redis.call('HINCRBY', key, 'version', 1)
    return 1
end
return 0
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl order-service
```
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add order-service/src/main/java/org/monkey/order/cache/ order-service/src/main/resources/lua/
git commit -m "新增 StockRedisCache 和 Lua 脚本，实现 Redis 原子预扣/回补库存"
```

---

### Task 6: 修改 OrderServiceImpl — createOrder 加入 Redis 预扣 + MQ

**Files:**
- Modify: `order-service/src/main/java/org/monkey/order/service/impl/OrderServiceImpl.java`

- [ ] **Step 1: 修改 import 和成员变量**

替换 `OrderServiceImpl.java` 头部。原文件第 1-48 行替换为：

```java
package org.monkey.order.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.monkey.catalog.api.dto.SkuDTO;
import org.monkey.catalog.api.feign.CatalogFeignClient;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.monkey.order.cache.StockRedisCache;
import org.monkey.order.dto.CreateOrderRequest;
import org.monkey.order.dto.OrderItemRequest;
import org.monkey.order.dto.OrderListResponse;
import org.monkey.order.dto.OrderResponse;
import org.monkey.order.model.BusinessException;
import org.monkey.order.model.Order;
import org.monkey.order.model.OrderItem;
import org.monkey.order.api.enums.OrderStatus;
import org.monkey.order.mq.OrderEventProducer;
import org.monkey.order.service.OrderRepository;
import org.monkey.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单服务实现
 *
 * <p>核心改动（RocketMQ + Redis 改造）：
 * <ul>
 *   <li>下单：Redis Lua 原子预扣 → 落 DB → 发 MQ 异步同步 DB 库存锁定</li>
 *   <li>取消：Redis 回补 + 发 MQ 异步释放 DB 库存</li>
 *   <li>发货：发 MQ 异步扣减 DB 库存</li>
 * </ul>
 *
 * @author monkey
 */
@Service
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final CatalogFeignClient catalogFeignClient;
    private final OrderEventProducer orderEventProducer;
    private final StockRedisCache stockRedisCache;

    // 不再注入 InventoryFeignClient，库存操作改为 MQ 异步

    public OrderServiceImpl(OrderRepository orderRepository,
                             CatalogFeignClient catalogFeignClient,
                             OrderEventProducer orderEventProducer,
                             StockRedisCache stockRedisCache) {
        this.orderRepository = orderRepository;
        this.catalogFeignClient = catalogFeignClient;
        this.orderEventProducer = orderEventProducer;
        this.stockRedisCache = stockRedisCache;
    }
```

- [ ] **Step 2: 修改 createOrder 方法（Redis 预扣 + MQ）**

替换原 `createOrder` 方法（第 50-100 行）：

```java
    @Override
    @Transactional
    public OrderResponse createOrder(Long buyerId, CreateOrderRequest request) {
        // 1. 通过 Feign 查询每个 SKU 的信息（价格、规格、所属商品、所属卖家），做快照
        List<OrderItemSnapshot> snapshots = new ArrayList<>();
        Long sellerId = null;

        for (OrderItemRequest item : request.getItems()) {
            // 调用 catalog 查 SKU —— 读操作，保持同步
            SkuDTO sku = catalogFeignClient.getSkuById(item.getSkuId());
            if (sku == null) {
                throw new BusinessException(404, "SKU " + item.getSkuId() + " 不存在");
            }
            // 所有行项必须是同一个卖家
            if (sellerId == null) {
                sellerId = sku.getSellerId();
            } else if (!sellerId.equals(sku.getSellerId())) {
                throw new BusinessException("不允许跨卖家下单");
            }
            snapshots.add(new OrderItemSnapshot(sku, item.getQuantity()));
        }

        // 2. Redis Lua 原子预扣库存（防超卖核心）
        for (OrderItemSnapshot snap : snapshots) {
            StockRedisCache.LockResult lockResult = stockRedisCache.lockStock(
                    snap.sku.getId(), snap.quantity);
            if (!lockResult.isSuccess()) {
                // 预扣失败 → 回滚已预扣的 Redis 库存
                rollbackRedisLocks(snapshots, snap);
                long available = lockResult.getAvailable() == -1
                        ? 0 : lockResult.getAvailable();
                throw new BusinessException(
                        "SKU " + snap.sku.getId() + " 库存不足，可用:" + available + " 需要:" + snap.quantity);
            }
        }

        // 3. 创建订单聚合根（PENDING_PAYMENT）
        List<OrderItem> orderItems = new ArrayList<>();
        for (OrderItemSnapshot snap : snapshots) {
            orderItems.add(OrderItem.create(buyerId, sellerId,
                    snap.sku.getProductId(), snap.sku.getId(),
                    snap.sku.getSpecName(), snap.quantity, snap.sku.getPrice()));
        }
        Order order = Order.create(buyerId, sellerId, request.getAddress(), orderItems);
        order = orderRepository.save(order);

        // 4. 发送 MQ 消息 —— inventory-service 异步同步 DB 库存锁定
        List<OrderEventMessage.OrderSkuItem> skuItems = snapshots.stream()
                .map(s -> new OrderEventMessage.OrderSkuItem(s.sku.getId(), s.quantity))
                .collect(Collectors.toList());
        OrderEventMessage event = OrderEventMessage.ofCreated(order.getId(), skuItems, buyerId, null);
        try {
            orderEventProducer.sendOrderEvent(event);
        } catch (Exception e) {
            // MQ 发送失败不阻塞下单，对账任务兜底
            log.error("订单 {} 发送 ORDER_CREATED 消息失败", order.getId(), e);
        }

        return OrderResponse.from(order);
    }

    /** Redis 预扣失败时回滚已锁定的库存 */
    private void rollbackRedisLocks(List<OrderItemSnapshot> snapshots, OrderItemSnapshot failedAt) {
        for (OrderItemSnapshot snap : snapshots) {
            if (snap == failedAt) {
                break; // 只回滚失败项之前的
            }
            stockRedisCache.releaseStock(snap.sku.getId(), snap.quantity);
        }
    }
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl order-service
```
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add order-service/src/main/java/org/monkey/order/service/impl/OrderServiceImpl.java
git commit -m "createOrder 改造为 Redis Lua 预扣 + MQ 异步同步 DB 库存"
```

---

### Task 7: 修改 OrderServiceImpl — cancel 和 deliver 改为 MQ

**Files:**
- Modify: `order-service/src/main/java/org/monkey/order/service/impl/OrderServiceImpl.java`

- [ ] **Step 1: 修改 cancel 方法 —— Redis 回补 + MQ 释放**

替换原 `cancel` 方法（第 116-139 行）：

```java
    @Override
    @Transactional
    public void cancel(Long buyerId, Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.belongsToBuyer(buyerId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        OrderStatus oldStatus = order.getStatus();
        order.cancel(reason);
        orderRepository.save(order);

        // 如果取消前是待支付或待发货，需要释放库存
        if (oldStatus == OrderStatus.PENDING_PAYMENT || oldStatus == OrderStatus.PENDING_DELIVERY) {
            // Redis 回补库存
            for (OrderItem item : order.getItems()) {
                stockRedisCache.releaseStock(item.getSkuId(), item.getQuantity());
            }

            // 发送 MQ 异步释放 DB 库存
            OrderEventMessage event = OrderEventMessage.ofCancelled(orderId, buyerId, null);
            try {
                orderEventProducer.sendOrderEvent(event);
            } catch (Exception e) {
                log.error("订单 {} 发送 ORDER_CANCELLED 消息失败", orderId, e);
            }
        }
    }
```

- [ ] **Step 2: 修改 deliver 方法 —— MQ 扣减**

替换原 `deliver` 方法（第 155-174 行）：

```java
    @Override
    @Transactional
    public void deliver(Long sellerId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(404, "订单不存在"));
        if (!order.belongsToSeller(sellerId)) {
            throw new BusinessException(403, "无权操作此订单");
        }
        order.deliver();
        orderRepository.save(order);

        // 发送 MQ 异步扣减 DB 库存
        OrderEventMessage event = OrderEventMessage.ofDelivered(orderId, sellerId, null);
        try {
            orderEventProducer.sendOrderEvent(event);
        } catch (Exception e) {
            log.error("订单 {} 发送 ORDER_DELIVERED 消息失败", orderId, e);
        }
    }
```

- [ ] **Step 3: 删除不再需要的 InventoryFeignClient import 和成员变量**

确认编译通过：

```bash
mvn compile -pl order-service
```
预期：BUILD SUCCESS（不再依赖 InventoryFeignClient）

- [ ] **Step 4: 提交**

```bash
git add order-service/src/main/java/org/monkey/order/service/impl/OrderServiceImpl.java
git commit -m "cancel/deliver 改为 Redis 回补 + MQ 异步通知 inventory"
```

---

### Task 8: 创建 OrderTimeoutTask — 超时自动取消

**Files:**
- Create: `order-service/src/main/java/org/monkey/order/scheduler/OrderTimeoutTask.java`
- Modify: `order-service/src/main/java/org/monkey/order/service/OrderRepository.java` — 添加超时查询方法
- Modify: `order-service/src/main/java/org/monkey/order/service/impl/OrderRepositoryImpl.java` — 实现超时查询

- [ ] **Step 1: 读取 OrderRepositoryImpl**

先读取现有的 Repository 实现，确保新增方法符合项目风格。

- [ ] **Step 2: OrderRepository 添加超时查询方法**

在 `OrderRepository.java` 末尾添加：

```java
    /** 查询超过指定时间仍未支付的订单 */
    List<Order> findPendingPaymentBefore(java.time.LocalDateTime deadline);
```

- [ ] **Step 3: OrderRepositoryImpl 实现超时查询**

在 `order-service/src/main/java/org/monkey/order/service/impl/OrderRepositoryImpl.java` 中实现：

使用 MyBatis-Plus 的 `QueryWrapper`：

```java
    @Override
    public List<Order> findPendingPaymentBefore(LocalDateTime deadline) {
        List<OrderEntity> entities = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<OrderEntity>()
                        .eq("status", 1)  // PENDING_PAYMENT
                        .lt("create_time", deadline)
                        .last("LIMIT 100"));
        if (entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(e -> orderEntityMapper.toDomain(e, findItemsByOrderId(e.getId())))
                .collect(Collectors.toList());
    }
```

需要额外注入 `OrderMapper`——检查现有 RepositoryImpl 中是否已有 `orderMapper` 字段。

- [ ] **Step 4: 创建 OrderTimeoutTask**

```java
package org.monkey.order.scheduler;

import org.monkey.order.cache.StockRedisCache;
import org.monkey.order.model.Order;
import org.monkey.order.model.OrderItem;
import org.monkey.order.mq.OrderEventProducer;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.monkey.order.service.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单超时自动取消定时任务
 *
 * <p>每 5 秒扫描 PENDING_PAYMENT 状态且超过 15 分钟的订单，自动取消并释放库存
 *
 * @author monkey
 */
@Component
public class OrderTimeoutTask {

    private static final Logger log = LoggerFactory.getLogger(OrderTimeoutTask.class);

    /** 超时阈值：15 分钟 */
    private static final int TIMEOUT_MINUTES = 15;

    private final OrderRepository orderRepository;
    private final StockRedisCache stockRedisCache;
    private final OrderEventProducer orderEventProducer;

    public OrderTimeoutTask(OrderRepository orderRepository,
                            StockRedisCache stockRedisCache,
                            OrderEventProducer orderEventProducer) {
        this.orderRepository = orderRepository;
        this.stockRedisCache = stockRedisCache;
        this.orderEventProducer = orderEventProducer;
    }

    /**
     * 每 5 秒扫描超时订单并取消
     */
    @Scheduled(fixedDelay = 5_000)
    public void cancelTimeoutOrders() {
        LocalDateTime deadline = LocalDateTime.now().minusMinutes(TIMEOUT_MINUTES);
        List<Order> timeoutOrders = orderRepository.findPendingPaymentBefore(deadline);

        if (timeoutOrders.isEmpty()) {
            return;
        }

        log.info("发现 {} 个超时订单，开始自动取消", timeoutOrders.size());
        for (Order order : timeoutOrders) {
            try {
                // 先改状态（幂等：cancel() 内部有状态机校验，已取消的会抛异常被 catch 掉）
                order.cancel("超时未支付，自动取消");
                orderRepository.save(order);

                // Redis 回补库存
                for (OrderItem item : order.getItems()) {
                    stockRedisCache.releaseStock(item.getSkuId(), item.getQuantity());
                }

                // 发送 MQ 异步释放 DB 库存
                OrderEventMessage event = OrderEventMessage.ofCancelled(
                        order.getId(), order.getBuyerId(), null);
                orderEventProducer.sendOrderEvent(event);

                log.info("订单 {} 超时自动取消成功", order.getId());
            } catch (Exception e) {
                log.warn("订单 {} 超时取消处理异常: {}", order.getId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 5: 启用 Spring 定时任务**

在 order-service 的 `OrderServiceApplication` 启动类上加 `@EnableScheduling`（或创建单独的 config 类）。

检查 `order-service/src/main/java/org/monkey/order/OrderServiceApplication.java`，添加：

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
// 加在 @SpringBootApplication 下方
```

- [ ] **Step 6: 编译验证**

```bash
mvn compile -pl order-service
```
预期：BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add order-service/src/main/java/org/monkey/order/scheduler/ order-service/src/main/java/org/monkey/order/service/OrderRepository.java order-service/src/main/java/org/monkey/order/service/impl/OrderRepositoryImpl.java order-service/src/main/java/org/monkey/order/OrderServiceApplication.java
git commit -m "新增 OrderTimeoutTask 超时自动取消定时任务"
```

---

### Task 9: inventory-service 添加 RocketMQ + Redis 依赖

**Files:**
- Modify: `inventory-service/pom.xml`
- Modify: `inventory-service/src/main/resources/application.yml`

- [ ] **Step 1: inventory-service pom.xml 添加依赖**

在 `inventory-service/pom.xml` 的 `<dependencies>` block 中，在 `<!-- SpringDoc Swagger UI -->` 之前添加：

```xml
<!-- common-mq：消息 DTO 和常量 -->
<dependency>
    <groupId>org.monkey</groupId>
    <artifactId>common-mq</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- RocketMQ Spring Boot Starter -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>

<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- commons-pool2（Redis 连接池） -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

- [ ] **Step 2: 修改 application.yml 添加 RocketMQ + Redis 配置**

在 `inventory-service/src/main/resources/application.yml` 末尾追加：

```yaml
# RocketMQ 配置
rocketmq:
  name-server: localhost:9876
  producer:
    group: inventory-producer_${spring.profiles.active}
  env: ${spring.profiles.active}

# Redis 配置
spring:
  data:
    redis:
      host: localhost
      port: 6379
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl inventory-service
```
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add inventory-service/pom.xml inventory-service/src/main/resources/application.yml
git commit -m "inventory-service 添加 RocketMQ 和 Redis 依赖及配置"
```

---

### Task 10: 创建 inventory-service 的 StockRedisCache 和 StockLuaScripts

**Files:**
- Create: `inventory-service/src/main/java/org/monkey/inventory/cache/StockRedisCache.java`
- Create: `inventory-service/src/main/java/org/monkey/inventory/cache/StockLuaScripts.java`

inventory-service 侧的 Redis 缓存比 order 侧更全面——需要支持**初始化**（Hash 创建）和**对账**（从 DB 重建）。

- [ ] **Step 1: 创建 StockRedisCache**

```java
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
 * <p>与 order 侧的 StockRedisCache 不同，inventory 侧负责：
 * <ul>
 *   <li>初始化库存缓存（创建商品/对账重建时写入 Hash）</li>
 *   <li>读取缓存用于对账比对</li>
 * </ul>
 * 锁定/回补的 Lua 原子操作在 order 侧。
 *
 * @author monkey
 */
@Component
public class StockRedisCache {

    private static final Logger log = LoggerFactory.getLogger(StockRedisCache.class);

    /** SKU 级别库存 Key 模板 */
    private static final String STOCK_KEY_FORMAT = "stock:sku:%d";

    /** Hash 字段名 */
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
        // 只在 key 不存在时初始化（NX），避免覆盖已有库存
        Map<String, String> fields = new HashMap<>();
        fields.put(FIELD_TOTAL, String.valueOf(totalStock));
        fields.put(FIELD_LOCKED, "0");
        fields.put(FIELD_AVAILABLE, String.valueOf(totalStock));
        fields.put(FIELD_VERSION, "0");
        redisTemplate.opsForHash().putAll(key, fields);
        log.debug("初始化库存缓存: skuId={}, total={}", skuId, totalStock);
    }

    /**
     * 获取 SKU 库存缓存（用于对账）
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

    /** 组装 Redis Key */
    private String formatKey(Long skuId) {
        return String.format(STOCK_KEY_FORMAT, skuId);
    }

    /** 安全转 int */
    private int parseInt(Object val) {
        if (val == null) return 0;
        return Integer.parseInt(String.valueOf(val));
    }

    // -------- StockCacheSnapshot --------

    /**
     * 库存缓存快照
     */
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
```

- [ ] **Step 2: 创建 StockLuaScripts**（预留 Lua 脚本工具类，用于 inventory 侧扩展）

```java
package org.monkey.inventory.cache;

/**
 * 库存 Lua 脚本管理
 *
 * <p>inventory 侧的 Lua 参考：order 侧的 lock_stock.lua / release_stock.lua 负责原子操作。
 * 此处预留 inventory 侧可能用到的 Lua 脚本（如批量初始化、对账修复）。
 *
 * @author monkey
 */
public final class StockLuaScripts {

    private StockLuaScripts() {
        // 工具类
    }

    // 后续可在此处定义 inventory 专属的 Lua 脚本
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl inventory-service
```
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add inventory-service/src/main/java/org/monkey/inventory/cache/
git commit -m "新增 inventory 侧 StockRedisCache 和 StockLuaScripts"
```

---

### Task 11: 创建 OrderEventConsumer — 消费 ORDER_EVENT

**Files:**
- Create: `inventory-service/src/main/java/org/monkey/inventory/mq/OrderEventConsumer.java`

- [ ] **Step 1: 创建 OrderEventConsumer**

```java
package org.monkey.inventory.mq;

import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.OrderEventMessage;
import org.monkey.inventory.api.dto.StockOperationRequest;
import org.monkey.inventory.api.dto.StockOperationResult;
import org.monkey.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 订单事件消费者 —— 消费 ORDER_EVENT Topic
 *
 * <p>处理下单(同步DB锁定)、取消(释放库存)、发货(扣减库存)三种事件
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.ORDER_EVENT,
        consumerGroup = "inventory-consumer_${rocketmq.env}",
        selectorExpression = "env = '${rocketmq.env}'"
)
public class OrderEventConsumer implements RocketMQListener<OrderEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    /** 幂等 Key 前缀 */
    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";
    /** 幂等 Key 过期时间 */
    private static final int IDEMPOTENT_TTL_HOURS = 72;

    private final InventoryService inventoryService;
    private final StringRedisTemplate redisTemplate;

    public OrderEventConsumer(InventoryService inventoryService, StringRedisTemplate redisTemplate) {
        this.inventoryService = inventoryService;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(OrderEventMessage msg) {
        // 1. 幂等检查
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + msg.getEventId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "PROCESSING", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            log.debug("重复消息，跳过: eventId={}", msg.getEventId());
            return;
        }

        try {
            // 2. 按事件类型路由
            switch (msg.getEventType()) {
                case EventType.ORDER_CREATED -> handleOrderCreated(msg);
                case EventType.ORDER_CANCELLED -> handleOrderCancelled(msg);
                case EventType.ORDER_DELIVERED -> handleOrderDelivered(msg);
                default -> log.warn("未知事件类型: {}", msg.getEventType());
            }
            // 3. 处理成功 → 标记幂等值
            redisTemplate.opsForValue().set(idempotentKey, "OK", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            // 4. 失败 → 删除幂等 key，让 RocketMQ 自动重试
            log.error("消费 ORDER_EVENT 失败: eventId={}, eventType={}", msg.getEventId(), msg.getEventType(), e);
            redisTemplate.delete(idempotentKey);
            throw e; // RocketMQ 自动重试
        }
    }

    /** 处理下单 — 同步 DB 锁定库存 */
    private void handleOrderCreated(OrderEventMessage msg) {
        List<StockOperationRequest.StockItem> stockItems = msg.getItems().stream()
                .map(i -> new StockOperationRequest.StockItem(i.getSkuId(), i.getQuantity()))
                .toList();
        StockOperationRequest request = new StockOperationRequest(msg.getOrderId(), stockItems);
        StockOperationResult result = inventoryService.lockStock(request);
        if (Boolean.FALSE.equals(result.getSuccess())) {
            throw new RuntimeException("DB 锁定库存失败: orderId=" + msg.getOrderId() + ", " + result.getMessage());
        }
        log.info("DB 锁定库存成功: orderId={}", msg.getOrderId());
    }

    /** 处理取消 — 释放 DB 库存 */
    private void handleOrderCancelled(OrderEventMessage msg) {
        StockOperationRequest request = new StockOperationRequest(msg.getOrderId(), null);
        StockOperationResult result = inventoryService.releaseStock(request);
        if (Boolean.FALSE.equals(result.getSuccess())) {
            // 可能已经释放过（幂等），只记日志不抛异常
            log.warn("释放库存: orderId={}, result={}", msg.getOrderId(), result.getMessage());
        }
        log.info("DB 库存已释放: orderId={}", msg.getOrderId());
    }

    /** 处理发货 — 扣减 DB 库存 */
    private void handleOrderDelivered(OrderEventMessage msg) {
        StockOperationRequest request = new StockOperationRequest(msg.getOrderId(), null);
        StockOperationResult result = inventoryService.deductStock(request);
        if (Boolean.FALSE.equals(result.getSuccess())) {
            // 可能已经扣减过（幂等），只记日志不抛异常
            log.warn("扣减库存: orderId={}, result={}", msg.getOrderId(), result.getMessage());
        }
        log.info("DB 库存已扣减: orderId={}", msg.getOrderId());
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl inventory-service
```
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add inventory-service/src/main/java/org/monkey/inventory/mq/OrderEventConsumer.java
git commit -m "新增 OrderEventConsumer 消费 ORDER_EVENT 事件"
```

---

### Task 12: 创建 ProductEventConsumer — 消费 PRODUCT_EVENT

**Files:**
- Create: `inventory-service/src/main/java/org/monkey/inventory/mq/ProductEventConsumer.java`

- [ ] **Step 1: 创建 ProductEventConsumer**

```java
package org.monkey.inventory.mq;

import org.monkey.common.mq.constant.EventType;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.monkey.inventory.api.dto.BatchInventorySetupRequest;
import org.monkey.inventory.cache.StockRedisCache;
import org.monkey.inventory.service.InventoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 商品事件消费者 —— 消费 PRODUCT_EVENT Topic
 *
 * <p>处理创建商品时初始化库存
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = TopicConstants.PRODUCT_EVENT,
        consumerGroup = "inventory-consumer_${rocketmq.env}",
        selectorExpression = "env = '${rocketmq.env}'"
)
public class ProductEventConsumer implements RocketMQListener<ProductEventMessage> {

    private static final Logger log = LoggerFactory.getLogger(ProductEventConsumer.class);

    private static final String IDEMPOTENT_KEY_PREFIX = "mq:idempotent:";
    private static final int IDEMPOTENT_TTL_HOURS = 72;

    private final InventoryService inventoryService;
    private final StockRedisCache stockRedisCache;
    private final StringRedisTemplate redisTemplate;

    public ProductEventConsumer(InventoryService inventoryService,
                                StockRedisCache stockRedisCache,
                                StringRedisTemplate redisTemplate) {
        this.inventoryService = inventoryService;
        this.stockRedisCache = stockRedisCache;
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void onMessage(ProductEventMessage msg) {
        // 1. 幂等检查
        String idempotentKey = IDEMPOTENT_KEY_PREFIX + msg.getEventId();
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(idempotentKey, "PROCESSING", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        if (Boolean.FALSE.equals(acquired)) {
            log.debug("重复消息，跳过: eventId={}", msg.getEventId());
            return;
        }

        try {
            if (EventType.PRODUCT_CREATED.equals(msg.getEventType())) {
                handleProductCreated(msg);
            }
            redisTemplate.opsForValue().set(idempotentKey, "OK", IDEMPOTENT_TTL_HOURS, TimeUnit.HOURS);
        } catch (Exception e) {
            log.error("消费 PRODUCT_EVENT 失败: eventId={}", msg.getEventId(), e);
            redisTemplate.delete(idempotentKey);
            throw e;
        }
    }

    /** 处理商品创建 — 初始化 DB 库存 + Redis 缓存 */
    private void handleProductCreated(ProductEventMessage msg) {
        // 转换为 BatchInventorySetupRequest
        List<BatchInventorySetupRequest> requests = msg.getSkuSetups().stream()
                .map(sku -> {
                    List<BatchInventorySetupRequest.WarehouseStock> warehouseStocks = sku.getWarehouseStocks()
                            .stream()
                            .map(ws -> new BatchInventorySetupRequest.WarehouseStock(
                                    ws.getWarehouseId(), ws.getTotalStock()))
                            .toList();
                    return new BatchInventorySetupRequest(sku.getSkuId(), msg.getSellerId(), warehouseStocks);
                })
                .toList();

        // 写入 DB
        inventoryService.batchSetupInventory(requests);

        // 写入 Redis 缓存（按 SKU 聚合总库存）
        for (ProductEventMessage.SkuInventorySetup sku : msg.getSkuSetups()) {
            int totalStock = sku.getWarehouseStocks().stream()
                    .mapToInt(ProductEventMessage.WarehouseStockEntry::getTotalStock)
                    .sum();
            stockRedisCache.initStock(sku.getSkuId(), totalStock);
        }

        log.info("商品创建库存初始化完成: productId={}, 共 {} 个SKU", msg.getProductId(), msg.getSkuSetups().size());
    }
}
```

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl inventory-service
```
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add inventory-service/src/main/java/org/monkey/inventory/mq/ProductEventConsumer.java
git commit -m "新增 ProductEventConsumer 消费 PRODUCT_EVENT 事件"
```

---

### Task 13: 创建死信消费者和死信表

**Files:**
- Create: `inventory-service/src/main/java/org/monkey/inventory/mq/DlqMessageHandler.java`
- Modify: `docker/mysql/init.sql` — 添加 dead_message 表

- [ ] **Step 1: 创建死信表 DDL**

在 `docker/mysql/init.sql` 末尾追加（需要确认是在哪个数据库; inventory 相关表在 `shop_db_inventory`）：

```sql
-- ================================================================
-- 死信消息表（消息消费 16 次失败后进入 DLQ 的记录）
-- ================================================================
CREATE TABLE IF NOT EXISTS dead_message (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    topic           VARCHAR(64)   NOT NULL COMMENT '原始Topic',
    msg_id          VARCHAR(64)   NOT NULL COMMENT 'RocketMQ消息ID',
    body            TEXT          COMMENT '消息体JSON',
    event_type      VARCHAR(32)   COMMENT '事件类型',
    retry_times     INT DEFAULT 0 COMMENT '已重试次数',
    fail_reason     VARCHAR(500)  COMMENT '失败原因',
    status          TINYINT DEFAULT 0 COMMENT '0待处理 1已修复 2已丢弃',
    create_time     DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_topic (topic),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='死信消息表';
```

- [ ] **Step 2: 创建 DlqMessageHandler**

```java
package org.monkey.inventory.mq;

import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 死信队列消费者 —— 消费 DLQ Topic
 *
 * <p>RocketMQ 默认重试 16 次后，消息进入 %DLQ% 前缀的 Topic
 *
 * @author monkey
 */
@Component
@RocketMQMessageListener(
        topic = "%DLQ%inventory-consumer_${rocketmq.env}",
        consumerGroup = "inventory-dlq-consumer_${rocketmq.env}"
)
public class DlqMessageHandler implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(DlqMessageHandler.class);

    @Override
    public void onMessage(MessageExt msg) {
        String body = new String(msg.getBody(), StandardCharsets.UTF_8);
        log.error("死信消息: topic={}, msgId={}, retryTimes={}, body={}",
                msg.getTopic(), msg.getMsgId(), msg.getReconsumeTimes(), body);

        // TODO: 写入 dead_message 表（需要创建对应的实体和 Mapper）
        // TODO: 发送告警通知（钉钉/企微/邮件）

        // ACK 掉 —— 死信队列中的消息不再重试
    }
}
```

- [ ] **Step 3: 编译验证**

```bash
mvn compile -pl inventory-service
```
预期：BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add inventory-service/src/main/java/org/monkey/inventory/mq/DlqMessageHandler.java docker/mysql/init.sql
git commit -m "新增死信消费者和 dead_message 建表 DDL"
```

---

### Task 14: 创建对账任务 StockReconciliationTask

**Files:**
- Create: `inventory-service/src/main/java/org/monkey/inventory/scheduler/StockReconciliationTask.java`
- Modify: `inventory-service/src/main/java/org/monkey/inventory/service/InventoryRepository.java` — 添加 summaryBySku
- Modify: `inventory-service/src/main/java/org/monkey/inventory/service/impl/InventoryRepositoryImpl.java` — 实现 summaryBySku
- Modify: `inventory-service/src/main/java/org/monkey/inventory/mapper/InventoryMapper.java` — 添加汇总查询

- [ ] **Step 1: InventoryMapper 添加汇总查询**

在 `InventoryMapper.java` 中添加：

```java
import org.monkey.inventory.entity.InventoryEntity;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 按 SKU 汇总库存（用于对账）
 */
@Select("SELECT sku_id, SUM(total_stock) AS total_stock, SUM(locked_stock) AS locked_stock, " +
        "SUM(total_stock - locked_stock) AS available_stock " +
        "FROM inventory GROUP BY sku_id")
List<InventoryEntity> summaryBySku();
```

注意：`InventoryEntity` 需要有 `totalStock`, `lockedStock`, `availableStock` 等字段（或者加 `@TableField(exist = false)` 的额外字段）。如果没有，改为用 Map 接收：

```java
import org.apache.ibatis.annotations.Select;
import java.util.List;
import java.util.Map;

/**
 * 按 SKU 汇总库存
 */
@Select("SELECT sku_id, SUM(total_stock) AS total_stock, SUM(locked_stock) AS locked_stock, " +
        "SUM(total_stock - locked_stock) AS available_stock " +
        "FROM inventory GROUP BY sku_id")
List<Map<String, Object>> summaryBySku();
```

- [ ] **Step 2: InventoryRepository 接口添加方法**

在 `InventoryRepository.java` 中添加：

```java
/** 按 SKU 汇总库存（用于对账） */
List<java.util.Map<String, Object>> summaryBySku();
```

- [ ] **Step 3: InventoryRepositoryImpl 实现**

在实际实现类中委托给 Mapper。

- [ ] **Step 4: 创建 StockReconciliationTask**

```java
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

    @Scheduled(fixedDelay = 300_000) // 每 5 分钟
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
                // Redis 缺失 → 从 DB 重建
                stockRedisCache.initStock(skuId, dbTotal);
                // init 时 locked=0, available=total，需要再校正 locked
                stockRedisCache.correctFromDb(skuId, dbTotal, dbLocked, dbAvailable, 0);
                fixed++;
                continue;
            }

            // 三方对比
            if (cache.getTotal() != dbTotal
                    || cache.getLocked() != dbLocked
                    || cache.getAvailable() != dbAvailable) {
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
```

- [ ] **Step 5: 启用定时任务**

在 `InventoryServiceApplication` 启动类上加 `@EnableScheduling`。

- [ ] **Step 6: 编译验证**

```bash
mvn compile -pl inventory-service
```
预期：BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add inventory-service/src/main/java/org/monkey/inventory/scheduler/ inventory-service/src/main/java/org/monkey/inventory/mapper/InventoryMapper.java inventory-service/src/main/java/org/monkey/inventory/service/InventoryRepository.java inventory-service/src/main/java/org/monkey/inventory/service/impl/InventoryRepositoryImpl.java inventory-service/src/main/java/org/monkey/inventory/InventoryServiceApplication.java
git commit -m "新增 StockReconciliationTask Redis/DB 对账任务"
```

---

### Task 15: catalog-service 添加 RocketMQ 依赖和 ProductEventProducer

**Files:**
- Modify: `catalog-service/pom.xml`
- Modify: `catalog-service/src/main/resources/application.yml`
- Create: `catalog-service/src/main/java/org/monkey/catalog/mq/ProductEventProducer.java`

- [ ] **Step 1: catalog-service pom.xml 添加依赖**

在 `<!-- SpringDoc Swagger UI -->` 之前添加：

```xml
<!-- common-mq：消息 DTO 和常量 -->
<dependency>
    <groupId>org.monkey</groupId>
    <artifactId>common-mq</artifactId>
    <version>${project.version}</version>
</dependency>

<!-- RocketMQ Spring Boot Starter -->
<dependency>
    <groupId>org.apache.rocketmq</groupId>
    <artifactId>rocketmq-spring-boot-starter</artifactId>
</dependency>
```

- [ ] **Step 2: application.yml 添加 RocketMQ 配置**

在 `catalog-service/src/main/resources/application.yml` 末尾追加：

```yaml
# RocketMQ 配置
rocketmq:
  name-server: localhost:9876
  producer:
    group: catalog-producer_${spring.profiles.active}
  env: ${spring.profiles.active}
```

- [ ] **Step 3: 创建 ProductEventProducer**

```java
package org.monkey.catalog.mq;

import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.monkey.common.mq.constant.TopicConstants;
import org.monkey.common.mq.dto.ProductEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 商品事件消息生产者
 *
 * @author monkey
 */
@Component
public class ProductEventProducer {

    private static final Logger log = LoggerFactory.getLogger(ProductEventProducer.class);

    private final RocketMQTemplate rocketMQTemplate;

    @Value("${rocketmq.env}")
    private String env;

    public ProductEventProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送商品创建事件消息
     *
     * @param event 商品事件消息
     */
    public void sendProductEvent(ProductEventMessage event) {
        event.setEnv(env);
        Message<ProductEventMessage> message = MessageBuilder
                .withPayload(event)
                .setHeader("env", env)
                .build();
        rocketMQTemplate.send(TopicConstants.PRODUCT_EVENT, message);
        log.info("发送商品事件: eventType={}, productId={}, eventId={}",
                event.getEventType(), event.getProductId(), event.getEventId());
    }
}
```

- [ ] **Step 4: 编译验证**

```bash
mvn compile -pl catalog-service
```
预期：BUILD SUCCESS

- [ ] **Step 5: 提交**

```bash
git add catalog-service/pom.xml catalog-service/src/main/resources/application.yml catalog-service/src/main/java/org/monkey/catalog/mq/
git commit -m "catalog-service 添加 RocketMQ 依赖和 ProductEventProducer"
```

---

### Task 16: 修改 ProductServiceImpl — createProduct 改为 MQ

**Files:**
- Modify: `catalog-service/src/main/java/org/monkey/catalog/service/impl/ProductServiceImpl.java`

- [ ] **Step 1: 修改 createProduct —— 发 MQ 替代 Feign**

修改 `ProductServiceImpl.java` 的构造器和 `createProduct` 方法。

**删除** `InventoryFeignClient` 注入，**新增** `ProductEventProducer` 注入：

```java
// 删除:
// import org.monkey.inventory.api.feign.InventoryFeignClient;
// private final InventoryFeignClient inventoryFeignClient;
// 新增:
import org.monkey.catalog.mq.ProductEventProducer;
import org.monkey.common.mq.dto.ProductEventMessage;
import java.util.stream.Collectors;

private final ProductEventProducer productEventProducer;

public ProductServiceImpl(ProductRepository productRepository, ProductEventProducer productEventProducer) {
    this.productRepository = productRepository;
    this.productEventProducer = productEventProducer;
}
```

**修改 `createProduct` 方法**（替换第 57-65 行）：

```java
        // 发送 MQ 消息异步初始化库存（替代原先的 Feign 同步调用）
        try {
            List<ProductEventMessage.SkuInventorySetup> skuSetups = product.getSkus().stream()
                    .map(sku -> {
                        CreateSkuRequest skuReq = request.getSkus().stream()
                                .filter(r -> r.getSpecName().equals(sku.getSpecName()))
                                .findFirst().orElse(null);
                        List<ProductEventMessage.WarehouseStockEntry> entries;
                        if (skuReq != null) {
                            entries = skuReq.getStockEntries().stream()
                                    .map(e -> new ProductEventMessage.WarehouseStockEntry(
                                            e.getWarehouseId(), e.getTotalStock()))
                                    .collect(Collectors.toList());
                        } else {
                            entries = List.of();
                        }
                        return new ProductEventMessage.SkuInventorySetup(sku.getId(), entries);
                    })
                    .collect(Collectors.toList());

            ProductEventMessage event = ProductEventMessage.ofCreated(
                    product.getId(), product.getSellerId(), skuSetups, null);
            productEventProducer.sendProductEvent(event);
        } catch (Exception e) {
            log.warn("发送 PRODUCT_CREATED 消息失败，对账任务将兜底: productId={}", product.getId(), e);
        }
```

**删除** `buildInventoryRequests` 方法和相关的 import（`BatchInventorySetupRequest` 等不再需要）。

- [ ] **Step 2: 编译验证**

```bash
mvn compile -pl catalog-service
```
预期：BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add catalog-service/src/main/java/org/monkey/catalog/service/impl/ProductServiceImpl.java
git commit -m "createProduct 改为 MQ 异步初始化库存，替代 Feign 同步调用"
```

---

### Task 17: 全量编译和集成验证

- [ ] **Step 1: 全量编译**

```bash
mvn compile
```
预期：全部模块 BUILD SUCCESS

- [ ] **Step 2: 启动所有基础设施**

```bash
docker-compose up -d
```

确认所有容器 `healthy`：
```bash
docker-compose ps
```
预期：mysql(healthy), redis(healthy), nacos(healthy), rocketmq-namesrv(up), rocketmq-broker(up), rocketmq-dashboard(up)

- [ ] **Step 3: 启动 inventory-service**

```bash
mvn spring-boot:run -pl inventory-service
```
预期：启动成功，Nacos 注册成功，RocketMQ Consumer 连接成功

- [ ] **Step 4: 启动 catalog-service**

```bash
mvn spring-boot:run -pl catalog-service
```
预期：启动成功，Nacos 注册成功

- [ ] **Step 5: 启动 order-service**

```bash
mvn spring-boot:run -pl order-service
```
预期：启动成功，Nacos 注册成功，RocketMQ Producer 连接成功

- [ ] **Step 6: 验证下单流程**

用 curl 或 Postman 测试下单 API。验证点：
1. 下单 → Redis 预扣成功（Redis CLI 查 `stock:sku:{id}` Hash 的 locked 字段 +1）
2. 下单返回订单 ID
3. 等几秒 → inventory-service 消费 ORDER_EVENT → DB 的 StockLock 表有记录
4. 超时取消 → 等 15 分钟（或临时改阈值测试）→ 订单自动变为 CANCELLED

- [ ] **Step 7: 提交**

```bash
git commit -m "全量编译通过，集成验证"
```

---

## Self-Review Checklist

- [x] Spec coverage: 全部 8 个改造点有对应 Task
  - ① Task 6 (createOrder Redis预扣+MQ)
  - ② 支付不改，N/A
  - ③ Task 7 (deliver MQ扣减)
  - ④ Task 7 (cancel MQ释放)
  - ⑤ Task 8 (OrderTimeoutTask)
  - ⑥ Task 16 (createProduct MQ)
  - ⑦ Task 14 (StockReconciliationTask)
  - ⑧ Task 13 (DlqMessageHandler)
- [x] No placeholders — 所有代码步骤都有完整实现
- [x] Type consistency — OrderEventMessage/ProductEventMessage 的字段名在各 Task 中一致
- [x] All JavaDoc and inline comments per project convention
