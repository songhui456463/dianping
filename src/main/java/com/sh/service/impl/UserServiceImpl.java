package com.sh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sh.dto.LoginFormDTO;
import com.sh.dto.Result;
import com.sh.dto.UserDTO;
import com.sh.dto.UserHolder;
import com.sh.entry.User;
import com.sh.mapper.UserMapper;
import com.sh.service.IUserService;
import com.sh.utils.RedisConstants;
import com.sh.utils.RegexUtils;
import com.sh.utils.SystemConstants;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    public Result sendCode(String phone, HttpSession session) {

        // 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 生成验证码
        String code = RandomUtil.randomNumbers(6);
//        session.setAttribute("code", code);

        // 保存验证码到redis
        redisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 发送验证码
        log.debug("发送验证码成功，验证码为{}", code);
        return Result.ok(code);
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        // 1 校验手机
        if (RegexUtils.isPhoneInvalid(phone)) {
            return Result.fail("手机号格式错误！");
        }

        // 2 校验验证码
//        String cacheCode = (String) session.getAttribute("code");
        String cacheCode = redisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        if (RegexUtils.isCodeInvalid(code)) {
            return Result.fail("验证码格式错误！");
        }

        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证码错误！");
        }

        // 3 查询当前用户是否已经注册
        User user = query().eq("phone", phone).one();

        if (user == null) {
            user = createUserWithPhone(phone);
        }

        // session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        // 保存用户信息到redis中
        String tokenKey = UUID.randomUUID().toString(true);
        // 将user转为hashmap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO,
                new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        // 存入redis
        redisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + tokenKey, userMap);
        // 设置过期时间
        redisTemplate.expire(RedisConstants.LOGIN_USER_KEY + tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 将token返回给客户端保存
        return Result.ok(tokenKey);
    }


    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX);
        save(user);
        return user;
    }

    @Override
    public Result logout(String tokenKey) {
        Boolean isSuccess = redisTemplate.delete(tokenKey);
        return Result.ok(isSuccess);
    }

    @Override
    public Result signCount() {
        Long userId = UserHolder.getUser().getId();
        LocalDateTime now = LocalDateTime.now();
        String keyPrefix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 拼接key
        String key = RedisConstants.USER_SIGN_KEY + userId + keyPrefix;
        // 获取当前是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        // 截取当前用户签到表的[0, dayOfMonth]数据
        List<Long> result = redisTemplate.opsForValue().bitField(
                key,
                BitFieldSubCommands.create().get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth)).valueAt(0)
        );

        if (result == null || result.isEmpty()) {
            return Result.ok(0);
        }

        Long num = result.get(0);

        if (num == null || num == 0) {
            return Result.ok(0);
        }

        int count = 0;
        while (true) {
            if ((num & 1) == 0) {
                break;
            } else {
                count++;
            }
            // 把数字右移一位，抛弃最后一个 bit 位，继续下一个 bit 位
            // >> :右移 最高位是0，左边补齐0;最高为是1，左边补齐1
            // << :左移 左边最高位丢弃，右边补齐0
            // >>>:无符号右移 无论最高位是0还是1，左边补齐0
            // num >>>= 1 ————> num = num >>> 1
            num >>>= 1;
        }
        return Result.ok(count);
    }

    @Override
    public Result sign() {
        // 获取当前登录用户id
        Long userId = UserHolder.getUser().getId();
        // 签到key
        LocalDateTime now = LocalDateTime.now();
        String keyPrefix = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));
        // 拼接key
        String key = RedisConstants.USER_SIGN_KEY + userId + keyPrefix;
        // 获取当前是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        redisTemplate.opsForValue().setBit(key, dayOfMonth - 1, true);
        return Result.ok();
    }
}
