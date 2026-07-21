package com.redbank.iam.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.redbank.contracts.envelope.DeviceContext;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.iam.api.v1.dto.DeviceVerifyRequest;
import com.redbank.iam.api.v1.dto.LoginRequest;
import com.redbank.iam.api.v1.dto.LoginResponse;
import com.redbank.iam.domain.CredentialRecord;
import com.redbank.iam.domain.DeviceRecord;
import com.redbank.iam.domain.DeviceStatus;
import com.redbank.iam.domain.OtpChallengeRecord;
import com.redbank.iam.domain.OtpChallengeStatus;
import com.redbank.iam.domain.OtpPurpose;
import com.redbank.iam.domain.UserRecord;
import com.redbank.iam.domain.UserStatus;
import com.redbank.iam.infrastructure.CredentialRepository;
import com.redbank.iam.infrastructure.DeviceRepository;
import com.redbank.iam.infrastructure.OtpChallengeRepository;
import com.redbank.iam.infrastructure.UserRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

class IamServiceOtpChallengeTest {
    private static final Instant NOW = Instant.parse("2026-06-24T08:00:00Z");

    private final UserRepository userRepository = mock(UserRepository.class);
    private final CredentialRepository credentialRepository = mock(CredentialRepository.class);
    private final DeviceRepository deviceRepository = mock(DeviceRepository.class);
    private final OtpChallengeRepository otpChallengeRepository = mock(OtpChallengeRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final OtpService otpService = mock(OtpService.class);
    private final AccountCompletionClient accountCompletionClient = mock(AccountCompletionClient.class);
    private final RedisService redisService = mock(RedisService.class);
    private final IamService iamService = new IamService(
            userRepository,
            credentialRepository,
            deviceRepository,
            otpChallengeRepository,
            passwordEncoder,
            jwtTokenService,
            otpService,
            mock(RegistrationValidator.class),
            new DeviceContextValidator(),
            accountCompletionClient,
            mock(IamOutboxService.class),
            Clock.fixed(NOW, ZoneOffset.UTC),
            new SimpleMeterRegistry(),
            redisService

    );

    @Test
    void repeatedLoginForPendingDeviceReusesOneActiveOtpChallenge() {
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        AtomicReference<OtpChallengeRecord> activeChallenge = new AtomicReference<>();
        UserRecord user = user(userId);
        DeviceRecord device = device(userId, deviceId);

        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user));
        when(credentialRepository.findByUserId(userId)).thenReturn(Optional.of(credential(userId)));
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);
        when(deviceRepository.findByUserIdAndFingerprint(userId, "fingerprint-1")).thenReturn(Optional.of(device));
        when(otpService.generateOtpHash()).thenReturn("otp-hash");
        when(otpService.ttlSeconds()).thenReturn(300L);
        when(otpService.maxAttempts()).thenReturn(5);
        when(jwtTokenService.createDeviceVerifyToken(userId, deviceId))
                .thenReturn(TokenResult.bearer("device-verify-token", NOW.plusSeconds(300)));
        when(otpChallengeRepository.findPendingByUserDevicePurpose(
                eq(userId),
                eq(deviceId),
                eq(OtpPurpose.DEVICE_VERIFICATION),
                eq(NOW)
        )).thenAnswer(invocation -> Optional.ofNullable(activeChallenge.get()));
        doAnswer(invocation -> {
            activeChallenge.set(invocation.getArgument(0));
            return null;
        }).when(otpChallengeRepository).save(any(OtpChallengeRecord.class));

        LoginResponse firstLogin = iamService.login(context(), new LoginRequest("Alice", "correct-password"));
        LoginResponse secondLogin = iamService.login(context(), new LoginRequest("Alice", "correct-password"));

        assertThat(firstLogin.deviceVerificationRequired()).isTrue();
        assertThat(secondLogin.deviceVerificationRequired()).isTrue();
        assertThat(secondLogin.challengeId()).isEqualTo(firstLogin.challengeId());
        verify(otpChallengeRepository, times(2)).expirePendingByUserDevicePurpose(
                userId,
                deviceId,
                OtpPurpose.DEVICE_VERIFICATION,
                NOW
        );
        verify(otpChallengeRepository, times(1)).save(any(OtpChallengeRecord.class));
    }
    @Test
    public void logoutAddsTokenToBlacklist(){
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = NOW.plusSeconds(300);
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();

        when(jwtTokenService.verifyBearerToken("Bearer device-token", JwtTokenService.SCOPE_USER))
                .thenReturn(new VerifiedToken(
                                jti,
                                expiresAt,
                                userId,
                                deviceId,
                                List.of("USER"),
                                List.of(JwtTokenService.SCOPE_USER)
                        ));

        iamService.logout("Bearer device-token");

        verify(redisService).blacklistToken(eq(jti), eq(Duration.ofSeconds(300)));
        verify(jwtTokenService).verifyBearerToken("Bearer device-token", JwtTokenService.SCOPE_USER);

    }

    @Test
    void wrongOtpConsumesAttemptBeforeUnauthorizedResponse() {
        String jti = UUID.randomUUID().toString();
        Instant expiresAt = NOW.plusSeconds(300);
        UUID userId = UUID.randomUUID();
        UUID deviceId = UUID.randomUUID();
        UUID challengeId = UUID.randomUUID();
        OtpChallengeRecord challenge = challenge(userId, deviceId, challengeId, 4, 5);

        when(jwtTokenService.verifyBearerToken("Bearer device-token", JwtTokenService.SCOPE_DEVICE_VERIFY))
                .thenReturn(new VerifiedToken(jti, expiresAt, userId, deviceId, java.util.List.of("ONBOARDING"), java.util.List.of(JwtTokenService.SCOPE_DEVICE_VERIFY)));
        when(otpChallengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(otpService.matches("000000", "otp-hash")).thenReturn(false);

        assertThatThrownBy(() -> iamService.verifyDevice(
                "Bearer device-token",
                new DeviceVerifyRequest(challengeId.toString(), "000000")
        )).isInstanceOf(IamUnauthorizedException.class);

        verify(otpChallengeRepository).recordFailedAttempt(challengeId, NOW);
    }

    @Test
    void deviceVerificationUnauthorizedResponsesDoNotRollbackAttemptUpdates() throws NoSuchMethodException {
        Transactional transactional = IamService.class
                .getMethod("verifyDevice", String.class, DeviceVerifyRequest.class)
                .getAnnotation(Transactional.class);

        assertThat(Arrays.asList(transactional.noRollbackFor())).contains(IamUnauthorizedException.class);
    }

    private RequestContext context() {
        return new RequestContext(
                "request-id",
                null,
                null,
                null,
                new DeviceContext("client-device", "Pixel", "fingerprint-1"),
                null
        );
    }

    private UserRecord user(UUID userId) {
        return new UserRecord(
                userId,
                "alice",
                "09120000000",
                "1234567890",
                "Ali",
                "Nazari",
                "IR",
                "Tehran",
                "Tehran",
                "Main",
                "1234567890",
                "job-engineer",
                UserStatus.REGISTERED,
                NOW,
                NOW
        );
    }

    private CredentialRecord credential(UUID userId) {
        return new CredentialRecord(
                UUID.randomUUID(),
                userId,
                "stored-hash",
                "BCrypt",
                NOW,
                NOW,
                NOW
        );
    }

    private DeviceRecord device(UUID userId, UUID deviceId) {
        return new DeviceRecord(
                deviceId,
                userId,
                "fingerprint-1",
                "Pixel",
                "Android",
                "1.0.0",
                DeviceStatus.PENDING_VERIFICATION,
                null,
                NOW,
                NOW
        );
    }

    private OtpChallengeRecord challenge(UUID userId, UUID deviceId, UUID challengeId, int attemptCount, int maxAttempts) {
        return new OtpChallengeRecord(
                challengeId,
                userId,
                deviceId,
                "otp-hash",
                OtpPurpose.DEVICE_VERIFICATION,
                NOW.plusSeconds(300),
                attemptCount,
                maxAttempts,
                OtpChallengeStatus.PENDING,
                NOW,
                NOW
        );
    }
}
