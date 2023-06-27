package com.sh.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 将任意java对象序列化为JSON存储在String类型的key中，并设置超时时间
     */
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }

    /**
     * 获取锁
     */
    private Boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放锁
     */
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }


    /**
     * 缓存穿透
     */
    public <R, ID> R queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit
    ) {
        String key = keyPrefix + id;
        // 从redis中获取缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        // 判断是否非空
        if (StrUtil.isNotBlank(json)) {
            // 如果非空，则直接返回
            return JSONUtil.toBean(json, type);
        }

        // 判断命中的是否是空值
        if (json != null) {
            // 非空值则是数据库中不存在的信息，返回一个错误信息
            return null;
        }

        // 如果为空值，则说明缓存过期,要从数据库中取
        R r = dbFallback.apply(id);

        if (r == null) {
            // 数据库中也不存在，就缓存空值
            stringRedisTemplate.opsForValue().set(key, "" , RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
            // 返回错误信息
            return null;
        }

        // 存在就先缓存，再返回
        this.set(key, r, time, unit);
        return r;
    }

    /**
     * 缓存击穿
     */
    public <R, ID> R queryWithMutex(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit
    ) {
        String key = keyPrefix + id;
        // 从redis中获取缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            // 命中直接返回
            return JSONUtil.toBean(shopJson, type);
        }

        // 未命中，判断是否为空值
        if (shopJson != null) {
            return null;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        R r = null;

        try {
            // 获取锁
            Boolean isLock = tryLock(lockKey);

            if (!isLock) {
                // 获取失败，重新获取
                Thread.sleep(50);
                return queryWithMutex(keyPrefix, id, type, dbFallback, time, unit);
            }

            // 获取到锁，从数据库中查询数据
            r = dbFallback.apply(id);

            if (r == null) {
                // 店铺不存在就缓存空值
                stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.SECONDS);
                // 返回错误信息
                return null;
            }

            // 存在，写入redis
            this.set(key, r, time, unit);
        }catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            // 释放锁
            unLock(lockKey);
        }

        return r;
    }

    public <R, ID> R queryWithLogicExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit
    ) {
        String key = keyPrefix + id;
        // 从redis中获取缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isBlank(json)) {
            // 为命中，即不存在该商铺，直接返回空值
            return null;
        }
        
        // 命中反序列化对象
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);

        // 判断是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期直接返回
            return r;
        }

        // 从数据库中找，缓存重建
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean isLock = tryLock(lockKey);

        if (isLock) {
            // 获取到锁，缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() ->{
                try {
                    // 查询数据库
                    R newR = dbFallback.apply(id);
                    // 缓存重建
                    this.setWithLogicalExpire(key, newR, time, unit);
                }catch (Exception e) {
                    throw new RuntimeException(e);
                }finally {
                    unLock(lockKey);
                }
            });
        }

        // 返回过期的商铺信息
        return r;
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        // 写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }


}
