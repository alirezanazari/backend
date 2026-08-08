package com.redbank.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@SuppressWarnings("unchecked")
class RedisServiceTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final RedisService redisService = new RedisService(redisTemplate);

    @Test
    void blacklistTokenStoresJtiUntilTokenExpiration() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisService.blacklistToken("token-id", Duration.ofMinutes(5));

        verify(valueOperations).set("blacklist:token-id", "revoked", Duration.ofMinutes(5));
    }

    @Test
    void blacklistTokenSkipsNonPositiveTtl() {
        redisService.blacklistToken("token-id", Duration.ZERO);

        verifyNoInteractions(redisTemplate);
        verifyNoInteractions(valueOperations);
    }

    @Test
    void blankJtiIsTreatedAsBlacklistedWithoutRedisLookup() {
        assertThat(redisService.isBlacklisted(" ")).isTrue();

        verifyNoInteractions(redisTemplate);
    }
}
