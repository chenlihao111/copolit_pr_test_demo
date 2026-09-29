package com.redis_demo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.redis_demo.dto.Result;
import com.redis_demo.entity.VoucherOrder;
import com.redis_demo.mapper.VoucherOrderMapper;
import com.redis_demo.service.ISeckillVoucherService;
import com.redis_demo.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redis_demo.utils.RedisIdWorker;
import com.redis_demo.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Resource
    private ISeckillVoucherService iSeckillVoucherService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    private static final DefaultRedisScript<Long> SECKILL_ORDER_SCRIPT;

    //private BlockingQueue<VoucherOrder> ordertask = new ArrayBlockingQueue<>(1024 * 1024);

    private final ExecutorService seckill_order_execute = new ThreadPoolExecutor(1, 1, 10, TimeUnit.MINUTES,
            new ArrayBlockingQueue<>(1));

    static {
        SECKILL_ORDER_SCRIPT = new DefaultRedisScript<Long>();
        SECKILL_ORDER_SCRIPT.setLocation(new ClassPathResource("jug_seckill_stock.lua"));
        SECKILL_ORDER_SCRIPT.setResultType(Long.class);
    }

    @PostConstruct
    public void init() {
        // 1. 创建消费者组
        try {
            stringRedisTemplate.opsForStream().createGroup("stream.orders", ReadOffset.latest(), "g1");
        } catch (RedisSystemException e) {
            // 若组已存在，忽略 BUSYGROUP 异常；其他异常则抛出
            if (!e.getMessage().contains("BUSYGROUP")) {
                throw e;
            }
        }
        // 2. 启动订单处理线程
        seckill_order_execute.submit(new seckillorderhandler());
    }
   // 通过阻塞队列实现的
    /*
        private class seckillorderhandler implements Runnable {

            @Override
            public void run() {
                while (true) {
                    try {
                        VoucherOrder voucherOrder = ordertask.take();
                        handlervouerOrder(voucherOrder);
                    } catch (Exception e) {
                        log.error("下单失败", e);
                    }
                }

            }
        }
*/
    //通过消息队列实现
    private class seckillorderhandler implements Runnable {
        String qname="stream.orders";
        @Override
        public void run() {
            while (true) {
                //获取消息队列中的订单信息XREADGROUPGROUPg1 c1 COUNT 1 BLOCK2OOOSTREAMS streams.order >
                try {
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1","c1"), StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create(qname, ReadOffset.lastConsumed())
                    );

                    if (list==null||list.isEmpty()){
                       continue;
                    }
                    //从消息队列的结果list中解析
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherorder=BeanUtil.fillBeanWithMap(values,new VoucherOrder(),true) ;
                    //创建订单
                    handlervouerOrder(voucherorder);

                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(qname,"g1",record.getId());
                } catch (Exception e) {
                    log.error("订单处理异常");
                    handpendinglist();
                }
            }

        }

        private void handpendinglist() {
            while (true){
                //获取pending-list中的订单信息
                try {
                    List<MapRecord<String, Object, Object>> list1 = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"), StreamReadOptions.empty().count(1),
                            StreamOffset.create(qname, ReadOffset.from("0"))
                    );
                    if (list1==null||list1.isEmpty()){
                            break;
                    }
                    //从消息队列的结果list中解析
                    MapRecord<String, Object, Object> record = list1.get(0);
                    Map<Object, Object> values = record.getValue();
                    VoucherOrder voucherorder=BeanUtil.fillBeanWithMap(values,new VoucherOrder(),true) ;
                    //创建订单
                    handlervouerOrder(voucherorder);
                    //ACK确认
                    stringRedisTemplate.opsForStream().acknowledge(qname,"g1",record.getId());
                } catch (RuntimeException e) {
                    log.error("处理pentlist异常");
                    e.printStackTrace();
                    try {
                        Thread.sleep(20000);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }

            }
        }
    }


    public IVoucherOrderService proxy;
    private void handlervouerOrder(VoucherOrder voucherOrder) {
        Long userID = voucherOrder.getUserId();
        RLock lock = redissonClient.getLock("order" + userID);
        boolean islock = lock.tryLock(); //默认不等待  30s后释放锁
        if (!islock) {
            Result.fail("一人只能一单");
        }
        try {
            proxy.CreateVoucherOrder(voucherOrder);
        } finally {
            lock.unlock();
        }
    }


    @Override
