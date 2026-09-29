package com.redis_demo.service;

import com.redis_demo.dto.Result;
import com.redis_demo.entity.Follow;
import com.baomidou.mybatisplus.extension.service.IService;
import org.apache.ibatis.annotations.Param;

/**
 * <p>
 *  服务类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IFollowService extends IService<Follow> {

    Result follow(@Param("follow_id") Long followid, boolean isfollow);

    Result isfollowed(Long followid);

    Result getcommonfollow(Long id);
}
