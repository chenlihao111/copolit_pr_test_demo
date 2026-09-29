package com.redis_demo.service;

import com.redis_demo.dto.Result;
import com.redis_demo.entity.Blog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<Blog> {

    Result saveblog(Blog blog);



    Result queryBlogbyId(Long id);

    Result queryHotBlog(Integer current);

    Result likeBlog(Long id);

    Result queryBloglikes(Long id);


    Result queryblogoffollow(Long max, Integer offset);
}
