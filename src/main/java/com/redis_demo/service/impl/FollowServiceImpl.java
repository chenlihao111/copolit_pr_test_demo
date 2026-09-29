package com.redis_demo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.redis_demo.dto.Result;
import com.redis_demo.dto.UserDTO;
import com.redis_demo.entity.Follow;
import com.redis_demo.entity.User;
import com.redis_demo.mapper.FollowMapper;
import com.redis_demo.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redis_demo.service.IUserService;
import com.redis_demo.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
