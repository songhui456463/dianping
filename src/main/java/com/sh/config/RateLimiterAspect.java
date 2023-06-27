package com.sh.config;

import com.sh.exception.ServiceException;
import com.sh.utils.LimitType;
import com.sh.utils.RateLimiter;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

@Aspect
@Component
public class RateLimiterAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterAspect.class);

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RedisScript<Long> limitScript;

    @Before("@annotation(rateLimiter)")
    public void deBefore(JoinPoint point, RateLimiter rateLimiter) {
        String key = rateLimiter.key();
        int count = rateLimiter.count();
        int time = rateLimiter.time();

        String combineKey = getCombineKey(rateLimiter, point);
        List<String> keys = Collections.singletonList(combineKey);

        Long number = redisTemplate.execute(limitScript, keys, String.valueOf(count), String.valueOf(time));
        if (number == null || number.intValue() > count) {
            throw new ServiceException("访问过于频繁，请稍候再试");
        }
        log.info("限制请求'{}',当前请求'{}',缓存key'{}'", count, number.intValue(), key);
    }

    public String getCombineKey(RateLimiter rateLimiter, JoinPoint point) {
        // rate_limit:127.0.0.1-org.javaboy.ratelimiter.controller.HelloController-hello
        // 前缀 + IP + 类路径 + 方法名
        StringBuffer sb = new StringBuffer(rateLimiter.key());
        if (rateLimiter.limitType() == LimitType.IP) {
            sb.append(((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest().getRemoteAddr()).append("-");
        }
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Class<?> targetClass = method.getDeclaringClass();
        sb.append(targetClass.getName()).append("-").append(method.getName());

        return sb.toString();
    }


}
