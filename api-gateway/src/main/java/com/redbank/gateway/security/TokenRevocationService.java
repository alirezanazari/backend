package com.redbank.gateway.security;

import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TokenRevocationService {

    private static final String BLACKLIST_PREFIX = "blacklist:";

    private final ReactiveStringRedisTemplate redisTemplate;

    public TokenRevocationService(
            ReactiveStringRedisTemplate redisTemplate
    ) {
        this.redisTemplate = redisTemplate;
    }

    public Mono<Boolean> isRevoked(String tokenId) {

        if (tokenId == null || tokenId.isBlank()) {
            return Mono.just(true);
        }

        return redisTemplate.hasKey(
                BLACKLIST_PREFIX + tokenId
        );
    }
}
