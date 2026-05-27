package com.redbank.iam.application;

import com.redbank.iam.config.DebugProperties;
import com.redbank.iam.config.OtpProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;

class OtpServiceTest {
    @Test
    void acceptsMockOtpOnlyWhenEnabledInDevelopProfile() {
        OtpService service = new OtpService(
                new BCryptPasswordEncoder(),
                new OtpProperties(300, 5),
                new DebugProperties(false, new DebugProperties.MockOtp(true)),
                environment("develop")
        );

        assertThat(service.matches("111111", new BCryptPasswordEncoder().encode("222222"))).isTrue();
    }

    @Test
    void rejectsMockOtpWhenProfileIsNotDevelop() {
        OtpService service = new OtpService(
                new BCryptPasswordEncoder(),
                new OtpProperties(300, 5),
                new DebugProperties(false, new DebugProperties.MockOtp(true)),
                environment("test")
        );

        assertThat(service.matches("111111", new BCryptPasswordEncoder().encode("222222"))).isFalse();
    }

    @Test
    void matchesStoredOtpHash() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        OtpService service = new OtpService(
                encoder,
                new OtpProperties(300, 5),
                new DebugProperties(false, new DebugProperties.MockOtp(false)),
                new MockEnvironment()
        );

        assertThat(service.matches("123456", encoder.encode("123456"))).isTrue();
        assertThat(service.matches("654321", encoder.encode("123456"))).isFalse();
    }

    private MockEnvironment environment(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment;
    }
}
