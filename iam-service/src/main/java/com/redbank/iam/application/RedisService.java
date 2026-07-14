package com.redbank.iam.application;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {
    private final RedisTemplate<String, Object> redisTemplate;
    public RedisService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    public void blacklistToken(String jti, Duration ttl){
        String key = "blacklist:" + jti;
        String value = "revoked";
        redisTemplate.opsForValue().set(key,value);
        redisTemplate.expire(key,ttl);
    }
    public Boolean isBlacklisted(String jti){
        String key = "blacklist:" + jti;
        return redisTemplate.hasKey(key);
    }
}
