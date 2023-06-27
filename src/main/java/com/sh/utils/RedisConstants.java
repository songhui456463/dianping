package com.sh.utils;

public interface RedisConstants {

    String LOGIN_CODE_KEY = "login:code:";
    Long LOGIN_CODE_TTL = 1L;
    String LOGIN_USER_KEY = "login:token:";
    Long LOGIN_USER_TTL = 36000L;

    Long CACHE_NULL_TTL = 2L;

    Long CACHE_SHOP_TTL = 30L;
    String CACHE_SHOP_KEY = "cache:shop:";

    String LOCK_SHOP_KEY = "lock:shop:";
    Long LOCK_SHOP_TTL = 10L;

    String FOLLOW_KEY = "follow:";

    String SECKILL_STOCK_KEY = "seckill:stock:";
    String BLOG_LIKED_KEY = "blog:liked:";
    String FEED_KEY = "feed:";
    String SHOP_GEO_KEY = "shop:geo:";
    String USER_SIGN_KEY = "sign:";
}
