package com.sh.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.sh.dto.LoginFormDTO;
import com.sh.dto.Result;
import com.sh.entry.User;
import com.sh.utils.RegexUtils;

import javax.servlet.http.HttpSession;

public interface IUserService extends IService<User>{

    /**
     * 用户注册，发送手机号
     * @param phone 手机号
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 用户登录
     * @param loginForm 登录信息
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);

    /**
     * 退出登录
     * @param tokenKey redis中用户登录token
     * @return
     */
    Result logout(String tokenKey);

    /**
     * 签到
     * @return
     */
    Result sign();

    /**
     * 统计连续签到天数
     * @return
     */
    Result signCount();
}
