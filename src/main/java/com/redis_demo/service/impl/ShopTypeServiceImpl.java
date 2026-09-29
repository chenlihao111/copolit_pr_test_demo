package com.redis_demo.service.impl;

import cn.hutool.json.JSONUtil;
import com.redis_demo.dto.Result;
import com.redis_demo.entity.ShopType;
import com.redis_demo.mapper.ShopTypeMapper;
import com.redis_demo.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

import java.util.ArrayList;
import java.util.List;

import static com.redis_demo.utils.RedisConstants.CACHE_SHOP_TYPE_KEY;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result getqueryTypeList() {

       List<String> list= stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY,0,-1);

       if (list!=null&&!list.isEmpty()){
          List<ShopType> listtye=new ArrayList<>();
           for (String s:list
                ) {
               listtye.add(JSONUtil.toBean(s,ShopType.class));
           }
           return Result.ok(listtye);
       }

        List<ShopType> typeList = query().orderByAsc("sort").list();

       if (typeList.isEmpty()){
           return Result.fail("未查到商铺类型");
       }

        for (ShopType shopType:typeList
             ) {
            stringRedisTemplate.opsForList().leftPush(CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(shopType));
        }
        return Result.ok(typeList);
    }
}
