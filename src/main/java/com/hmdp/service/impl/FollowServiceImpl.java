package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.apache.ibatis.annotations.Mapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.DeleteMapping;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Resource
    private FollowMapper followMapper;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IUserService userService;

    @Override
    public Result follow(Long followid, boolean isfollow) {
        Long userid = UserHolder.getUser().getId();
        String key="follow_userid"+userid;

        if (isfollow) {
            Follow follow = new Follow();
            follow.setFollowUserId(followid);
            follow.setUserId(userid);
            boolean isok=save(follow);
            if (isok){
                stringRedisTemplate.opsForSet().add(key,followid.toString());
            }
        } else {
            //remove(new QueryWrapper<Follow>().eq("user_id",userid).eq("follow_user_id",followid));
            boolean issuccess = followMapper.deleteById(userid, followid);
            if (issuccess) {
                stringRedisTemplate.opsForSet().remove(key, followid.toString());
            }
        }

        return Result.ok();
    }

    @Override
    public Result isfollowed(Long followid) {
        Long userid = UserHolder.getUser().getId();
        if (followMapper.selectById(userid, followid) > 0) {
            return Result.ok(true);
        }
        return Result.ok(false);
    }

    @Override
    public Result getcommonfollow(Long id) {
        Long userid=UserHolder.getUser().getId();
        String key="follow_userid"+userid;
        String key2="follow_userid"+id;

        Set<String> res=stringRedisTemplate.opsForSet().intersect(key,key2);
        if (res==null||res.isEmpty()){
            return Result.fail("无共同关注");
        }
        //解析出id
        List<Long> list= res.stream().map(Long::valueOf).collect(Collectors.toList());
        List<User> users = userService.listByIds(list);

        List<UserDTO> resDto = users.stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());

        return Result.ok(resDto);
    }
}
