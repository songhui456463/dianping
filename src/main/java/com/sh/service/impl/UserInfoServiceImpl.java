package com.sh.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sh.entry.UserInfo;
import com.sh.mapper.UserInfoMapper;
import com.sh.service.IUserInfoService;
import com.sh.service.IUserService;
import org.springframework.stereotype.Service;

@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements IUserInfoService {
}
