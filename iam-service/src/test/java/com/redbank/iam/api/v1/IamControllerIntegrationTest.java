package com.redbank.iam.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.iam.api.v1.dto.RegisterRequest;
import com.redbank.iam.application.AccountCompletionClient;
import com.redbank.iam.application.AccountCompletionResult;
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
import static com.redbank.iam.jooq.tables.OtpChallenges.OTP_CHALLENGES;
import static com.redbank.iam.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static javax.security.sasl.Sasl.CREDENTIALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void resetAccountCompletion() {
        accountCompletionClient.result(new AccountCompletionResult(AccountCompletionStatus.ACCOUNT_REQUIRED, null));
    }

    @Test
    void registerCreatesUserCredentialAndLimitedToken() throws Exception {
        RegistrationData data = registrationData();

        String response = register(data)
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

