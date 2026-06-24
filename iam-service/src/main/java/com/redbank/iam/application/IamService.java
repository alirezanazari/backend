package com.redbank.iam.application;

import com.redbank.contracts.envelope.RequestContext;
import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.contracts.identity.AccountCompletionStatus;
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
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import io.micrometer.core.instrument.MeterRegistry;
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
    private final DeviceContextValidator deviceContextValidator;
    private final AccountCompletionClient accountCompletionClient;
    private final IamOutboxService iamOutboxService;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public IamService(
            UserRepository userRepository,
            CredentialRepository credentialRepository,
            DeviceRepository deviceRepository,
            OtpChallengeRepository otpChallengeRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            OtpService otpService,
            RegistrationValidator registrationValidator,
            DeviceContextValidator deviceContextValidator,
            AccountCompletionClient accountCompletionClient,
            IamOutboxService iamOutboxService,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.userRepository = userRepository;
        this.credentialRepository = credentialRepository;
        this.deviceRepository = deviceRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.otpService = otpService;
        this.registrationValidator = registrationValidator;
        this.deviceContextValidator = deviceContextValidator;
        this.accountCompletionClient = accountCompletionClient;
        this.iamOutboxService = iamOutboxService;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        registrationValidator.validate(request);

        String username = normalizeUsername(request.username());
        ensureUniqueRegistration(username, request.phoneNumber(), request.nationalId());

        Instant now = clock.instant();
        UserRecord user = new UserRecord(
                UUID.randomUUID(),
                username,
                request.phoneNumber(),
                request.nationalId(),
                request.firstName(),
                request.lastName(),
                request.location().countryCode(),
                request.location().province(),
                request.location().city(),
                request.location().address(),
                request.location().postalCode(),
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

        TokenResult token = jwtTokenService.createAccountRequiredToken(user.id());
        return new RegisterResponse(
                user.id().toString(),
                token.accessToken(),
                token.tokenType(),
                token.expiresAt(),
                AccountCompletionStatus.ACCOUNT_REQUIRED
        );
    }

    @Transactional
    public LoginResponse login(RequestContext context, LoginRequest request) {
        String fingerprint = deviceContextValidator.requireFingerprint(context);
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
            AccountCompletionResult completion = accountCompletionClient.getCompletion(user.id());
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

    @Transactional(noRollbackFor = IamUnauthorizedException.class)
    public DeviceVerifyResponse verifyDevice(String authorizationHeader, DeviceVerifyRequest request) {
        VerifiedToken token = jwtTokenService.verifyBearerToken(authorizationHeader, JwtTokenService.SCOPE_DEVICE_VERIFY);
        UUID challengeId = parseChallengeId(request.challengeId());
        OtpChallengeRecord challenge = otpChallengeRepository.findById(challengeId)
                .orElseThrow(() -> new IamBusinessRuleException("OTP challenge was not found"));

        if (!challenge.userId().equals(token.userId())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        if (token.deviceId() == null || !challenge.deviceId().equals(token.deviceId())) {
            throw new IamUnauthorizedException("Unauthorized");
        }
        validateChallengeCanBeUsed(challenge);

        Instant now = clock.instant();
        if (!otpService.matches(request.otpCode(), challenge.otpHash())) {
            otpChallengeRepository.recordFailedAttempt(challenge.id(), now);
            recordAuth("verify_device", "rejected");
            throw new IamUnauthorizedException("Unauthorized");
        }

        otpChallengeRepository.markStatus(challenge.id(), OtpChallengeStatus.VERIFIED, now);
        deviceRepository.markVerified(challenge.deviceId(), now);
        AccountCompletionResult completion = accountCompletionClient.getCompletion(challenge.userId());
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

    private void validateChallengeCanBeUsed(OtpChallengeRecord challenge) {
        Instant now = clock.instant();
        if (challenge.status() != OtpChallengeStatus.PENDING) {
            recordAuth("verify_device", "invalid_status");
            throw new IamBusinessRuleException("OTP challenge is not pending");
        }
        if (!challenge.expiresAt().isAfter(now)) {
            otpChallengeRepository.markStatus(challenge.id(), OtpChallengeStatus.EXPIRED, now);
            recordAuth("verify_device", "expired");
            throw new IamUnauthorizedException("Unauthorized");
        }
        if (challenge.attemptCount() >= challenge.maxAttempts()) {
            otpChallengeRepository.markStatus(challenge.id(), OtpChallengeStatus.FAILED, now);
            recordAuth("verify_device", "max_attempts");
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