/*
    public Result seckillVoucher(Long voucherId) {

        SeckillVoucher seckillVoucher = iSeckillVoucherService.getById(voucherId);
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {

            return Result.fail("秒杀尚未开始");
        }
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀已经结束");
        }

        if (seckillVoucher.getStock() < 1) {
            return Result.fail("库存不足");
        }

        Long userID = UserHolder.getUser().getId();
         /*一人一单单机实现
        synchronized (userID.toString().intern()) {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.CreateVoucherOrder(voucherId);  }


        手动构建锁  工具类不是由spring管理的，使用时要手动注入
        SImpleRedisLock lock= new SImpleRedisLock(stringRedisTemplate, "order" + userID);


        //redisson的锁
        RLock lock = redissonClient.getLock("order" + userID);
        boolean islock = lock.tryLock(); //默认不等待  30s后释放锁
        if (!islock) {
            return Result.fail("一人只能一单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.CreateVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }
        */

    /*

    public Result seckillVoucher(Long voucherId) {

        String userid = UserHolder.getUser().getId().toString();
        //lua脚本执行 判断是否达到下单条件
        Long res = stringRedisTemplate.execute(SECKILL_ORDER_SCRIPT, Collections.emptyList(), voucherId.toString(), userid);
        int r = res.intValue();
        if (r != 0) {
            return Result.fail(r == 1 ? "库存不足" : "你已经下过单");
        }
        //TODO 保存到阻塞队列
        long orderId = redisIdWorker.getnextId("order");
        //封装
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(orderId);//订单id
        voucherOrder.setUserId(UserHolder.getUser().getId());//用户id
        voucherOrder.setVoucherId(voucherId);//代金券id
        //保存阻塞队列
        ordertask.add(voucherOrder);
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(orderId);
    }
    */

public Result seckillVoucher(Long voucherId) {

    String userid = UserHolder.getUser().getId().toString();
    long orderId = redisIdWorker.getnextId("order");
    String id = String.valueOf(orderId); //将订单id转化为字符串类型传入lua脚本
    //lua脚本执行 判断是否达到下单条件
    Long res = stringRedisTemplate.execute(SECKILL_ORDER_SCRIPT, Collections.emptyList(),
            voucherId.toString(), userid, id);
    int r = res.intValue();
    if (r != 0) {
        return Result.fail(r == 1 ? "库存不足" : "你已经下过单");
    }
    //获取代理对象
    proxy = (IVoucherOrderService) AopContext.currentProxy();

    return Result.ok(orderId);
}


/*
    @Transactional
    public Result CreateVoucherOrder(Long voucherId) {
        Long userID = UserHolder.getUser().getId();

        int res = query().eq("user_id", userID).eq("voucher_id", voucherId).count();
        if (res > 0) {
            return Result.fail("你已经下过单了");
        }
        //数据库更新
        boolean success = iSeckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!success) {
            return Result.fail("库存不足");
        }
        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //订单ID
        Long orderID = redisIdWorker.getnextId("order");
        voucherOrder.setId(orderID);
        voucherOrder.setVoucherId(voucherId);
        //用户ID
        voucherOrder.setUserId(userID);
        //优惠券ID
        voucherOrder.setVoucherId(voucherId);
        //保存订单到数据库中
        save(voucherOrder);
        return Result.ok(orderID);

    }

   */
    @Transactional
    public void CreateVoucherOrder(VoucherOrder voucherOrder) {
        Long userID = voucherOrder.getUserId();
        Long voucherId = voucherOrder.getVoucherId();
        int res = query().eq("user_id", userID).eq("voucher_id", voucherId).count();
        if (res > 0) {
            Result.fail("你已经下过单了");
        }
        //数据库更新
        boolean success = iSeckillVoucherService.update().setSql("stock=stock-1").eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (!success) {
            Result.fail("库存不足");
        }
        //创建订单
        long orderId = redisIdWorker.getnextId("order");//订单id
        voucherOrder.setId(orderId);
        voucherOrder.setUserId(userID);
        voucherOrder.setVoucherId(voucherOrder.getVoucherId());//代金券id
        save(voucherOrder);

    }
}
