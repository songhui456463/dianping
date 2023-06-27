package com.sh.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import cn.hutool.log.Log;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sh.dto.Result;
import com.sh.entry.Shop;
import com.sh.mapper.ShopMapper;
import com.sh.service.IShopService;
import com.sh.utils.CacheClient;
import com.sh.utils.RedisConstants;
import com.sh.utils.RedisData;
import com.sh.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.geo.*;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private CacheClient cacheClient;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    @Override
    public Result queryShopByType(Long typeId, Integer current, Double x, Double y) {
        if (x == null || y == null) {
            // 如果没有坐标就查询当前种类的所有商铺
            Page<Shop> page = query().eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            List<Shop> shops = page.getRecords();
            return Result.ok(shops);
        }

        // 计算分页参数
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

        String key = RedisConstants.SHOP_GEO_KEY + typeId;

        // 查询 Redis，按照距离排序、分页。
        GeoResults<RedisGeoCommands.GeoLocation<String>> search = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeCoordinates().limit(end)
        );

        if (search == null) {
            return Result.ok(Collections.emptyList());
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = search.getContent();
        if (content.size() == 0) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = new ArrayList<>(content.size());
        Map<String, Distance> distanceMap = new HashMap<>(content.size());
        // 跳过from个元素
        content.stream().skip(from).forEach(item -> {
            // 店铺id
            String shopStr = item.getContent().getName();
            ids.add(Long.valueOf(shopStr));
            // 获取距离
            Distance distance = item.getDistance();
            distanceMap.put(shopStr, distance);
        });

        String idsStr = StrUtil.join(",", ids);
        // 查询所有符合要求的商店
        List<Shop> shops = query().in("id", ids).last("order by field(id," + idsStr + ")").list();

        shops.forEach(shop -> {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        });

        return Result.ok(shops);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }

        // 先更新数据库
        if (updateById(shop)) {
            // 让缓存失效
            stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + id);
            return Result.ok();
        }

        return Result.fail("更新失败");
    }

    @Override
    public Result queryById(Long id) {
        if (id == null) {
            return Result.fail("id不能为空");
        }

        // 缓存穿透
//        Shop shop = queryWithPassThrough(id);
        // 缓存击穿
//        Shop shop = queryWithMutex(id);
        // 逻辑过期
//        Shop shop = queryWithLogicExpire(id);
        Shop shop = cacheClient.queryWithLogicExpire(RedisConstants.CACHE_SHOP_KEY, id, Shop.class, this::getById, RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        if (shop == null) {
            return Result.fail("该商铺不存在");
        }

        return Result.ok(shop);
    }

    /**
     * 逻辑过期解决缓存穿透和缓存击穿
     *
     * @param id
     * @return
     */
    public Shop queryWithLogicExpire(Long id) {
        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;
        // 从redis中获取商铺缓存
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);

        // 判断是否命中
        if (StrUtil.isBlank(shopJson)) {
            // 为命中，即不存在该商铺，直接返回空值
            return null;
        }

        // 命中，反序列化为对象
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);

        if (expireTime.isAfter(LocalDateTime.now())) {
            // 未过期
            return shop;
        }

        // 过期
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);

        // 获取到锁
        if (isLock) {
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    // 缓存重建
                    saveShop2Redis(id, 20L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                } finally {
                    unlock(lockKey);
                }
            });
        }

        // 无论是否获取到锁都返回shop
        return shop;
    }

    public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        Shop shop = getById(id);
        Thread.sleep(200);
        // 封装缓存过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }

    /**
     * 解决缓存击穿问题
     */
    public Shop queryWithMutex(Long id) {

        String cacheKey = RedisConstants.CACHE_SHOP_KEY + id;

        // 从redis中查询商店信息
        String shopJson = stringRedisTemplate.opsForValue().get(cacheKey);
        // 如果非空，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中是否为空字符串
        if (shopJson != null) {
            return null;
        }

        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Shop shop;

        try {
            // 尝试获取互斥锁
            boolean isLock = tryLock(lockKey);

            if (!isLock) {
                // 重复去获得锁
                Thread.sleep(30);
                return queryWithMutex(id);
            }

            // 否则从数据库中取
            shop = getById(id);

//           Thread.sleep(200);

            if (shop == null) {
                // 店铺不存在就缓存空值,设置2分钟超时
                stringRedisTemplate.opsForValue().set(cacheKey,
                        "",
                        RedisConstants.CACHE_NULL_TTL,
                        TimeUnit.MINUTES);
                return null;
            }

            // 写入redis缓存中
            stringRedisTemplate.opsForValue().set(cacheKey,
                    JSONUtil.toJsonStr(shop),
                    RedisConstants.CACHE_SHOP_TTL,
                    TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            unlock(lockKey);
        }

        return shop;
    }

    /**
     * 判断当前线程是否拿到key的锁
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        return Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.MINUTES));
    }

    /**
     * 释放锁
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    /**
     * 解决缓存穿透(返回空值)
     */
    public Shop queryWithPassThrough(Long id) {

        // 从redis中查询商店信息
        String shopJson = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        // 如果非空，直接返回
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }

        // 判断命中是否为空字符串
        if (shopJson != null) {
            return null;
        }

        // 否则从数据库中取
        Shop shop = getById(id);

        if (shop == null) {
            // 店铺不存在就缓存空值,设置2分钟超时
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,
                    "",
                    RedisConstants.CACHE_NULL_TTL,
                    TimeUnit.MINUTES);
            return null;
        }

        // 存入redis中，并设置超时时间
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop),
                RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);

        return shop;
    }


}
