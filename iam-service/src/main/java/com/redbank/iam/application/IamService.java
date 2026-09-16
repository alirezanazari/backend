package com.redbank.iam.application;

import com.nimbusds.jwt.JWTClaimsSet;
import com.redbank.contracts.common.Location;
import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.contracts.identity.LoginReason;
import com.redbank.iam.api.v1.dto.DeviceVerifyRequest;
import com.redbank.iam.api.v1.dto.DeviceVerifyResponse;
import com.redbank.iam.api.v1.dto.LoginRequest;
import com.redbank.iam.api.v1.dto.LoginResponse;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import com.redbank.iam.api.v1.dto.RegisterResponse;
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
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IamService {
    private static final String PASSWORD_ALGORITHM = "BCrypt";

    private final UserRepository userRepository;
    private final CredentialRepository credentialRepository;
    private final DeviceRepository deviceRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final OtpService otpService;
    private final RegistrationValidator registrationValidator;
    private final LoginValidator loginValidator;
    private final DeviceContextValidator deviceContextValidator;
    private final AccountCompletionClient accountCompletionClient;
    private final KycCompletionClient kycCompletionClient;
    private final IamOutboxService iamOutboxService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final RedisService redisService;

    public IamService(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            DeviceRepository deviceRepository,
            OtpChallengeRepository otpChallengeRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            OtpService otpService,
            RegistrationValidator registrationValidator,
            LoginValidator loginValidator,
            DeviceContextValidator deviceContextValidator,
            AccountCompletionClient accountCompletionClient,
            KycCompletionClient kycCompletionClient,
            IamOutboxService iamOutboxService,
            Clock clock,
            MeterRegistry meterRegistry,
            RedisService redisService
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.deviceRepository = deviceRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.otpService = otpService;
        this.registrationValidator = registrationValidator;
        this.loginValidator = loginValidator;
        this.deviceContextValidator = deviceContextValidator;
        this.accountCompletionClient = accountCompletionClient;
        this.kycCompletionClient = kycCompletionClient;
        this.iamOutboxService = iamOutboxService;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
        this.redisService = redisService;
    }

    @Transactional
    public RegisterResponse register(RequestContext context, String authorizationHeader, RegisterRequest request) {
        registrationValidator.validate(request);

        OtpChallengeRecord onboardingSession = resolveVerifiedOnboardingChallenge(authorizationHeader);
        if (onboardingSession != null && !onboardingSession.phoneNumber().equals(request.phoneNumber())) {
            recordAuth("register", "phone_mismatch");
            throw new IamBusinessRuleException("Phone number does not match the verified onboarding phone");
        }

        String username = normalizeUsername(request.username());
        ensureUniqueRegistration(username, request.phoneNumber(), request.nationalId());

        final Location location = request.location();

        String countryCode = location == null ? "" : location.countryCode();
        String province = location == null ? "" : location.province();
        String city = location == null ? "" : location.city();
        String address = location == null ? "" : location.address();
        String postalCode = location == null ? "" : location.postalCode();

        Instant now = clock.instant();
        UserRecord user = new UserRecord(
                UUID.randomUUID(),
                username,
                request.phoneNumber(),
                request.nationalId(),
                request.firstName(),
                request.lastName(),
                countryCode,
                province,
                city,
                address,
                postalCode,
                request.jobId(),
                UserStatus.REGISTERED,
                now,
                now
        );
        CredentialRecord credential = new CredentialRecord(
                UUID.randomUUID(),
                user.id(),
                passwordEncoder.encode(request.password()),
                PASSWORD_ALGORITHM,
                now,
                now,
                now
        );

        try {
            userRepository.save(user);
            credentialRepository.save(credential);
        } catch (DataIntegrityViolationException exception) {
            throw new IamConflictException("Username, phone number, or national ID already exists");
        }
        iamOutboxService.enqueueUserRegistered(user);
        recordAuth("register", "success");

        UUID adoptedDeviceId = null;
        if (onboardingSession != null && onboardingSession.deviceFingerprint() != null) {
            DeviceRecord device = adoptVerifiedDevice(context, user.id(), onboardingSession, now);
            adoptedDeviceId = device.id();
        }

        TokenResult token = jwtTokenService.createAccountRequiredToken(user.id(), adoptedDeviceId);
        return new RegisterResponse(
                user.id().toString(),
                token.accessToken(),
                token.tokenType(),
                token.expiresAt(),
                AccountCompletionStatus.ACCOUNT_REQUIRED
        );
    }

    private OtpChallengeRecord resolveVerifiedOnboardingChallenge(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }
        VerifiedToken token = jwtTokenService.verifyBearerToken(authorizationHeader, JwtTokenService.SCOPE_IAM_REGISTER);
        return otpChallengeRepository.findLatestVerifiedPhoneChallengeBySession(token.userId()).orElse(null);
    }

    private DeviceRecord adoptVerifiedDevice(
            RequestContext context,
            UUID userId,
            OtpChallengeRecord onboardingSession,
            Instant now
    ) {
        DeviceRecord device = new DeviceRecord(
                UUID.randomUUID(),
                userId,
                onboardingSession.deviceFingerprint(),
                context == null || context.device() == null ? null : context.device().model(),
                context == null || context.os() == null ? null : context.os().name(),
                context == null || context.app() == null ? null : context.app().version(),
                DeviceStatus.VERIFIED,
                now,
                now,
                now
        );
        deviceRepository.save(device);
        iamOutboxService.enqueueDeviceVerified(device);
        recordAuth("register", "onboarding_device_adopted");
        return device;
    }

    @Transactional
    public LoginResponse login(RequestContext context, LoginRequest request) {
        loginValidator.validate(request);
        if (request.reason() == LoginReason.RECOVERY) {
            recordAuth("login", "recovery_unsupported");
            throw new IamBusinessRuleException("Login reason RECOVERY is not supported yet");
        }
        String fingerprint = deviceContextValidator.requireFingerprint(context);
        if (request.reason() == LoginReason.REGISTER) {
            return anonymousOnboardingLogin();
        }
        String username = normalizeUsername(request.username());
        UserRecord user = userRepository.findByUsername(username)
                .orElseThrow(() -> new IamUnauthorizedException("Unauthorized"));
        CredentialRecord credential = credentialRepository.findByUserId(user.id())
                .orElseThrow(() -> new IamUnauthorizedException("Unauthorized"));

        if (!passwordEncoder.matches(request.password(), credential.passwordHash())) {
            recordAuth("login", "rejected");
            throw new IamUnauthorizedException("Unauthorized");
        }

        DeviceRecord device = findOrCreateDevice(context, user.id(), fingerprint);
        if (device.status() == DeviceStatus.BLOCKED || device.status() == DeviceStatus.REMOVED) {
            recordAuth("login", "blocked");
            throw new IamBusinessRuleException("Device is not allowed");
        }
        if (device.status() == DeviceStatus.VERIFIED) {
            AccountCompletionResult completion = resolveCompletion(user.id());
            TokenResult token = jwtTokenService.createUserToken(user.id(), device.id(), completion.activeProfileId());
            recordAuth("login", "authenticated");
            return new LoginResponse(
                    token.accessToken(),
                    token.tokenType(),
                    token.expiresAt(),
                    completion.accountCompletionStatus(),
                    completion.activeProfile(),
                    false,
                    null
            );
        }

        OtpChallengeRecord challenge = findOrCreateOtpChallenge(user.id(), device.id());
        TokenResult token = jwtTokenService.createDeviceVerifyToken(user.id(), device.id());
        recordAuth("login", "challenge_required");
        return new LoginResponse(
                token.accessToken(),
                token.tokenType(),
                token.expiresAt(),
                AccountCompletionStatus.DEVICE_VERIFICATION_REQUIRED,
                null,
                true,
                challenge.id().toString()
        );
    }

    @Transactional
    public void logout(String authorizationHeader) {
        VerifiedToken token = jwtTokenService.verifyBearerToken(authorizationHeader, JwtTokenService.SCOPE_USER);
        String jti = token.jti();
        Instant expiresAt = token.expiresAt();
        Instant now = clock.instant();
        Duration ttl = Duration.between(now, expiresAt);
        redisService.blacklistToken(jti, ttl);
    }

    @Transactional(noRollbackFor = IamUnauthorizedException.class)
    public DeviceVerifyResponse verifyDevice(RequestContext context, String authorizationHeader, DeviceVerifyRequest request) {
        VerifiedToken token = jwtTokenService.verifyBearerToken(authorizationHeader, JwtTokenService.SCOPE_DEVICE_VERIFY);
        UUID challengeId = parseChallengeId(request.challengeId());
        OtpChallengeRecord challenge = otpChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new IamBusinessRuleException("OTP challenge was not found"));

        if (challenge.purpose() == OtpPurpose.PHONE_VERIFICATION) {
            return verifyOnboardingPhoneChallenge(context, token, challenge, request.otpCode());
        }

        if (!challenge.userId().equals(token.userId())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        if (token.deviceId() == null || !challenge.deviceId().equals(token.deviceId())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        validateChallengeCanBeUsed(challenge, "verify_device");

        Instant now = clock.instant();
        if (!otpService.matches(request.otpCode(), challenge.otpHash())) {
            otpChallengeRepository.recordFailedAttempt(challenge.id(), now);
            recordAuth("verify_device", "rejected");
            throw new IamUnauthorizedException("Unauthorized");
        }

        otpChallengeRepository.markStatus(challenge.id(), OtpChallengeStatus.VERIFIED, now);
        deviceRepository.markVerified(challenge.deviceId(), now);
        AccountCompletionResult completion = resolveCompletion(challenge.userId());
        TokenResult userToken = jwtTokenService.createUserToken(challenge.userId(), challenge.deviceId(), completion.activeProfileId());
        DeviceRecord device = deviceRepository.findById(challenge.deviceId())
                .orElseThrow(() -> new IamBusinessRuleException("Device was not found"));
        iamOutboxService.enqueueDeviceVerified(device);
        recordAuth("verify_device", "success");

        return new DeviceVerifyResponse(
                device.id().toString(),
                DeviceStatus.VERIFIED.name(),
                userToken.accessToken(),
                userToken.tokenType(),
                userToken.expiresAt(),
                completion.accountCompletionStatus()
        );
    }

    private DeviceVerifyResponse verifyOnboardingPhoneChallenge(
            RequestContext context,
            VerifiedToken token,
            OtpChallengeRecord challenge,
            String otpCode
    ) {
        if (challenge.onboardingSessionId() == null || !challenge.onboardingSessionId().equals(token.userId())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        String fingerprint = deviceContextValidator.requireFingerprint(context);
        validateChallengeCanBeUsed(challenge, "verify_phone");

        Instant now = clock.instant();
        if (!otpService.matches(otpCode, challenge.otpHash())) {
            otpChallengeRepository.recordFailedAttempt(challenge.id(), now);
            recordAuth("verify_phone", "rejected");
            throw new IamUnauthorizedException("Unauthorized");
        }

        otpChallengeRepository.markVerifiedWithFingerprint(challenge.id(), fingerprint, now);
        recordAuth("verify_phone", "success");

        // No user or device exists yet: the verified phone + fingerprint stay bound to the
        // onboarding session, and iam/register adopts them. The client keeps its anonymous token.
        return new DeviceVerifyResponse(
                null,
                DeviceStatus.VERIFIED.name(),
                null,
                null,
                null,
                AccountCompletionStatus.ONBOARDING
        );
    }

    private LoginResponse anonymousOnboardingLogin() {
        TokenResult token = jwtTokenService.createAnonymousOnboardingToken(UUID.randomUUID());
        recordAuth("login", "anonymous_onboarding");
        return new LoginResponse(
                token.accessToken(),
                token.tokenType(),
                token.expiresAt(),
                AccountCompletionStatus.ONBOARDING,
                null,
                false,
                null
        );
    }

    private AccountCompletionResult resolveCompletion(UUID userId) {
        AccountCompletionResult accountResult = accountCompletionClient.getCompletion(userId);
        if (accountResult.accountCompletionStatus() != AccountCompletionStatus.KYC_REQUIRED) {
            return accountResult;
        }
        AccountCompletionStatus kycStatus = kycCompletionClient.getCompletionStatus(userId);
        return new AccountCompletionResult(kycStatus, accountResult.activeProfile());
    }

    private void ensureUniqueRegistration(String username, String phoneNumber, String nationalId) {
        if (userRepository.existsByUsername(username)) {
            throw new IamConflictException("Username already exists");
        }
        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            throw new IamConflictException("Phone number already exists");
        }
        if (userRepository.existsByNationalId(nationalId)) {
            throw new IamConflictException("National ID already exists");
        }
    }

    private DeviceRecord findOrCreateDevice(RequestContext context, UUID userId, String fingerprint) {
        return deviceRepository.findByUserIdAndFingerprint(userId, fingerprint)
                .orElseGet(() -> {
                    Instant now = clock.instant();
                    DeviceRecord device = new DeviceRecord(
                            UUID.randomUUID(),
                            userId,
                            fingerprint,
                            context.device() == null ? null : context.device().model(),
                            context.os() == null ? null : context.os().name(),
                            context.app() == null ? null : context.app().version(),
                            DeviceStatus.PENDING_VERIFICATION,
                            null,
                            now,
                            now
                    );
                    deviceRepository.save(device);
                    return device;
                });
    }

    private OtpChallengeRecord findOrCreateOtpChallenge(UUID userId, UUID deviceId) {
        Instant now = clock.instant();
        otpChallengeRepository.expirePendingByUserDevicePurpose(userId, deviceId, OtpPurpose.DEVICE_VERIFICATION, now);
        return otpChallengeRepository.findPendingByUserDevicePurpose(userId, deviceId, OtpPurpose.DEVICE_VERIFICATION, now)
                .orElseGet(() -> createOtpChallenge(userId, deviceId, now));
    }

    private OtpChallengeRecord createOtpChallenge(UUID userId, UUID deviceId, Instant now) {
        OtpChallengeRecord challenge = new OtpChallengeRecord(
                UUID.randomUUID(),
                userId,
                deviceId,
                null,
                null,
                null,
                otpService.generateOtpHash(),
                OtpPurpose.DEVICE_VERIFICATION,
                now.plusSeconds(otpService.ttlSeconds()),
                0,
                otpService.maxAttempts(),
                OtpChallengeStatus.PENDING,
                now,
                now
        );
        otpChallengeRepository.save(challenge);
        return challenge;
    }

    private void validateChallengeCanBeUsed(OtpChallengeRecord challenge, String operation) {
        Instant now = clock.instant();
        if (challenge.status() != OtpChallengeStatus.PENDING) {
            recordAuth(operation, "invalid_status");
            throw new IamBusinessRuleException("OTP challenge is not pending");
        }
        if (!challenge.expiresAt().isAfter(now)) {
            otpChallengeRepository.markStatus(challenge.id(), OtpChallengeStatus.EXPIRED, now);
            recordAuth(operation, "expired");
            throw new IamUnauthorizedException("Unauthorized");
        }
        if (challenge.attemptCount() >= challenge.maxAttempts()) {
            otpChallengeRepository.markStatus(challenge.id(), OtpChallengeStatus.FAILED, now);
            recordAuth(operation, "max_attempts");
            throw new IamUnauthorizedException("Unauthorized");
        }
    }

    private void recordAuth(String operation, String outcome) {
        meterRegistry.counter(
                "redbank.auth.requests",
                "operation",
                operation,
                "outcome",
                outcome
        ).increment();
    }

    private UUID parseChallengeId(String challengeId) {
        try {
            return UUID.fromString(challengeId);
        } catch (RuntimeException exception) {
            throw new IamValidationException(List.of(new StatusDetail(
                    "data.challengeId",
                    "challengeId must be a UUID",
                    "CHALLENGE_ID_UUID"
            )));
        }
    }

    private String normalizeUsername(String username) {
        return username.toLowerCase(Locale.ROOT);
    }
}
