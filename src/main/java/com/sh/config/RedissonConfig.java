package com.sh.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedissonConfig {

    @Value(value = "${spring.redis.host}")
    private String redisPath;

    @Value(value = "${spring.redis.port}")
    private int port;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://" + redisPath + ":" + port);
        return Redisson.create(config);
    }

    @Bean
    public DefaultRedisScript<Long> limitScript() {
        DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("limit.lua")));
        redisScript.setResultType(Long.class);
        return redisScript;
    }

//    @Bean
//    public RedissonClient redissonClient2() {
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://" + redisPath + ":6380");
//        return Redisson.create(config);
//    }
//
//    @Bean
//    public RedissonClient redissonClient3() {
//        Config config = new Config();
//        config.useSingleServer().setAddress("redis://" + redisPath + ":6381");
//        return Redisson.create(config);
//    }
}
