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
