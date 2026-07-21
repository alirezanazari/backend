package com.redbank.kyc.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.kyc.application.FeatureFlagClient;
import com.redbank.kyc.application.OtpChallengeClient;
import com.redbank.kyc.application.ReferralRewardClient;
import com.redbank.kyc.application.ReferrerAccountClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.jooq.DSLContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.redbank.kyc.jooq.tables.KycCaseHistory.KYC_CASE_HISTORY;
import static com.redbank.kyc.jooq.tables.KycCases.KYC_CASES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the temporary default-approve behavior (TODO(kyc-panel)): when no KYC case exists and
 * auto-approve is enabled, completion resolves to COMPLETED so onboarding can finish without an
 * admin review panel.
 */
@SpringBootTest(properties = {
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.kyc.auto-approve-enabled=true"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
@Testcontainers
class InternalKycCompletionAutoApproveTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";
    private static final String SERVICE_TOKEN = "local-develop-internal-service-token-change-me";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private ReferrerAccountClient referrerAccountClient;

    @MockitoBean
    private ReferralRewardClient referralRewardClient;

    @MockitoBean
    private FeatureFlagClient featureFlagClient;

    @MockitoBean
    private OtpChallengeClient otpChallengeClient;

    @BeforeEach
    void clean() {
        dsl.deleteFrom(KYC_CASE_HISTORY).execute();
        dsl.deleteFrom(KYC_CASES).execute();
    }

    @Test
    void internalCompletionAutoApprovesWhenNoCaseExists() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/internal/v1/kyc/completion/get")
                        .header("X-Internal-Service-Token", SERVICE_TOKEN)
                        .contentType("application/json")
                        .content("{\"userId\":\"%s\"}".formatted(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycStatus").value("APPROVED"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("COMPLETED"));

        var kycCase = dsl.selectFrom(KYC_CASES).where(KYC_CASES.USER_ID.eq(userId)).fetchOne();
        assertThat(kycCase).isNotNull();
        assertThat(kycCase.get(KYC_CASES.STATUS)).isEqualTo("APPROVED");
        assertThat(dsl.fetchCount(
                KYC_CASE_HISTORY,
                KYC_CASE_HISTORY.KYC_CASE_ID.eq(kycCase.get(KYC_CASES.ID))
                        .and(KYC_CASE_HISTORY.NEW_STATUS.eq("APPROVED"))
        )).isEqualTo(1);
    }

    @Test
    void repeatedCompletionReusesTheSameAutoApprovedCase() throws Exception {
        UUID userId = UUID.randomUUID();

        completion(userId).andExpect(status().isOk());
        completion(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("COMPLETED"));

        assertThat(dsl.fetchCount(KYC_CASES, KYC_CASES.USER_ID.eq(userId))).isEqualTo(1);
    }

    @Test
    void userStatusGetAlsoReflectsAutoApproval() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/kyc/status/get")
                        .header("Authorization", "Bearer " + userToken(userId))
                        .contentType("application/json")
                        .content("""
                                {
                                  "context": {
                                    "requestId": "req-auto-approve-status",
                                    "account": null,
                                    "os": null,
                                    "app": null,
                                    "device": null,
                                    "network": null
                                  },
                                  "data": {}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("COMPLETED"));
    }

    private org.springframework.test.web.servlet.ResultActions completion(UUID userId) throws Exception {
        return mockMvc.perform(post("/internal/v1/kyc/completion/get")
                .header("X-Internal-Service-Token", SERVICE_TOKEN)
                .contentType("application/json")
                .content("{\"userId\":\"%s\"}".formatted(userId)));
    }

    private String userToken(UUID userId) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-develop")
                .audience("red-bank-api")
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("roles", List.of("USER"))
                .claim("scope", List.of("USER"))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
