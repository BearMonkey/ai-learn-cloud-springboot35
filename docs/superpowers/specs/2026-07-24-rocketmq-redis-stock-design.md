# RocketMQ + Redis 库存改造设计文档

## 目标

将项目中同步 Feign 调用的库存操作改造为基于 RocketMQ 的异步消息架构，引入 Redis 预扣库存解决高并发场景下的超卖问题，实现企业级电商库存管理方案。

## 改造范围

| # | 场景 | 改造前 | 改造后 | 涉及服务 |
|---|------|--------|--------|---------|
| ① | 下单锁库存 | Feign 同步调 DB | Redis Lua 预扣 + MQ 异步落 DB | order + inventory |
| ② | 支付 | 改状态 | 不改 | — |
| ③ | 发货扣库存 | Feign 同步 | MQ 异步 + 幂等 | order + inventory |
| ④ | 取消释库存 | Feign 同步 + try-catch | MQ 异步 + 幂等 | order + inventory |
| ⑤ | 超时自动取消 | 无 | 定时扫表 + Redis 回补 + MQ 释放 DB | order |
| ⑥ | 创建商品初始化库存 | Feign 同步 + try-catch | MQ 异步 + 幂等 | catalog + inventory |
| ⑦ | Redis/DB 对账 | 无 | 定时任务 | inventory |
| ⑧ | 死信队列 | 无 | DLQ 消费 + 死信表 | inventory |

## 架构设计

### 消息 Topic

| Topic | 生产者 | 消费者 | 说明 |
|-------|--------|--------|------|
| ORDER_EVENT | order-service | inventory-service | 订单全生命周期事件（创建/取消/发货） |
| PRODUCT_EVENT | catalog-service | inventory-service | 商品创建事件 |
| ORDER_TIMEOUT_CHECK | order-service（自产自消） | order-service | 超时取消检查（延迟消息） |

### 环境隔离方案

- 同一集群内 Topic 固定不变，不同环境用 Consumer Group 隔离
- 生产者发送消息时注入 `env` User Property
- 消费者通过 SQL92 `selectorExpression` 过滤只消费本环境消息
- Consumer Group 命名规则：`{service}-consumer_{env}`，如 `inventory-consumer_dev`

### Redis 库存缓存

```
Key:  stock:sku:{skuId}
Type: Hash
Fields:
  total     → 总库存数
  locked    → 已锁定数
  available → 可用数（total - locked）
  version   → 乐观锁版本号（对账追踪用）
```

### Lua 原子预扣

```lua
-- lock_stock.lua
-- KEYS[1]: stock:sku:{skuId}
-- ARGV[1]: 锁定数量
-- 返回: {1, new_available} 成功 / {0, available} 失败
local key = KEYS[1]
local qty = tonumber(ARGV[1])
local available = tonumber(redis.call('HGET', key, 'available'))

if available == nil then
    return {0, -1}
end

if available >= qty then
    redis.call('HINCRBY', key, 'locked', qty)
    redis.call('HINCRBY', key, 'available', -qty)
    redis.call('HINCRBY', key, 'version', 1)
    return {1, tonumber(redis.call('HGET', key, 'available'))}
else
    return {0, available}
end
```

```lua
-- release_stock.lua
-- KEYS[1]: stock:sku:{skuId}
-- ARGV[1]: 释放数量
local key = KEYS[1]
local qty = tonumber(ARGV[1])
local locked = tonumber(redis.call('HGET', key, 'locked'))

if locked ~= nil and locked >= qty then
    redis.call('HINCRBY', key, 'locked', -qty)
    redis.call('HINCRBY', key, 'available', qty)
    redis.call('HINCRBY', key, 'version', 1)
    return 1
end
return 0
```

## 详细流程

### ① 下单锁库存

