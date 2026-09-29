package com.redis_demo;

import com.redis_demo.entity.Shop;
import com.redis_demo.service.IVoucherOrderService;
import com.redis_demo.service.impl.ShopServiceImpl;
import com.redis_demo.utils.CacheClient;
import com.redis_demo.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.redis_demo.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.redis_demo.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class ApplicationTests {
    @Resource
    private ShopServiceImpl shopService;
    @Resource
    private CacheClient cacheClient;
    @Resource
    private RedisIdWorker redisIdWorker;
    @Resource
    private IVoucherOrderService iVoucherOrderService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Test
    void savedata2redis() throws InterruptedException {
        //shopService.saveshop2Redis(1L,30L);
        Shop shop = shopService.getById(1L);
        cacheClient.setLogicalExprired(CACHE_SHOP_KEY + 1, shop, 30L, TimeUnit.SECONDS);
    }
    @Test
    void loadshop(){

        List<Shop> list = shopService.list();

        Map<Long,List<Shop>> map=list.stream().collect(Collectors.groupingBy(Shop::getTypeId));

        for (Map.Entry<Long,List<Shop>> entry: map.entrySet()
             ) {
            String key=SHOP_GEO_KEY+entry.getKey().toString();
            List<Shop> list1=entry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations=new ArrayList<>();
            for (Shop s:list1
                 ) {
                locations.add(new RedisGeoCommands.GeoLocation<>(s.getId().toString(),new Point(s.getX(),s.getY())));
               // stringRedisTemplate.opsForGeo().add(key,new Point(s.getX(),s.getY()),s.getId().toString());
            }
            stringRedisTemplate.opsForGeo().add(key,locations);

        }



    }


}