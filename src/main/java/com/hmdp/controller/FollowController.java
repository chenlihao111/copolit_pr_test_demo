package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import com.hmdp.service.impl.FollowServiceImpl;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService ifollowService;

    @PutMapping("/{id}/{isfollow}")
    public Result follow(@PathVariable("id") Long followid, @PathVariable("isfollow") boolean isfollow) {

        return ifollowService.follow(followid,isfollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isfollowed(@PathVariable("id") Long followid) {
        return ifollowService.isfollowed(followid);
    }


    @GetMapping("/common/{id}")
    public Result commenfollow(@PathVariable("id") Long id){
        return ifollowService.getcommonfollow(id);
    }
}
