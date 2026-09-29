package com.hmdp.mapper;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface FollowMapper extends BaseMapper<Follow> {
    boolean deleteById(@Param("userid") Long userid, @Param("followid")  Long followid);

    int selectById(@Param("userid") Long userid,@Param("followid")  Long followid);

    List<Follow> selectfollowersbyid(@Param("followuserid") Long followuserid);

}
