package com.sh.utils;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RedisData {
    // 过期时间
    private LocalDateTime expireTime;
    // 过期数据信息
    private Object data;
}
