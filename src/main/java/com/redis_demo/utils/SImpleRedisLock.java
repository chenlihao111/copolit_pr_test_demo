package com.redis_demo.utils;


import com.redis_demo.service.ILock;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SImpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String name;
    private static final String KEY_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString() + "_";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<Long>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("Unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SImpleRedisLock(StringRedisTemplate stringRedisTemplate, String name) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.name = name;
    }

    @Override
    public boolean tryLock(Long timeoutSec) {
        String id = ID_PREFIX + Thread.currentThread().getId();
        Boolean res = stringRedisTemplate.opsForValue().setIfAbsent(
                KEY_PREFIX + name, id, timeoutSec, TimeUnit.SECONDS);
        //包含了自动拆箱过程，如果有自动拆箱则可能会出现空指针，此处res就算为空也会返回false
        return Boolean.TRUE.equals(res);
    }

    @Override
    public void unlock() {
        /*
        //线程标识
        String Threadid = ID_PREFIX + Thread.currentThread().getId();
        //redis中锁的value
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);
        if (Threadid.equals(id)) {
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }*/
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(KEY_PREFIX + name),
                ID_PREFIX + Thread.currentThread().getId());


    }


}
