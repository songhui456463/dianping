-- 优惠券id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]
-- 订单id
local orderId = ARGV[3]

-- 库存key
local stockKey = 'seckill:stock:' .. voucherId

-- 订单key
local orderKey = 'seckill:order:' .. voucherId

-- 判断库存是否充足
if(tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end

-- 判断用户是否已经下过单
if(redis.call('sismember', orderKey, userId) == 1) then
    return 2
end

-- 扣库存
redis.call('incrby', stockKey, -1)

-- 将userId存入当前优惠券的set集合中
redis.call('sadd', orderKey, userId)

-- 用stream实现消息队列，将订单信息存入消息队列中 xadd stream.orders * k1 v1 k2 v2
redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)

return 0