```
用户提交订单
    │
    ├─ 1. 查SKU（Feign 同步，不变）
    ├─ 2. Redis Lua 预扣库存（原子操作，同步）
    │      扣失败 → 返回"库存不足"
    │      扣成功 → 继续
    ├─ 3. 创建订单 + 落库（PENDING_PAYMENT）
    ├─ 4. 发 ORDER_EVENT（eventType=ORDER_CREATED）异步同步 DB
    └─ 5. 返回"下单成功"

inventory-service 消费 ORDER_EVENT（eventType=ORDER_CREATED）:
    ├─ 幂等检查（eventId + StockLock.orderId）
    ├─ DB 层锁定库存 + 创建 StockLock 记录
    └─ 失败重试 → 死信队列
```

### ③ 发货扣库存

```
order-service.deliver():
    ├─ 订单状态: PENDING_DELIVERY → IN_DELIVERY，落库
    ├─ 发 ORDER_EVENT（eventType=ORDER_DELIVERED）
    └─ 返回

inventory-service 消费:
    ├─ 幂等检查
    ├─ 扣减库存 + StockLock → DEDUCTED
    └─ 失败重试
```

### ④ 取消释库存

```
order-service.cancel():
    ├─ 订单状态 → CANCELLED，落库
    ├─ Redis 回补库存（Lua脚本）
    ├─ 发 ORDER_EVENT（eventType=ORDER_CANCELLED）
    └─ 返回

inventory-service 消费:
    ├─ 幂等检查（StockLock 状态）
    ├─ 释放库存 + StockLock → RELEASED
    └─ 失败重试
```

### ⑤ 超时自动取消

```
定时任务（每5秒，order-service）:
    ├─ 查 orders WHERE status=PENDING_PAYMENT AND create_time < now()-15min
    ├─ 逐笔处理:
    │   ├─ 更新订单状态 → CANCELLED
    │   ├─ Redis 回补库存
    │   └─ 发 ORDER_EVENT(CANCELLED) 异步释放 DB 库存
    └─ 每批最多处理100条
```

### ⑥ 创建商品初始化库存

```
catalog-service.createProduct():
    ├─ 创建商品 + SKU，落库
    ├─ 发 PRODUCT_EVENT
    └─ 返回

inventory-service 消费:
    ├─ 幂等检查（sku+warehouse）
    ├─ DB 初始化库存 + Redis 写入库存缓存
    └─ 失败重试
```

## 幂等设计

- 幂等键：`mq:idempotent:{eventId}`，Redis String，NX 写入，TTL 72小时
- 业务层双重幂等：StockLock.orderId 唯一约束、StockLock.status 状态判断

## 死信队列

- RocketMQ 默认重试16次后进入 `%DLQ%{consumerGroup}`
- 死信消费者：解析原始消息体 → 写入 `dead_message` 表 → 告警

## 对账任务

- 每5分钟执行一次（inventory-service）
- 按 SKU 聚合 DB 库存与 Redis 缓存比对
- 不一致以 DB 为准校正 Redis
- 记录对账结果日志

## 新增模块：common-mq

```
common-mq/
  constant/TopicConstants.java
  constant/EventType.java
  dto/OrderEventMessage.java
  dto/ProductEventMessage.java
  dto/OrderTimeoutMessage.java
```

## 各服务改动概览

### order-service
- createOrder: 加 Redis 预扣 + 发 ORDER_EVENT
- cancel: 加 Redis 回补 + 发 ORDER_EVENT（替代 Feign）
- deliver: 发 ORDER_EVENT（替代 Feign）
- 新增 OrderTimeoutTask 定时任务
- 新增 OrderEventProducer

### inventory-service
- 新增 OrderEventConsumer 消费 ORDER_EVENT
- 新增 ProductEventConsumer 消费 PRODUCT_EVENT
- 新增 DlqMessageHandler 处理死信
- 新增 StockRedisCache + StockLuaScripts
- 新增 StockReconciliationTask 对账任务
- 新增 RedisStockConfig 配置

### catalog-service
- createProduct: 发 PRODUCT_EVENT（替代 Feign）
- 新增 ProductEventProducer
