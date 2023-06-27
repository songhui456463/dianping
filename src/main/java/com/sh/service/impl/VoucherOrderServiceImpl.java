package com.sh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sh.dto.Result;
import com.sh.dto.UserDTO;
import com.sh.dto.UserHolder;
import com.sh.entry.VoucherOrder;
import com.sh.mapper.VoucherOrderMapper;
import com.sh.service.ISeckillVoucherService;
import com.sh.service.IVoucherOrderService;
import com.sh.utils.RedisIdWorker;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Autowired
    private ISeckillVoucherService seckillVoucherService;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

//    private ConcurrentHashMap<Long, Boolean> localMap = new ConcurrentHashMap<>();

    private AtomicInteger stock = new AtomicInteger(200);

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

//    /**
//     * 创建阻塞队列，并初始化阻塞队列的大小
//     */
//    private static final BlockingQueue<VoucherOrder> orderTasks =
//            new ArrayBlockingQueue<>(1024 * 1024);

    /**
     * 创建线程池
     */
    private static final ExecutorService SECKILL_ORDER_EXECUTOR =new ThreadPoolExecutor(
            8,
            16,
            60,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(100)
    );

    private static final String queueName = "stream.orders";

    /**
     * 标有postconstruct注解的方法，容器在bean创建完成并属性赋值完成后，会调用该初始化方法
     * 容器在启动时，便初始化独立线程，从队列去数据
     */
    @PostConstruct
    private void init() {
//        localMap.put(12L, false);
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 获取消息队列中未被消费的订单信息, XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(queueName, ReadOffset.lastConsumed())
                    );

                    if (list == null || list.isEmpty()) {
                        // 如果为null，则说明没有消息
                        continue;
                    }
                    // 解析消息
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 处理订单
                    createVoucherOrder(voucherOrder);
                    // 确认消息 xack s1 g1 id
                    stringRedisTemplate.opsForStream().acknowledge(
                            queueName,
                            "g1",
                            record.getId()
                    );
                } catch (Exception e) {
                    log.error("订单处理异常", e);
                    handlePendingList();
                }
            }
        }
    }

    /**
     * 处理异常消息
     */
    private void handlePendingList() {
        while (true) {
            try {
                // 获取消息队列中未被消费的订单信息, XREADGROUP GROUP g1 c1 COUNT 1 STREAMS s1 0
                List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                        Consumer.from("g1", "c1"),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(queueName, ReadOffset.from("0"))
                );

                if (list == null || list.isEmpty()) {
                    // 如果为null，则说明没有异常消息
                    break;
                }
                // 解析消息
                MapRecord<String, Object, Object> record = list.get(0);
                Map<Object, Object> value = record.getValue();
                VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                // 处理订单
                createVoucherOrder(voucherOrder);
                // 确认消息 xack s1 g1 id
                stringRedisTemplate.opsForStream().acknowledge(
                        queueName,
                        "g1",
                        record.getId()
                );
            } catch (Exception e) {
                log.error("订单处理异常", e);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
    }

    @Autowired
    private RabbitTemplate rabbitTemplate;
    // 1: 定义交换机
    private String exchangeName = "topic_order_exchange";
    // 2: 路由key
    private String routeKey = "miaosha.order";

    @Override
    public Result seckillVoucher(Long voucherId) {
        if (stock.intValue() == 0) {
            return Result.fail("库存不足！");
        }

//        if (localMap.get(voucherId)) {
//            return Result.fail("库存不足！");
//        }

        UserDTO user = UserHolder.getUser();
        // 生成订单id
        long orderId = redisIdWorker.nextId("order");
        // 执行脚本
        Long result = stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(),
                user.getId().toString(),
                String.valueOf(orderId)
        );
        int r = result.intValue();

        //  不为0，则不表示没有购买资格
        if (r != 0) {
//            if (r == 1) localMap.put(voucherId, true);
            return Result.fail(r == 1 ? "库存不足！" : "不能重复下单!");
        }

        stock.decrementAndGet();

//        // 发送给消息中间件
//        VoucherOrder voucherOrder = new VoucherOrder();
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(user.getId());
//        voucherOrder.setVoucherId(voucherId);
//        // 发送订单信息给RabbitMQ fanout
//        rabbitTemplate.convertAndSend(exchangeName, routeKey, voucherOrder);

        // 返回订单 id
        return Result.ok(orderId);
    }


    //    @Override
