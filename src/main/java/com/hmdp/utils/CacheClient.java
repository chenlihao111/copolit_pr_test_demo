package com.hmdp.utils;


import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.rmi.CORBA.Util;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    @Resource
    private final StringRedisTemplate stringRedisTemplate;


    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit timeUnit){

        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,timeUnit);

    }


    public void setLogicalExprired(String key, Object value, Long time, TimeUnit timeUnit){

        RedisData redisData=new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(timeUnit.toSeconds(time)));
        redisData.setData(value);
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //缓存穿透空值
    public <R,ID> R  queryPassthrough(Long time, TimeUnit timeUnit,String Prefix, ID id, Class<R> type, Function<ID,R> dbfunction){

        String key=Prefix+id;
        String json=stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(json)){
            return JSONUtil.toBean(json,type);
        }
        if (json!=null){
            return null;
        }
        R r=dbfunction.apply(id);
        if (r==null){
            stringRedisTemplate.opsForValue().set(key,"");
            return null;
        }
        this.set(key,JSONUtil.toJsonStr(r),time,timeUnit);

        return r;

    }
    public static final ExecutorService COACH_REBOUND = Executors.newFixedThreadPool(10);
    //缓存击穿逻辑时间
    public  <R,ID> R QuerywithLogicExpire(String Prefixid,String lockprefix,ID id,Class<R> type,
                                          Function<ID,R> dbfunction, Long time, TimeUnit timeUnit) {
        String key = Prefixid + id;
        //查缓存
        String json = stringRedisTemplate.opsForValue().get(key);
        //缓存中没有
        if (StrUtil.isBlank(json)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        JSONObject j= (JSONObject) redisData.getData();
        R r = JSONUtil.toBean(j, type);  //从redisdata中提取出data(redisdata由data和expiretime组成,data为之前封装的类，)
        //缓存中有且未过期
        if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
            return r;
        } else {
            //已过期获得锁
            if (trylock(lockprefix + id)) {
                COACH_REBOUND.submit(() -> { //重建一个新的线程完成更新和释放锁的操作
                    try {
                        R r1=dbfunction.apply(id);
                        this.setLogicalExprired(key,r1,time,timeUnit);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }finally {
                        dellock(lockprefix + id);
                    }
                });
            }
            return r;
        }
    }
    private boolean trylock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.MINUTES);

        return BooleanUtil.isTrue(flag);
    }

    private void dellock(String key) {

        stringRedisTemplate.delete(key);
    }



}
