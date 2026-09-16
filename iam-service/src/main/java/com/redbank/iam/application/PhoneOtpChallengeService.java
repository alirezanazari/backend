package com.redbank.iam.application;

import com.redbank.contracts.envelope.StatusDetail;
import com.redbank.iam.api.internal.dto.CreateOtpChallengeRequest;
import com.redbank.iam.api.internal.dto.CreateOtpChallengeResponse;
import com.redbank.iam.config.OtpProperties;
import com.redbank.iam.domain.OtpChallengeRecord;
import com.redbank.iam.domain.OtpChallengeStatus;
import com.redbank.iam.domain.OtpPurpose;
import com.redbank.iam.infrastructure.OtpChallengeRepository;
import com.redbank.iam.infrastructure.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PhoneOtpChallengeService {
    private static final Logger log = LoggerFactory.getLogger(PhoneOtpChallengeService.class);
    private static final Pattern PHONE_NUMBER = Pattern.compile("^\\+?[0-9]{10,15}$");

    private final UserRepository userRepository;
    private final OtpChallengeRepository otpChallengeRepository;
    private final OtpService otpService;
    private final OtpProperties otpProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    public PhoneOtpChallengeService(
            UserRepository userRepository,
            OtpChallengeRepository otpChallengeRepository,
            OtpService otpService,
            OtpProperties otpProperties,
            Clock clock,
            MeterRegistry meterRegistry
    ) {
        this.userRepository = userRepository;
        this.otpChallengeRepository = otpChallengeRepository;
        this.otpService = otpService;
        this.otpProperties = otpProperties;
        this.clock = clock;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public CreateOtpChallengeResponse createPhoneChallenge(CreateOtpChallengeRequest request) {
        UUID onboardingSessionId = parseOnboardingSessionId(request.onboardingSessionId());
        String phoneNumber = validatePhoneNumber(request.phoneNumber());

        if (userRepository.existsByPhoneNumber(phoneNumber)) {
            recordChallenge("phone_already_registered");
            throw new IamConflictException("Phone number already exists");
        }

        Instant now = clock.instant();
        enforcePhoneLimits(phoneNumber, now);

        otpChallengeRepository.supersedePendingBySessionPurpose(onboardingSessionId, OtpPurpose.PHONE_VERIFICATION, now);
        OtpChallengeRecord challenge = new OtpChallengeRecord(
                UUID.randomUUID(),
                null,
                null,
                onboardingSessionId,
                phoneNumber,
                null,
                otpService.generateOtpHash(),
                OtpPurpose.PHONE_VERIFICATION,
                now.plusSeconds(otpService.ttlSeconds()),
                0,
                otpService.maxAttempts(),
                OtpChallengeStatus.PENDING,
                now,
                now
        );
        otpChallengeRepository.save(challenge);
        recordChallenge("created");
        log.info("Phone verification OTP challenge created challengeId={} onboardingSessionId={}",
                challenge.id(), onboardingSessionId);

        return new CreateOtpChallengeResponse(
                challenge.id().toString(),
                challenge.expiresAt(),
                now.plusSeconds(otpProperties.resendCooldownSeconds())
        );
    }

    private void enforcePhoneLimits(String phoneNumber, Instant now) {
        Optional<Instant> latestForPhone = otpChallengeRepository
                .findLatestCreatedAtByPhonePurpose(phoneNumber, OtpPurpose.PHONE_VERIFICATION);
        if (latestForPhone.isPresent()) {
            Instant resendAvailableAt = latestForPhone.get().plusSeconds(otpProperties.resendCooldownSeconds());
            if (now.isBefore(resendAvailableAt)) {
                recordChallenge("resend_cooldown");
                throw new IamLimitExceededException("OTP resend is not available yet");
            }
        }
        int recentChallenges = otpChallengeRepository.countByPhonePurposeSince(
                phoneNumber,
                OtpPurpose.PHONE_VERIFICATION,
                now.minus(Duration.ofHours(1))
        );
        if (recentChallenges >= otpProperties.maxPerPhonePerHour()) {
            recordChallenge("phone_hourly_limit");
            throw new IamLimitExceededException("Too many OTP requests for this phone number");
        }
    }

    private UUID parseOnboardingSessionId(String onboardingSessionId) {
        try {
            return UUID.fromString(onboardingSessionId);
        } catch (RuntimeException exception) {
            throw new IamValidationException(List.of(new StatusDetail(
                    "onboardingSessionId",
                    "onboardingSessionId must be a UUID",
                    "ONBOARDING_SESSION_ID_UUID"
            )));
        }
    }

    private String validatePhoneNumber(String phoneNumber) {
        System.out.println("SERVICE PHONE=[" + phoneNumber + "]");
        System.out.println("SERVICE LENGTH=" + (phoneNumber == null ? "null" : phoneNumber.length()));
        System.out.println("PHONE CODES=" + phoneNumber.chars().boxed().toList());
        System.out.println("REGEX MATCH=" + (phoneNumber != null && PHONE_NUMBER.matcher(phoneNumber).matches()));
        if (phoneNumber == null || !PHONE_NUMBER.matcher(phoneNumber).matches()) {
            throw new IamValidationException(List.of(new StatusDetail(
                    "phoneNumber",
                    "phone number must be 10-15 digits and may start with +",
                    "PHONE_PATTERN"
            )));
        }
        return phoneNumber;
    }

    private void recordChallenge(String outcome) {
        meterRegistry.counter(
                "redbank.auth.requests",
                "operation",
                "phone_otp_challenge",
                "outcome",
                outcome
        ).increment();
    }
}
