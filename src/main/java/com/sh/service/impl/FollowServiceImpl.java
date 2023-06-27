package com.sh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sh.dto.Result;
import com.sh.dto.UserDTO;
import com.sh.dto.UserHolder;
import com.sh.entry.Follow;
import com.sh.mapper.FollowMapper;
import com.sh.service.IFollowService;
import com.sh.service.IUserService;
import com.sh.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private IUserService userService;

    @Override
    public Result isFollow(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        Integer count = query()
                .eq("user_id", userId)
                .eq("follow_user_id", followUserId).count();
        return Result.ok(count > 0);
    }

    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long userId = UserHolder.getUser().getId();
        if (isFollow) {
            // 关注用户
            Follow follow = new Follow();
            follow.setUserId(userId);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess) {
                stringRedisTemplate.opsForSet().add(RedisConstants.FOLLOW_KEY + userId, followUserId.toString());
            }
        } else {
            // 取消关注
            boolean isSuccess = remove(new QueryWrapper<Follow>().eq("follow_user_id", followUserId).eq("user_id", userId));
            if (isSuccess) {
                stringRedisTemplate.opsForSet().remove(RedisConstants.FOLLOW_KEY + userId, followUserId.toString());
            }
        }
        return Result.ok();
    }


    @Override
    public Result followCommons(Long followUserId) {
        Long userId = UserHolder.getUser().getId();
        String key1 = RedisConstants.FOLLOW_KEY + userId;
        String key2 = RedisConstants.FOLLOW_KEY + followUserId;

        Set<String> intersect = stringRedisTemplate.opsForSet().intersect(key1, key2);
        if (intersect == null || intersect.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        List<Long> ids = intersect.stream().map(Long::valueOf).collect(Collectors.toList());
        List<UserDTO> collect = userService.listByIds(ids).stream().map(user -> BeanUtil.copyProperties(user, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(collect);
    }
}
