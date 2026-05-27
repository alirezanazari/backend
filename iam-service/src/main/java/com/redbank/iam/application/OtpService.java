package com.redbank.iam.application;

import com.redbank.iam.config.DebugProperties;
import com.redbank.iam.config.OtpProperties;
import java.security.SecureRandom;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class OtpService {
    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final String MOCK_OTP = "111111";

    private final PasswordEncoder passwordEncoder;
    private final OtpProperties otpProperties;
    private final DebugProperties debugProperties;
    private final Environment environment;
    private final SecureRandom secureRandom = new SecureRandom();

    public OtpService(
            PasswordEncoder passwordEncoder,
            OtpProperties otpProperties,
            DebugProperties debugProperties,
            Environment environment
    ) {
        this.passwordEncoder = passwordEncoder;
        this.otpProperties = otpProperties;
        this.debugProperties = debugProperties;
        this.environment = environment;
    }

    public String generateOtpHash() {
        String otpCode = "%06d".formatted(secureRandom.nextInt(1_000_000));
        return passwordEncoder.encode(otpCode);
    }

    public boolean matches(String submittedOtp, String storedHash) {
        if (mockOtpAllowed() && MOCK_OTP.equals(submittedOtp)) {
            log.info("Develop mock OTP path used");
            return true;
        }
        return passwordEncoder.matches(submittedOtp, storedHash);
    }

    public long ttlSeconds() {
        return otpProperties.ttlSeconds();
    }

    public int maxAttempts() {
        return otpProperties.maxAttempts();
    }

    private boolean mockOtpAllowed() {
        return debugProperties.mockOtp().enabled()
                && Arrays.asList(environment.getActiveProfiles()).contains("develop");
    }
}
