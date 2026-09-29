


package com.redis_demo.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.redis_demo.dto.LoginFormDTO;
import com.redis_demo.dto.Result;
import com.redis_demo.dto.UserDTO;
import com.redis_demo.entity.User;
import com.redis_demo.mapper.UserMapper;
import com.redis_demo.service.IUserService;
import com.redis_demo.utils.RegexUtils;
import com.redis_demo.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.redis_demo.utils.RedisConstants.*;
import static com.redis_demo.utils.SystemConstants.USER_NICK_NAME_PREFIX;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private UserMapper userMapper;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result sendCode(String phone, HttpSession session) {
        if (!RegexUtils.isCodeInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
        ;

        String Code = RandomUtil.randomNumbers(6);

        //session.setAttribute("code",Code);

        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, Code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

        log.debug("发送验证码成功验证码为{}", Code);

        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String s = LOGIN_CODE_KEY + phone;
        if (!RegexUtils.isCodeInvalid(phone)) {
            return Result.fail("手机号格式错误");
        }
//        Object cachecode=session.getAttribute("code");
//        String code=loginForm.getCode();
//        if (cachecode==null||!cachecode.toString().equals(code)){
//            return Result.fail("验证码错误");
//        }
//        User user=query().eq("phone",phone).one();
//        if (user==null){
//            user= createUserwithPhone(phone);
//        }
//
//        session.setAttribute("user", BeanUtil.copyProperties(user,UserDTO.class));
        //从redis获取验证码以校验
        String cachecode = stringRedisTemplate.opsForValue().get(s);
        String code = loginForm.getCode();
        if (cachecode == null || !cachecode.toString().equals(code)) {
            return Result.fail("验证码错误");
        }
        User user = query().eq("phone", phone).one();
        if (user == null) {
            user = createUserwithPhone(phone);
        }
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> usrmap = BeanUtil.beanToMap(userDTO, new HashMap<>(), CopyOptions.create().
                setIgnoreNullValue(true).
                setFieldValueEditor((fieldname, fieldValue) -> fieldValue.toString()));
        String Tokenkey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(Tokenkey, usrmap);
        stringRedisTemplate.expire(Tokenkey, LOGIN_USER_TTL, TimeUnit.SECONDS);



        return Result.ok(token);
    }

    private User createUserwithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }

    @Override
    public Result loginout() {
        return Result.ok();
    }

    @Override
    public UserDTO queryUserbyId(Long id) {
        User user = userMapper.getUserById(id);
        if (user == null) {
            return null;
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);

        return userDTO;

    }

    @Override
    public Result sign() {
        Long userID = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userID.toString() + date;
        stringRedisTemplate.opsForValue().setBit(key, now.getDayOfMonth() - 1, true);
        return Result.ok();

    }

    @Override
    public Result signcount() {
        Long userID = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyyyMM"));
        String key = USER_SIGN_KEY + userID.toString() + date;
        int day = now.getDayOfMonth();
        List<Long> results = stringRedisTemplate.opsForValue().bitField(key, BitFieldSubCommands.create().
                get(BitFieldSubCommands.BitFieldType.unsigned(day)).valueAt(0));
        if (results == null || results.isEmpty()) {
            return Result.ok(0);
        }
        int count = 0;
        Long res = results.get(0);
        if (res == null || res == 0) {
            return Result.ok(0);
        }
        while (true) {
            if ((res & 1) == 0) {
                break;
            } else {
                count++;
            }
            res>>>=1;
        }
        return Result.ok(count);
    }
}
