package com.sh;

import cn.hutool.json.JSONUtil;
import com.sh.entry.Shop;
import com.sh.mapper.ShopMapper;
import com.sh.service.impl.ShopServiceImpl;
import com.sh.utils.CacheClient;
import com.sh.utils.RedisConstants;
import com.sh.utils.RedisData;
import com.sh.utils.RedisIdWorker;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@SpringBootTest
class DianpingApplicationTests {

    @Resource
    private CacheClient cacheClient;

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private ShopMapper shopMapper;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private ExecutorService es = Executors.newFixedThreadPool(500);

    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);

        Runnable task = () -> {
            for (int i = 0; i < 100; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }


    @Test
    void contextLoads() throws InterruptedException {
        shopService.saveShop2Redis(1L, 20L);
    }


    @Test
    void loadShops() {
        List<Shop> shops = shopMapper.selectList(null);
        shops.forEach(this::saveShops2Redis);
    }

    void saveShops2Redis(Shop shop) {
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusHours(48));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + shop.getId(), JSONUtil.toJsonStr(redisData));
    }


    // 加载店铺信息
    @Test
    void loadShopData() {
        // 查询所有店铺信息
        List<Shop> list = shopService.list();
        // 把店铺分组，按照 typeId 分组，typeId 一致的放到一个集合中
        Map<Long, List<Shop>> map = list.stream().collect(Collectors.groupingBy(Shop::getTypeId));
        // 分批写入redis
        for (Map.Entry<Long, List<Shop>> entry : map.entrySet()) {
            Long typeId = entry.getKey();
            List<Shop> shops = entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>(shops.size());
            for (Shop shop: shops) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),
                        new Point(shop.getX(), shop.getY())
                ));
            }
            stringRedisTemplate.opsForGeo().add(RedisConstants.SHOP_GEO_KEY + typeId, locations);
        }
    }

    @Test
    void testHyperLogLog() {
        String[] values = new String[1000];

        for (int i = 0; i < 1000000; i++) {
            int j = i % 1000;

            values[j] = "user_" + i;

            if (j == 999) {
                stringRedisTemplate.opsForHyperLogLog().add("hl2", values);
            }
        }

        Long size = stringRedisTemplate.opsForHyperLogLog().size("hl2");
        System.out.println(size);
    }

}
