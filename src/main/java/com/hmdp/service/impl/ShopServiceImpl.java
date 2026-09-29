package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.rmi.CORBA.Util;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;

    @Override
    public Result querygetById(Long id) {
        //Shop shop = cacheClient.queryPassthrough(CACHE_SHOP_TTL,TimeUnit.MINUTES,
        // CACHE_SHOP_KEY, id,Shop.class,this::getById);
        Shop shop = cacheClient.QuerywithLogicExpire(CACHE_SHOP_KEY, LOCK_SHOP_KEY, id, Shop.class, this::getById,
                20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

/*
    //互斥锁解决缓存击穿
    private Shop querywithmutex(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //查缓存
        String coachstr = stringRedisTemplate.opsForValue().get(key);
        //缓存中有且不为空
        if (StrUtil.isNotBlank(coachstr)) {

            Shop shop = JSONUtil.toBean(coachstr, Shop.class);
            return shop;
        }
    缓存中有但是是空值
        if (coachstr != null) {
            return null;
        }
        //缓存中没有从mysql中查
        Shop shop = null;
        String lockkey = LOCK_SHOP_KEY + id;
        try {
            if (!trylock(lockkey)) {
                Thread.sleep(50);
                return querywithmutex(id);
            }
            shop = getById(id);
            Thread.sleep(200);
            if (shop == null) {
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //表中有，写入缓存并且返回
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            dellock(lockkey);
        }
        return shop;
    }

*/


    //逻辑时间解决缓存击穿
/*    private Shop QuerywithLogicExpire(Long id) {
        String key = CACHE_SHOP_KEY + id;
        //查缓存
        String coachstr = stringRedisTemplate.opsForValue().get(key);
        //缓存中没有
        if (StrUtil.isBlank(coachstr)) {
            return null;
        }
        RedisData redisData = JSONUtil.toBean(coachstr, RedisData.class);
        JSONObject jshop = (JSONObject) redisData.getData();
        Shop shop = JSONUtil.toBean(jshop, Shop.class);
        //未过期
        if (LocalDateTime.now().isBefore(redisData.getExpireTime())) {
            return shop;
        } else {
            //已过期获得锁
            if (trylock(LOCK_SHOP_KEY + id)) {
                COACH_REBOUND.submit(() -> {
                    try {
                        this.saveshop2Redis(id, 20L);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }finally {
                        dellock(LOCK_SHOP_KEY + id);
                    }
                });
            }
            return shop;
        }
    }*/


/*    public void saveshop2Redis(Long id, Long expiretime) throws InterruptedException {
//封装一个逻辑过期时间到shop中，为了不修改shop代码 嵌套一层redisdata，并将其存入缓存中。提前将热key加上逻辑过期时间添加到缓存中
        Shop shop = getById(id);
        Thread.sleep(200);
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expiretime));
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData)); 序列化
    }*/

    @Transactional
    @Override
    public Result coupdateshop(Shop shop) {

        /*
        修改缓存的方式
        String shopid=stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY+shop.getId());
        if (StrUtil.isBlank(shopid)){
           QueryChainWrapper<Shop> shop1=query().eq("id",shop.getId());
           if (shop1==null||shop1.isEmptyOfEntity()){
               return Result.fail("没找到对应的商铺");
           }
           updateById(shop);
           stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+shop.getId(),JSONUtil.toJsonStr(shop));
           return Result.ok();
        }
        updateById(shop);
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY+shop.getId(),JSONUtil.toJsonStr(shop));
        return Result.ok();
        */
        //删除重写缓存的方式
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("id不能为空");
        }
        updateById(shop);
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        //不需坐标查询
        if (x == null || y == null) {
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        //计算分页大小
        int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
        int end = current * SystemConstants.DEFAULT_PAGE_SIZE;
        String key = SHOP_GEO_KEY + typeId;
        //redis分页查询
        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(key, GeoReference.fromCoordinate(x, y), new Distance(5000),
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end));

        if (results == null) {
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> content = results.getContent();
        List<String> ids = new ArrayList<>(content.size());
        Map<String, Distance> distanceMa = new HashMap<>();
        if (content.size()<from){
            return Result.ok(Collections.emptyList());
        }
        content.stream().skip(from).forEach(result -> {
            String shopid = result.getContent().getName();
            ids.add(shopid);
            Distance distance = result.getDistance();
            distanceMa.put(shopid, distance);
        });
        String str = StrUtil.join(",", ids);
        List<Shop> shops = query().in("id", ids).last("order by field (id," + str + ")").list();

        shops.stream().forEach(shop -> {
            shop.setDistance(distanceMa.get(shop.getId().toString()).getValue());
        });

        return Result.ok(shops);
    }
}