//    public Result seckillVoucher(Long voucherId) {
//        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
//
//        // 先判断秒杀是否开始
//        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
//            return Result.fail("秒杀未开始");
//        }
//
//        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
//            return Result.fail("秒杀已结束");
//        }
//
//        // 判断库存情况
//        if (seckillVoucher.getStock() < 1) {
//            return Result.fail("库存不足");
//        }
//
//        // 在扣减库存前增加”判断当前优惠券用户是否已经下过单“的逻辑
//        // UserHolder.getUser()为不同线程的对象，那么userId也不同，不能锁userId
////        Long userId = UserHolder.getUser().getId();
////        // 这种方式要等事务提交才释放锁
////        synchronized (userId.toString().intern()) {
////            // 因为@Transactional是由aop实现的，只有调用代理对象的createVoucherOrder时，才生效
////            // this代表当前对象
////            // 所以得先获取当前的代理对象
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//
//        return createVoucherOrder(voucherId);
//    }

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
//        System.out.println(UserHolder.getUser());
        Long userId = voucherOrder.getUserId();

        // 获取锁
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        // 获取互斥锁
        // 使用空参意味着不会进行重复尝试获取锁
        boolean isLock = lock.tryLock();

        if (!isLock) {
            log.error("不允许重复下单！");
            return;
        }

        try {
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0) {
                log.error("用户已经购买过一次");
                return;
            }

            // 扣库存
            boolean success = seckillVoucherService.update().
                    setSql("stock = stock - 1").
                    eq("voucher_id", voucherOrder.getVoucherId()).
                    gt("stock", 0).
                    update();

            // 扣库存失败
            if (!success) {
                log.error("库存不足");
                return;
            }

            // 保存订单
            save(voucherOrder);
            log.debug("下单成功");
        } finally {
            // 释放锁
            lock.unlock();
        }
    }


//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//
//        // 获取锁
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        // 获取互斥锁
//        // 使用空参意味着不会进行重复尝试获取锁
//
//        boolean isLock = lock.tryLock();
//
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                return Result.fail("用户已经购买过一次");
//            }
//
//            // 扣库存
//            boolean success = seckillVoucherService.update().
//                    setSql("stock = stock - 1").
//                    eq("voucher_id", voucherId).
//                    gt("stock", 0).
//                    update();
//
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//
//            // 创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            // 生成id
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//
//            return Result.ok(orderId);
//        } finally {
//            // 释放锁
//            lock.unlock();
//        }
//    }


//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//
//        // 获取锁
//        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        boolean isLock = lock.tryLock(1200);
//
//        if (!isLock) {
//            return Result.fail("不允许重复下单");
//        }
//
//        try {
//            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//            if (count > 0) {
//                return Result.fail("用户已经购买过一次");
//            }
//
//            // 扣库存
//            boolean success = seckillVoucherService.update().
//                    setSql("stock = stock - 1").
//                    eq("voucher_id", voucherId).
//                    gt("stock", 0).
//                    update();
//
//            if (!success) {
//                return Result.fail("库存不足");
//            }
//
//            // 创建订单
//            VoucherOrder voucherOrder = new VoucherOrder();
//            // 生成id
//            long orderId = redisIdWorker.nextId("order");
//            voucherOrder.setId(orderId);
//            voucherOrder.setUserId(userId);
//            voucherOrder.setVoucherId(voucherId);
//            save(voucherOrder);
//
//            return Result.ok(orderId);
//        } finally {
//            // 释放锁
//            lock.unLock();
//        }
//    }


//    /**
//     * 一人一单
//     */
//    @Transactional
//    public Result createVoucherOrder(Long voucherId) {
//        Long userId = UserHolder.getUser().getId();
//        Integer count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
//        if (count > 0) {
//            return Result.fail("用户已经购买过一次");
//        }
//
//        // 扣库存
//        boolean success = seckillVoucherService.update().
//                setSql("stock = stock - 1").
//                eq("voucher_id", voucherId).
//                gt("stock", 0).
//                update();
//
//        if (!success) {
//            return Result.fail("库存不足");
//        }
//
//        // 创建订单
//        VoucherOrder voucherOrder = new VoucherOrder();
//        // 生成id
//        long orderId = redisIdWorker.nextId("order");
//        voucherOrder.setId(orderId);
//        voucherOrder.setUserId(userId);
//        voucherOrder.setVoucherId(voucherId);
//        save(voucherOrder);
//
//        return Result.ok(orderId);
//    }


}
