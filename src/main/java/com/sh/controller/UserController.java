package com.sh.controller;

import cn.hutool.core.bean.BeanUtil;
import com.sh.dto.LoginFormDTO;
import com.sh.dto.Result;
import com.sh.dto.UserDTO;
import com.sh.dto.UserHolder;
import com.sh.entry.User;
import com.sh.entry.UserInfo;
import com.sh.service.IUserInfoService;
import com.sh.service.IUserService;
import com.sh.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;


    @GetMapping("/sign")
    public Result sign() {
        return userService.sign();
    }

    @GetMapping("/signCount")
    public Result signCount() {
        return userService.signCount();
    }

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        return userService.sendCode(phone, session);
    }
    @PostMapping("login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session) {
        System.out.println(session.getId());
        return userService.login(loginForm, session);
    }

    @GetMapping("me")
    public Result me(){
        // 获取当前登录的用户并返回
        UserDTO user = UserHolder.getUser();
        return Result.ok(user);
    }

    @GetMapping("/{id}")
    public Result queryUserById(@PathVariable("id") Long userId) {
        User user = userService.getById(userId);
        if (user == null) {
            return Result.ok("当前用户不存在");
        }
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    @GetMapping("/info/{id}")
    public Result queryUserInfo(@PathVariable("id") Long userId) {
        UserInfo userInfo = userInfoService.getById(userId);
        // 查询详情
        if (userInfo == null) {
            // 没有详情，应该是第一次查看详情
            return Result.ok();
        }
        userInfo.setCreateTime(null);
        userInfo.setUpdateTime(null);
        return Result.ok(userInfo);
    }


    @PostMapping("/logout")
    public Result logout(HttpServletRequest request) {
        String token = request.getHeader("authorization");
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        return userService.logout(tokenKey);
    }

}
