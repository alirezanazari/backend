package com.redbank.iam.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import com.redbank.iam.application.AccountCompletionClient;
import com.redbank.iam.application.AccountCompletionResult;
import com.redbank.iam.application.RedisService;
import com.redbank.iam.domain.OutboxEventRecord;
import com.redbank.iam.domain.OutboxEventStatus;
import com.redbank.iam.infrastructure.OutboxEventRepository;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.redbank.iam.jooq.tables.Credentials.CREDENTIALS;
import static com.redbank.iam.jooq.tables.Devices.DEVICES;
import static com.redbank.iam.jooq.tables.OtpChallenges.OTP_CHALLENGES;
import static com.redbank.iam.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

@SpringBootTest(properties = {
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.debug.mock-otp.enabled=true",
        "redbank.otp.ttl-seconds=300",
        "redbank.otp.max-attempts=5",
        "redbank.events.publisher.enabled=false"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
@Testcontainers
class IamControllerIntegrationTest {
    private static final AtomicInteger SEQUENCE = new AtomicInteger(1000);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    @Autowired
    private MutableAccountCompletionClient accountCompletionClient;

    @Autowired
    private MutableKycCompletionClient kycCompletionClient;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @MockitoBean
    private RedisService redisService;

    @BeforeEach
    void resetAccountCompletion() {
        when(redisService.isBlacklisted(anyString()))
                .thenReturn(false);
        accountCompletionClient.result(new AccountCompletionResult(AccountCompletionStatus.ACCOUNT_REQUIRED, null));
        kycCompletionClient.status(AccountCompletionStatus.KYC_REQUIRED);
    }

    @Test
    void registerCreatesUserCredentialAndLimitedToken() throws Exception {
        RegistrationData data = registrationData();

        String response = register(data)
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.status.code").value("CREATED"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ACCOUNT_REQUIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String userId = objectMapper.readTree(response).path("data").path("userId").asText();
        String storedPassword = dsl.select(CREDENTIALS.PASSWORD_HASH)
                .from(CREDENTIALS)
                .where(CREDENTIALS.USER_ID.eq(UUID.fromString(userId)))
                .fetchOne(CREDENTIALS.PASSWORD_HASH);

        assertThat(storedPassword).isNotBlank();
        assertThat(storedPassword).isNotEqualTo(data.password());
        assertThat(storedPassword).startsWith("$2");
        assertThat(dsl.fetchCount(
                OUTBOX_EVENTS,
                OUTBOX_EVENTS.AGGREGATE_ID.eq(userId)
                        .and(OUTBOX_EVENTS.EVENT_TYPE.eq("USER_REGISTERED"))
                        .and(OUTBOX_EVENTS.STATUS.eq("PENDING"))
        )).isEqualTo(1);
    }

    @Test
    void loginCreatesDeviceVerificationChallengeForNewDevice() throws Exception {
        RegistrationData data = registrationData();
        register(data).andExpect(status().isCreated());

        String response = login(data, "fingerprint-new-device")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("DEVICE_VERIFICATION_REQUIRED"))
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(true))
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String challengeId = objectMapper.readTree(response).path("data").path("challengeId").asText();
        String otpHash = dsl.select(OTP_CHALLENGES.OTP_HASH)
                .from(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(UUID.fromString(challengeId)))
                .fetchOne(OTP_CHALLENGES.OTP_HASH);

        assertThat(otpHash).isNotBlank();
        assertThat(otpHash).isNotEqualTo("111111");
    }

    @Test
    void repeatedLoginForPendingDeviceReusesActiveChallenge() throws Exception {
        RegistrationData data = registrationData();
        String fingerprint = "fingerprint-repeat-login-" + data.sequence();
        register(data).andExpect(status().isCreated());

        JsonNode firstLogin = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString());
        JsonNode secondLogin = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(true))
                .andReturn()
                .getResponse()
                .getContentAsString());

        UUID challengeId = UUID.fromString(firstLogin.path("data").path("challengeId").asText());
        assertThat(secondLogin.path("data").path("challengeId").asText()).isEqualTo(challengeId.toString());

        var challengeOwner = dsl.select(OTP_CHALLENGES.USER_ID, OTP_CHALLENGES.DEVICE_ID)
                .from(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(challengeId))
                .fetchOne();
        assertThat(dsl.fetchCount(
                OTP_CHALLENGES,
                OTP_CHALLENGES.USER_ID.eq(challengeOwner.get(OTP_CHALLENGES.USER_ID))
                        .and(OTP_CHALLENGES.DEVICE_ID.eq(challengeOwner.get(OTP_CHALLENGES.DEVICE_ID)))
                        .and(OTP_CHALLENGES.PURPOSE.eq("DEVICE_VERIFICATION"))
                        .and(OTP_CHALLENGES.STATUS.eq("PENDING"))
        )).isEqualTo(1);
    }

    @Test
    void deviceVerifyMarksDeviceVerifiedAndNextLoginReturnsUserToken() throws Exception {
        RegistrationData data = registrationData();
        String fingerprint = "fingerprint-verify-device-" + data.sequence();
        register(data).andExpect(status().isCreated());

        JsonNode login = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        String limitedToken = login.path("data").path("accessToken").asText();
        String challengeId = login.path("data").path("challengeId").asText();

        String verificationResponse = verifyDevice(limitedToken, challengeId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.deviceStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ACCOUNT_REQUIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String deviceId = objectMapper.readTree(verificationResponse).path("data").path("deviceId").asText();
        assertThat(dsl.fetchCount(
                OUTBOX_EVENTS,
                OUTBOX_EVENTS.AGGREGATE_ID.eq(deviceId)
                        .and(OUTBOX_EVENTS.EVENT_TYPE.eq("DEVICE_VERIFIED"))
                        .and(OUTBOX_EVENTS.STATUS.eq("PENDING"))
        )).isEqualTo(1);

        login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(false))
                .andExpect(jsonPath("$.data.challengeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void failedOtpAttemptsConsumeBudgetAndFailChallenge() throws Exception {
        RegistrationData data = registrationData();
        String fingerprint = "fingerprint-failed-otp-" + data.sequence();
        register(data).andExpect(status().isCreated());

        JsonNode login = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String limitedToken = login.path("data").path("accessToken").asText();
        UUID challengeId = UUID.fromString(login.path("data").path("challengeId").asText());

        for (int attempt = 0; attempt < 5; attempt++) {
            verifyDevice(limitedToken, challengeId.toString(), "000000")
                    .andExpect(status().isUnauthorized());
        }

        assertThat(dsl.select(OTP_CHALLENGES.ATTEMPT_COUNT, OTP_CHALLENGES.STATUS)
                .from(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(challengeId))
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record.get(OTP_CHALLENGES.ATTEMPT_COUNT)).isEqualTo(5);
                    assertThat(record.get(OTP_CHALLENGES.STATUS)).isEqualTo("FAILED");
                });
    }

    @Test
    void verifiedLoginReturnsAccountCompletionFromAccountService() throws Exception {
        RegistrationData data = registrationData();
        String fingerprint = "fingerprint-account-completion-" + data.sequence();
        JsonNode registration = objectMapper.readTree(register(data)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        UUID userId = UUID.fromString(registration.path("data").path("userId").asText());

        JsonNode firstLogin = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        verifyDevice(firstLogin.path("data").path("accessToken").asText(), firstLogin.path("data").path("challengeId").asText())
                .andExpect(status().isOk());

        String profileId = UUID.randomUUID().toString();
        accountCompletionClient.result(new AccountCompletionResult(
                AccountCompletionStatus.KYC_REQUIRED,
                new ProfileSummary(profileId, ProfileType.PERSONAL, "Personal", null, true)
        ));

        login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_REQUIRED"))
                .andExpect(jsonPath("$.data.activeProfile.profileId").value(profileId))
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(false))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void loginRefinesKycRequiredWithKycStatusSoOnboardingCanComplete() throws Exception {
        RegistrationData data = registrationData();
        String fingerprint = "fingerprint-kyc-aware-" + data.sequence();
        register(data).andExpect(status().isCreated());

        JsonNode firstLogin = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        verifyDevice(firstLogin.path("data").path("accessToken").asText(), firstLogin.path("data").path("challengeId").asText())
                .andExpect(status().isOk());

        // Account exists (account-service reports KYC_REQUIRED); KYC ownership decides the real state.
        accountCompletionClient.result(new AccountCompletionResult(AccountCompletionStatus.KYC_REQUIRED, null));

        kycCompletionClient.status(AccountCompletionStatus.KYC_PENDING);
        login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_PENDING"));

        kycCompletionClient.status(AccountCompletionStatus.COMPLETED);
        login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("COMPLETED"));
    }

    @Test
    void loginDoesNotConsultKycBeforeAccountExists() throws Exception {
        RegistrationData data = registrationData();
        String fingerprint = "fingerprint-no-account-" + data.sequence();
        register(data).andExpect(status().isCreated());

        JsonNode firstLogin = objectMapper.readTree(login(data, fingerprint)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        verifyDevice(firstLogin.path("data").path("accessToken").asText(), firstLogin.path("data").path("challengeId").asText())
                .andExpect(status().isOk());

        // No account yet: account-service says ACCOUNT_REQUIRED and KYC must not override it.
        accountCompletionClient.result(new AccountCompletionResult(AccountCompletionStatus.ACCOUNT_REQUIRED, null));
        kycCompletionClient.status(AccountCompletionStatus.COMPLETED);

        login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ACCOUNT_REQUIRED"));
    }

    @Test
    void anonymousLoginWithRegisterReasonIssuesOnboardingToken() throws Exception {
        String response = anonymousLogin("REGISTER")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ONBOARDING"))
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(false))
                .andExpect(jsonPath("$.data.challengeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.activeProfile").value(org.hamcrest.Matchers.nullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String accessToken = objectMapper.readTree(response).path("data").path("accessToken").asText();
        com.nimbusds.jwt.JWTClaimsSet claims = com.nimbusds.jwt.SignedJWT.parse(accessToken).getJWTClaimsSet();

        assertThat(claims.getStringListClaim("scope")).containsExactlyInAnyOrder(
                "ONBOARDING_PHONE_SET",
                "ONBOARDING_DEVICE_VERIFY",
                "ONBOARDING_IAM_REGISTER"
        );
        assertThat(claims.getStringListClaim("roles")).containsExactly("ONBOARDING");
        assertThat(UUID.fromString(claims.getSubject())).isNotNull();
        assertThat(dsl.fetchCount(com.redbank.iam.jooq.tables.Users.USERS,
                com.redbank.iam.jooq.tables.Users.USERS.ID.eq(UUID.fromString(claims.getSubject()))))
                .isZero();
        long ttlSeconds = (claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()) / 1000;
        assertThat(ttlSeconds).isEqualTo(900);
    }

    @Test
    void anonymousLoginWithRecoveryReasonIsRejectedAsReserved() throws Exception {
        anonymousLogin("RECOVERY")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void loginWithoutReasonStillRequiresCredentials() throws Exception {
        mockMvc.perform(post("/api/v1/iam/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "context": {
                                    "requestId": "req-login-missing-credentials",
                                    "account": null,
                                    "os": null,
                                    "app": null,
                                    "device": {
                                      "deviceId": "client-device-x",
                                      "model": "Pixel 9",
                                      "fingerprint": "fingerprint-missing-credentials"
                                    },
                                    "network": null
                                  },
                                  "data": {
                                    "username": null,
                                    "password": null
                                  }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("VALIDATION_ERROR"));
    }

    @Test
    void onboardingPhoneChallengeVerifiesWithMockOtpAndBindsSession() throws Exception {
        AnonymousSession session = anonymousSession();
        String phone = "0935%07d".formatted(SEQUENCE.incrementAndGet());
        String challengeId = createOnboardingPhoneChallenge(session.sessionId(), phone);
        String fingerprint = "fp-onboarding-" + session.sessionId();

        verifyDeviceWithFingerprint(session.accessToken(), challengeId, "111111", fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.deviceId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.deviceStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.data.accessToken").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ONBOARDING"));

        var challenge = dsl.selectFrom(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(UUID.fromString(challengeId)))
                .fetchOne();
        assertThat(challenge.get(OTP_CHALLENGES.STATUS)).isEqualTo("VERIFIED");
        assertThat(challenge.get(OTP_CHALLENGES.DEVICE_FINGERPRINT)).isEqualTo(fingerprint);
        assertThat(challenge.get(OTP_CHALLENGES.PHONE_NUMBER)).isEqualTo(phone);
        assertThat(challenge.get(OTP_CHALLENGES.ONBOARDING_SESSION_ID))
                .isEqualTo(UUID.fromString(session.sessionId()));
    }

    @Test
    void onboardingPhoneChallengeRejectsOtherSessionsAndMissingFingerprint() throws Exception {
        AnonymousSession owner = anonymousSession();
        AnonymousSession stranger = anonymousSession();
        String phone = "0936%07d".formatted(SEQUENCE.incrementAndGet());
        String challengeId = createOnboardingPhoneChallenge(owner.sessionId(), phone);

        verifyDeviceWithFingerprint(stranger.accessToken(), challengeId, "111111", "fp-stranger")
                .andExpect(status().isUnauthorized());

        verifyDevice(owner.accessToken(), challengeId)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("VALIDATION_ERROR"));

        assertThat(dsl.select(OTP_CHALLENGES.STATUS)
                .from(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(UUID.fromString(challengeId)))
                .fetchOne(OTP_CHALLENGES.STATUS)).isEqualTo("PENDING");
    }

    @Test
    void onboardingPhoneChallengeWrongOtpConsumesAttempt() throws Exception {
        AnonymousSession session = anonymousSession();
        String phone = "0937%07d".formatted(SEQUENCE.incrementAndGet());
        String challengeId = createOnboardingPhoneChallenge(session.sessionId(), phone);

        verifyDeviceWithFingerprint(session.accessToken(), challengeId, "000000", "fp-wrong-otp")
                .andExpect(status().isUnauthorized());

        var challenge = dsl.selectFrom(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(UUID.fromString(challengeId)))
                .fetchOne();
        assertThat(challenge.get(OTP_CHALLENGES.ATTEMPT_COUNT)).isEqualTo(1);
        assertThat(challenge.get(OTP_CHALLENGES.STATUS)).isEqualTo("PENDING");
        assertThat(challenge.get(OTP_CHALLENGES.DEVICE_FINGERPRINT)).isNull();
    }

    @Test
    void registerWithVerifiedOnboardingSessionAdoptsDeviceAndSkipsSecondOtp() throws Exception {
        RegistrationData data = registrationData();
        AnonymousSession session = anonymousSession();
        String fingerprint = "fp-wizard-" + data.sequence();
        String challengeId = createOnboardingPhoneChallenge(session.sessionId(), data.phoneNumber());
        verifyDeviceWithFingerprint(session.accessToken(), challengeId, "111111", fingerprint)
                .andExpect(status().isOk());

        String response = registerWithToken(session.accessToken(), data, data.phoneNumber())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.status.code").value("CREATED"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ACCOUNT_REQUIRED"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(response).path("data").path("userId").asText());

        var device = dsl.selectFrom(DEVICES)
                .where(DEVICES.USER_ID.eq(userId))
                .fetchOne();
        assertThat(device).isNotNull();
        assertThat(device.get(DEVICES.DEVICE_FINGERPRINT)).isEqualTo(fingerprint);
        assertThat(device.get(DEVICES.STATUS)).isEqualTo("VERIFIED");
        assertThat(device.get(DEVICES.VERIFIED_AT)).isNotNull();
        assertThat(dsl.fetchCount(
                OUTBOX_EVENTS,
                OUTBOX_EVENTS.AGGREGATE_ID.eq(device.get(DEVICES.ID).toString())
                        .and(OUTBOX_EVENTS.EVENT_TYPE.eq("DEVICE_VERIFIED"))
        )).isEqualTo(1);

        // The adopted device is already trusted: no second OTP at login.
        login(data, fingerprint)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.deviceVerificationRequired").value(false))
                .andExpect(jsonPath("$.data.challengeId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void registerWithMismatchedPhoneAgainstVerifiedSessionFails() throws Exception {
        RegistrationData data = registrationData();
        AnonymousSession session = anonymousSession();
        String verifiedPhone = "0938%07d".formatted(SEQUENCE.incrementAndGet());
        String challengeId = createOnboardingPhoneChallenge(session.sessionId(), verifiedPhone);
        verifyDeviceWithFingerprint(session.accessToken(), challengeId, "111111", "fp-mismatch-" + data.sequence())
                .andExpect(status().isOk());

        registerWithToken(session.accessToken(), data, data.phoneNumber())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));

        assertThat(dsl.fetchCount(
                com.redbank.iam.jooq.tables.Users.USERS,
                com.redbank.iam.jooq.tables.Users.USERS.USERNAME.eq(data.username())
        )).isZero();
    }

    @Test
    void registerWithUnverifiedOnboardingSessionSkipsAdoption() throws Exception {
        RegistrationData data = registrationData();
        AnonymousSession session = anonymousSession();

        String response = registerWithToken(session.accessToken(), data, data.phoneNumber())
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        UUID userId = UUID.fromString(objectMapper.readTree(response).path("data").path("userId").asText());

        assertThat(dsl.fetchCount(DEVICES, DEVICES.USER_ID.eq(userId))).isZero();
    }

    @Test
    void registerRejectsInvalidBearerToken() throws Exception {
        RegistrationData data = registrationData();

        registerWithToken("not-a-real-token", data, data.phoneNumber())
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.context.status.code").value("UNAUTHORIZED"));
    }

    private ResultActions registerWithToken(String token, RegistrationData data, String phoneNumber) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/register")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-wizard-register-%s",
                            "account": null,
                            "os": {
                              "name": "Web",
                              "version": "1"
                            },
                            "app": null,
                            "device": {
                              "deviceId": "pwa-device",
                              "model": "PWA",
                              "fingerprint": "context-fingerprint-%s"
                            },
                            "network": null
                          },
                          "data": {
                            "nationalId": "%s",
                            "phoneNumber": "%s",
                            "username": "%s",
                            "password": "%s",
                            "firstName": "Wizard",
                            "lastName": "User",
                            "location": null,
                            "jobId": "job-engineer"
                          }
                        }
                        """.formatted(
                        data.sequence(),
                        data.sequence(),
                        data.nationalId(),
                        phoneNumber,
                        data.username(),
                        data.password()
                )));
    }

    private AnonymousSession anonymousSession() throws Exception {
        String response = anonymousLogin("REGISTER")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String accessToken = objectMapper.readTree(response).path("data").path("accessToken").asText();
        String sessionId = com.nimbusds.jwt.SignedJWT.parse(accessToken).getJWTClaimsSet().getSubject();
        return new AnonymousSession(sessionId, accessToken);
    }

    private String createOnboardingPhoneChallenge(String sessionId, String phone) throws Exception {
        String response = mockMvc.perform(post("/internal/v1/iam/otp-challenge/create")
                        .header("X-Internal-Service-Token", "local-develop-internal-service-token-change-me")
                        .contentType("application/json")
                        .content("""
                                {
                                  "onboardingSessionId": "%s",
                                  "phoneNumber": "%s"
                                }
                                """.formatted(sessionId, phone)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("challengeId").asText();
    }

    private ResultActions verifyDeviceWithFingerprint(
            String token,
            String challengeId,
            String otpCode,
            String fingerprint
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/device-verify")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-onboarding-verify",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": {
                              "deviceId": "pwa-device",
                              "model": "PWA",
                              "fingerprint": "%s"
                            },
                            "network": null
                          },
                          "data": {
                            "challengeId": "%s",
                            "otpCode": "%s"
                          }
                        }
                        """.formatted(fingerprint, challengeId, otpCode)));
    }

    private record AnonymousSession(String sessionId, String accessToken) {
    }

    private ResultActions anonymousLogin(String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/login")
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-anonymous-login",
                            "account": null,
                            "os": {
                              "name": "Web",
                              "version": "1"
                            },
                            "app": null,
                            "device": {
                              "deviceId": "pwa-device",
                              "model": "PWA",
                              "fingerprint": "fingerprint-anonymous-onboarding"
                            },
                            "network": null
                          },
                          "data": {
                            "username": null,
                            "password": null,
                            "reason": "%s"
                          }
                        }
                        """.formatted(reason)));
    }

    @Test
    void outboxRetriesMoveEventToDeadLetter() {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.parse("2026-06-14T12:00:00Z");
        outboxEventRepository.save(new OutboxEventRecord(
                eventId,
                "USER",
                UUID.randomUUID().toString(),
                "TEST_EVENT",
                "{}",
                OutboxEventStatus.PENDING,
                now,
                now,
                null,
                0
        ));

        for (int attempt = 0; attempt < 5; attempt++) {
            outboxEventRepository.markAttemptFailed(eventId, now.plusSeconds(attempt + 1L), 5);
        }

        assertThat(dsl.select(OUTBOX_EVENTS.STATUS, OUTBOX_EVENTS.RETRY_COUNT)
                .from(OUTBOX_EVENTS)
                .where(OUTBOX_EVENTS.ID.eq(eventId))
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record.get(OUTBOX_EVENTS.STATUS)).isEqualTo("DEAD_LETTER");
                    assertThat(record.get(OUTBOX_EVENTS.RETRY_COUNT)).isEqualTo(5);
                });
    }

    private org.springframework.test.web.servlet.ResultActions register(RegistrationData data) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/register")
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-register-%s",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "nationalId": "%s",
                            "phoneNumber": "%s",
                            "username": "%s",
                            "password": "%s",
                            "firstName": "Ali",
                            "lastName": "Nazari",
                            "location": {
                              "countryCode": "IR",
                              "province": "Tehran",
                              "city": "Tehran",
                              "address": "Main street",
                              "postalCode": "1234567890"
                            },
                            "jobId": "job-engineer"
                          }
                        }
                        """.formatted(data.sequence(), data.nationalId(), data.phoneNumber(), data.username(), data.password())));
    }

    private org.springframework.test.web.servlet.ResultActions login(RegistrationData data, String fingerprint) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/login")
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-login-%s",
                            "account": null,
                            "os": {
                              "name": "Android",
                              "version": "15"
                            },
                            "app": {
                              "version": "1.0.0",
                              "buildNumber": "100",
                              "isRooted": false,
                              "language": "en-US",
                              "theme": "dark"
                            },
                            "device": {
                              "deviceId": "client-device-%s",
                              "model": "Pixel 9",
                              "fingerprint": "%s"
                            },
                            "network": null
                          },
                          "data": {
                            "username": "%s",
                            "password": "%s"
                          }
                        }
                        """.formatted(data.sequence(), data.sequence(), fingerprint, data.username(), data.password())));
    }

    private org.springframework.test.web.servlet.ResultActions verifyDevice(String limitedToken, String challengeId) throws Exception {
        return verifyDevice(limitedToken, challengeId, "111111");
    }

    private org.springframework.test.web.servlet.ResultActions verifyDevice(
            String limitedToken,
            String challengeId,
            String otpCode
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/device-verify")
                .header("Authorization", "Bearer " + limitedToken)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-device-verify",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "challengeId": "%s",
                            "otpCode": "%s"
                          }
                        }
                        """.formatted(challengeId, otpCode)));
    }

    private RegistrationData registrationData() {
        int sequence = SEQUENCE.incrementAndGet();
        String numeric = "%010d".formatted(sequence);
        return new RegistrationData(
                sequence,
                "user" + sequence,
                "Password" + sequence + "!",
                numeric,
                "0912" + "%07d".formatted(sequence)
        );
    }

    private record RegistrationData(
            int sequence,
            String username,
            String password,
            String nationalId,
            String phoneNumber
    ) {
    }

    @TestConfiguration
    static class AccountCompletionTestConfiguration {
        @Bean
        @Primary
        MutableAccountCompletionClient mutableAccountCompletionClient() {
            return new MutableAccountCompletionClient();
        }

        @Bean
        @Primary
        MutableKycCompletionClient mutableKycCompletionClient() {
            return new MutableKycCompletionClient();
        }
    }

    static class MutableAccountCompletionClient implements AccountCompletionClient {
        private AccountCompletionResult result = new AccountCompletionResult(AccountCompletionStatus.ACCOUNT_REQUIRED, null);

        void result(AccountCompletionResult result) {
            this.result = result;
        }

        @Override
        public AccountCompletionResult getCompletion(UUID userId) {
            return result;
        }
    }

    static class MutableKycCompletionClient implements com.redbank.iam.application.KycCompletionClient {
        private AccountCompletionStatus status = AccountCompletionStatus.KYC_REQUIRED;

        void status(AccountCompletionStatus status) {
            this.status = status;
        }

        @Override
        public AccountCompletionStatus getCompletionStatus(UUID userId) {
            return status;
        }
    }
    private ResultActions register(RegisterRequest request) throws Exception {
        return mockMvc.perform(post("/api/v1/iam/register")
                .contentType("application/json")
                .content("""
                    {
                      "context": {
                        "requestId": "req-register-test",
                        "account": null,
                        "os": null,
                        "app": null,
                        "device": null,
                        "network": null
                      },
                      "data": %s
                    }
                    """.formatted(objectMapper.writeValueAsString(request))));
    }
    @Test
    void registerSucceedsWhenLocationIsNull() throws Exception{
        RegisterRequest request = new RegisterRequest(
                "1234328234",
                "09387652314",
                "shimo",
                "45326743@Ei",
                "shima",
                "azizi",
                null,
                "job-1"
        );
        register(request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.status.code").value("CREATED"))
                .andExpect(jsonPath("$.data.userId").isNotEmpty());

    }

}

