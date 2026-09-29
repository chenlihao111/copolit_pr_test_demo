package com.redis_demo.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIdWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    private static final long BEGIN_TIMESTAMP = 1640995200L;
    private static final long COUNT_BIT = 32L;
    public Long getnextId(String prefix){
        //时间戳
        LocalDateTime nowtime=LocalDateTime.now();
        long Cursecond=nowtime.toEpochSecond(ZoneOffset.UTC)-BEGIN_TIMESTAMP;

        String date= nowtime.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //序列号
        long str=stringRedisTemplate.opsForValue().increment("icr"+prefix+":"+date);
        //拼接
        return Cursecond <<COUNT_BIT|str ;
    }

}
