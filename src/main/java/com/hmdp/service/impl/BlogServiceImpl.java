package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jodd.util.StringUtil;
import org.omg.CORBA.LongHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;
    @Resource
    StringRedisTemplate stringRedisTemplate;
    @Resource
    FollowMapper followMapper;

    @Override
    public Result saveblog(Blog blog) {
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        boolean success = save(blog);
        if (!success) {
            return Result.fail("新增笔记失败");
        }
        List<Follow> res = followMapper.selectfollowersbyid(user.getId());

        for (Follow f : res
        ) {
            Long userid = f.getUserId();
            String keys = FEED_KEY + userid;
            stringRedisTemplate.opsForZSet().add(keys, blog.getId().toString(), System.currentTimeMillis());
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogbyId(Long id) {

        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        blog.getUserId();
        queryuser(blog);
        isblogliked(blog);
        return Result.ok(blog);

    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            this.queryuser(blog);
            isblogliked(blog);

        });
        return Result.ok(records);
    }

    @Override
    public Result likeBlog(Long id) {
        String userID = String.valueOf(UserHolder.getUser().getId());
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userID);

        if (score == null) {
            Boolean success = update().setSql("liked = liked + 1").eq("id", id).update();
            if (success) {
                //从set变成zset  add key value score
                stringRedisTemplate.opsForZSet().add(key, userID, System.currentTimeMillis());
            }
        } else {
            boolean success1 = update().setSql("liked = liked -1").eq("id", id).update();
            if (success1) {
                stringRedisTemplate.opsForZSet().remove(key, userID);
            }
        }

        return Result.ok();
    }

    @Override
    public Result queryBloglikes(Long id) {

        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //2.解析出其中的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String join = StrUtil.join(",", ids);
        //3.根据用户id查询用户   加入orderby
        List<UserDTO> userDTOS = userService.query().
                in("id", ids).last("order by field (id," + join + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        //4.返回
        return Result.ok(userDTOS);
    }

    @Override
    public Result queryblogoffollow(Long max, Integer offset) {
        //获取当前用户
        UserDTO userDTO = UserHolder.getUser();
        //根据当前用户查询该用户关注的所有用户，实现分页查询
        String key = FEED_KEY + userDTO.getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 4);
        if (typedTuples == null||typedTuples.isEmpty()) {
            return Result.ok();
        }
        List<Long> ids = new ArrayList<>(typedTuples.size());

        long mintime = 0;
        int count = 1;
        //  解析数据，blogid mintime offset
        for (ZSetOperations.TypedTuple<String> t : typedTuples
        ) {
            String str = t.getValue();
            ids.add(Long.valueOf(str));
            Long time = t.getScore().longValue();
            if (time == mintime) {
                count++;
            } else {
               count=1;
               mintime=time;
            }
        }
        //根据blogid查询blog，封装并且返回
        String strid=StrUtil.join(",",ids);
        List<Blog> blogs = query().
                in("id", ids).last("order by field (id," + strid + ")").list();
        for (Blog blog : blogs) {
            isblogliked(blog);
            User user = userService.getById(blog.getUserId());
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }

        ScrollResult r = new ScrollResult();
        r.setList(blogs);
        r.setOffset(count);
        r.setMinTime(mintime);

        return Result.ok(r);

    }

    public void isblogliked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return;
        }
        String userID = String.valueOf(user.getId());
        String key = BLOG_LIKED_KEY + blog.getId();
        //Boolean islike=stringRedisTemplate.opsForSet().isMember(key, userID);
        Double islike = stringRedisTemplate.opsForZSet().score(key, userID);
        blog.setIsLike(islike != null);


    }

    private void queryuser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
