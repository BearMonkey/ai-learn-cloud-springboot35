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
