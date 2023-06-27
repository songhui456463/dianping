package com.sh.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.sh.dto.Result;
import com.sh.dto.UserDTO;
import com.sh.dto.UserHolder;
import com.sh.entry.Blog;
import com.sh.entry.Follow;
import com.sh.entry.User;
import com.sh.mapper.BlogMapper;
import com.sh.service.IBlogService;
import com.sh.service.IFollowService;
import com.sh.service.IUserService;
import com.sh.utils.RedisConstants;
import com.sh.utils.ScrollResult;
import com.sh.utils.SystemConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Autowired
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryBlogLikes(Long id) {
        // 根据score(最新点赞的5个用户)查询top点赞用户id
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 解析用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 根据用户id查询用户 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query().
                in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    @Override
    public Result likeBlog(Long id) {
        // 当前用户id
        Long userId = UserHolder.getUser().getId();
        // 判断当前用户是否已经点赞
        String key = RedisConstants.BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 当前用户未点赞，点赞数加一
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                // 保存至redis数据库中，并增加分数，
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 用户已经点赞，点赞数减一
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                // 保存至redis数据库中，并增加分数，
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        records.forEach(blog -> {
            queryBlogByUser(blog);
            isBlogLiked(blog);
        });
        return Result.ok(records);
    }

    private void isBlogLiked(Blog blog) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 未登录
            return;
        }

        String key = RedisConstants.BLOG_LIKED_KEY + blog.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, user.getId().toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店笔记
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("新增笔记失败！");
        }
        // TODO 将笔记推荐给所有粉丝
        List<Follow> follows = followService.query().eq("follow_user_id", user.getId()).list();
        follows.forEach(follow ->{
            // 粉丝id
            Long userId = follow.getUserId();
            // 推送至粉丝收件箱
            String key = RedisConstants.FEED_KEY + userId;
            // 对于的点评id推送给粉丝,分数为时间戳
            stringRedisTemplate.opsForZSet().add(key, blog.getId().toString(), System.currentTimeMillis());
        });
        return Result.ok(blog.getId());
    }

    @Override
    public Result queryBlogOfFollow(Long max, Integer offset) {
        Long userId = UserHolder.getUser().getId();
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, offset, 2);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }

        long minTime = 0L;
        int count = 1;
        List<Long> ids = new ArrayList<>(typedTuples.size());
        for (ZSetOperations.TypedTuple<String> typedTuple :typedTuples) {
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));
            long time = Objects.requireNonNull(typedTuple.getScore()).longValue();

            if (time == minTime) {
                // 出现重复分数
                count++;
            } else {
                minTime = time;
                // 有可能中间出现重复分数
                count = 1;
            }
        }

        String idsStr = StrUtil.join(",", ids);

        List<Blog> blogs = query().in("user_id", ids).last("order by field(user_id," + idsStr + ")").list();

        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(count);
        scrollResult.setMinTime(minTime);
        return Result.ok(scrollResult);
    }

    @Override
    public Result queryById(Long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在");
        }
        // 查询发布博客的用户
        queryBlogByUser(blog);
        return Result.ok(blog);
    }

    private void queryBlogByUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }
}
