package com.sh.config;

import com.sh.interceptor.LoginInterceptor;
import com.sh.interceptor.RefreshTokenInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {


    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // addInterceptors 添加拦截器
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 添加拦截器并排除不需要拦截的路径，即不用登录也可以访问的页面
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(
                        "/shop/**",
                        "/voucher/**",
                        "/user/login",
                        "/user/code",
                        "/shop-type/**",
                        "/upload/**"
                ).order(1);
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/code",
                        "/shop/**"
                ).order(0);
    }
}
