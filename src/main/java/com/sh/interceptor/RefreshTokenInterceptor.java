package com.sh.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.sh.dto.UserDTO;
import com.sh.dto.UserHolder;
import com.sh.utils.RedisConstants;
import com.sh.utils.VisitCount;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    private HashMap<String, AtomicInteger> mp = new HashMap<>();

    // 构造注入
    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
//        // 获取session
//        HttpSession session = request.getSession();
//        //
//        Object user = session.getAttribute("user");
        // 从请求头获取token
        String token = request.getHeader("authorization");
        // 必须是已登录的用户，否则token为空
        if (StrUtil.isBlank(token)) {
            response.setStatus(401);
            return false;
        }

        // 从redis中获取用户信息
        String tokenKey = RedisConstants.LOGIN_USER_KEY + token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        // 判断userMap是否为空
        if (userMap.isEmpty()) {
            response.setStatus(401);
            return false;
        }

        // 接口访问数量控制
//        if (handler instanceof HandlerMethod) {
//            HandlerMethod handlerMethod = (HandlerMethod) handler;
//            Method method = handlerMethod.getMethod();
//            String name = method.getName();
//            VisitCount annotation = method.getAnnotation(VisitCount.class);
//            if (annotation != null) {
//                AtomicInteger ai = mp.getOrDefault(name, new AtomicInteger(0));
//                ai.incrementAndGet();
//                mp.put(name, ai);
//            }
//        }

        UserDTO user = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        // 存在，将其保存到 ThreadLocal 中
        UserHolder.saveUser(user);

        // 6、刷新 token 过期时间
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户，避免内存泄露
        // 视图渲染完成后执行 afterCompletion 方法，也就是说一次请求获取一次用户信息，用完立即释放
        UserHolder.removeUser();
    }

}
