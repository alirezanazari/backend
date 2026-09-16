package com.redbank.iam.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.redbank.iam.jooq.tables.OtpChallenges.OTP_CHALLENGES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.internal.otp-challenge-create-token-secret=test-otp-challenge-create-token",
        "redbank.otp.ttl-seconds=300",
        "redbank.otp.resend-cooldown-seconds=45",
        "redbank.otp.max-per-phone-per-hour=5",
        "redbank.events.publisher.enabled=false"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
@Testcontainers
class InternalOtpChallengeIntegrationTest {
    private static final String SERVICE_TOKEN = "test-otp-challenge-create-token";
    private static final AtomicInteger SEQUENCE = new AtomicInteger(5000);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

    @Test
    void createsHashedPhoneChallengeWithResendCooldown() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String phone = uniquePhone();

        System.out.println("PHONE=[" + phone + "] LENGTH=" + phone.length());
        Instant before = Instant.now();

        String response = createChallenge(SERVICE_TOKEN, sessionId.toString(), phone)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.challengeId").isNotEmpty())
                .andExpect(jsonPath("$.data.expiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.resendAvailableAt").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        Instant expiresAt = Instant.parse(data.path("expiresAt").asText());
        Instant resendAvailableAt = Instant.parse(data.path("resendAvailableAt").asText());
        assertThat(expiresAt).isAfter(before.plusSeconds(250));
        assertThat(resendAvailableAt).isAfter(before.plusSeconds(30));
        assertThat(resendAvailableAt).isBefore(before.plusSeconds(60));

        var challenge = dsl.selectFrom(OTP_CHALLENGES)
                .where(OTP_CHALLENGES.ID.eq(UUID.fromString(data.path("challengeId").asText())))
                .fetchOne();
        assertThat(challenge.get(OTP_CHALLENGES.PURPOSE)).isEqualTo("PHONE_VERIFICATION");
        assertThat(challenge.get(OTP_CHALLENGES.STATUS)).isEqualTo("PENDING");
        assertThat(challenge.get(OTP_CHALLENGES.PHONE_NUMBER)).isEqualTo(phone);
        assertThat(challenge.get(OTP_CHALLENGES.ONBOARDING_SESSION_ID)).isEqualTo(sessionId);
        assertThat(challenge.get(OTP_CHALLENGES.USER_ID)).isNull();
        assertThat(challenge.get(OTP_CHALLENGES.DEVICE_ID)).isNull();
        assertThat(challenge.get(OTP_CHALLENGES.OTP_HASH)).startsWith("$2");
    }

    @Test
    void enforcesResendCooldownPerPhone() throws Exception {
        UUID sessionId = UUID.randomUUID();
        String phone = uniquePhone();

        createChallenge(SERVICE_TOKEN, sessionId.toString(), phone).andExpect(status().isOk());
        createChallenge(SERVICE_TOKEN, sessionId.toString(), phone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("LIMIT_EXCEEDED"));

        // Rotating the onboarding session must not bypass the per-phone cooldown.
        createChallenge(SERVICE_TOKEN, UUID.randomUUID().toString(), phone)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("LIMIT_EXCEEDED"));
    }

    @Test
    void rejectsAlreadyRegisteredPhone() throws Exception {
        String phone = uniquePhone();
        registerUser(phone);

        createChallenge(SERVICE_TOKEN, UUID.randomUUID().toString(), phone)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.context.status.code").value("CONFLICT"));
    }

    @Test
    void rejectsInvalidPhoneAndSession() throws Exception {
        createChallenge(SERVICE_TOKEN, UUID.randomUUID().toString(), "not-a-phone")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("VALIDATION_ERROR"));

        createChallenge(SERVICE_TOKEN, "not-a-uuid", uniquePhone())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("VALIDATION_ERROR"));
    }

    @Test
    void rejectsMissingOrWrongServiceToken() throws Exception {
        createChallenge(null, UUID.randomUUID().toString(), uniquePhone())
                .andExpect(status().isUnauthorized());
        createChallenge("wrong-token", UUID.randomUUID().toString(), uniquePhone())
                .andExpect(status().isUnauthorized());
    }

    private ResultActions createChallenge(String serviceToken, String sessionId, String phone) throws Exception {
        var request = post("/internal/v1/iam/otp-challenge/create")
                .contentType("application/json")
                .content("""
                        {
                          "onboardingSessionId": "%s",
                          "phoneNumber": "%s"
                        }
                        """.formatted(sessionId, phone));
        if (serviceToken != null) {
            request = request.header("X-Internal-Service-Token", serviceToken);
        }
        return mockMvc.perform(request);
    }

    private void registerUser(String phone) throws Exception {
        int sequence = SEQUENCE.incrementAndGet();
        String nationalId = String.format(Locale.ROOT, "%010d", sequence);
        mockMvc.perform(post("/api/v1/iam/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "context": {
                                    "requestId": "req-internal-otp-register-%s",
                                    "account": null,
                                    "os": null,
                                    "app": null,
                                    "device": null,
                                    "network": null
                                  },
                                  "data": {
                                    "nationalId": "%s",
                                    "phoneNumber": "%s",
                                    "username": "otpuser%s",
                                    "password": "Password%s!",
                                    "firstName": "Otp",
                                    "lastName": "User",
                                    "location": null,
                                    "jobId": "job-engineer"
                                  }
                                }
                                """.formatted(sequence, nationalId, phone, sequence, sequence)))
                .andDo(result -> System.out.println(result.getResponse().getContentAsString()))
                .andExpect(status().isCreated());
    }
    private String uniquePhone() {
        return "093" + String.format(
                Locale.ROOT,
                "%08d",
                SEQUENCE.incrementAndGet()
        );
    }

}
