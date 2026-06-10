package com.redbank.kyc.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.kyc.application.ActiveReferrerAccount;
import com.redbank.kyc.application.FeatureFlagClient;
import com.redbank.kyc.application.ReferralRewardClient;
import com.redbank.kyc.application.ReferralRewardResult;
import com.redbank.kyc.application.ReferrerAccountClient;
import com.redbank.kyc.application.ResolvedReferrer;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static com.redbank.kyc.jooq.tables.KycCaseHistory.KYC_CASE_HISTORY;
import static com.redbank.kyc.jooq.tables.KycCases.KYC_CASES;
import static com.redbank.kyc.jooq.tables.ReferralRewards.REFERRAL_REWARDS;
import static com.redbank.kyc.jooq.tables.Referrals.REFERRALS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:kyctest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.referrer-account-client.enabled=true",
        "redbank.referral-reward-client.enabled=true"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class KycControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";

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

    @BeforeEach
    void clean() {
        dsl.deleteFrom(KYC_CASE_HISTORY).execute();
        dsl.deleteFrom(REFERRAL_REWARDS).execute();
        dsl.deleteFrom(REFERRALS).execute();
        dsl.deleteFrom(KYC_CASES).execute();
        reset(referrerAccountClient, referralRewardClient, featureFlagClient);
        when(featureFlagClient.isEnabled(anyString())).thenReturn(true);
    }

    @Test
    void jobsListReturnsOnlySeededActiveJobsWithoutAuthentication() throws Exception {
        api("/api/v1/kyc/jobs/list", null, """
                {"language":"en-US"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobs.length()").value(4))
                .andExpect(jsonPath("$.data.jobs[0].jobId").value("job-business-owner"))
                .andExpect(jsonPath("$.data.jobs[0].code").value("BUSINESS_OWNER"));
    }

    @Test
    void statusDefaultsToNotStartedAndDerivesKycRequired() throws Exception {
        UUID userId = UUID.randomUUID();

        api("/api/v1/kyc/status/get", token(userId, List.of("USER"), List.of()), "{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.kycCaseId").doesNotExist())
                .andExpect(jsonPath("$.data.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_REQUIRED"));
    }

    @Test
    void referralIsIdempotentForSameCodeAndImmutableForAnotherCode() throws Exception {
        UUID referredUserId = UUID.randomUUID();
        UUID referrerUserId = UUID.randomUUID();
        String userToken = token(
                referredUserId,
                List.of(),
                List.of("ONBOARDING_REFERRAL_SET")
        );
        when(referrerAccountClient.resolveByCode("ALICE_2026"))
                .thenReturn(new ResolvedReferrer(referrerUserId, "Alice"));

        api("/api/v1/kyc/referral/set", userToken, """
                {"referralCode":"alice_2026"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.referrerName").value("Alice"))
                .andExpect(jsonPath("$.data.status").value("PENDING_KYC"));

        api("/api/v1/kyc/referral/set", userToken, """
                {"referralCode":"ALICE_2026"}
                """)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING_KYC"));

        api("/api/v1/kyc/referral/set", userToken, """
                {"referralCode":"BOB_2026"}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.context.status.code").value("CONFLICT"));

        assertThat(dsl.fetchCount(REFERRALS)).isEqualTo(1);
        verify(referrerAccountClient, times(1)).resolveByCode("ALICE_2026");
    }

    @Test
    void approvedKycRequestsReferralRewardOnlyOnceAndStoresAuditHistory() throws Exception {
        UUID referredUserId = UUID.randomUUID();
        UUID referrerUserId = UUID.randomUUID();
        String referrerAccount = "123456789012345678";
        when(referrerAccountClient.resolveByCode(anyString()))
                .thenReturn(new ResolvedReferrer(referrerUserId, "Alice"));
        when(referrerAccountClient.findActiveAccount(referrerUserId))
                .thenReturn(new ActiveReferrerAccount(referrerUserId, referrerAccount, "IRR"));
        when(referralRewardClient.requestReward(any()))
                .thenReturn(new ReferralRewardResult(UUID.randomUUID().toString(), "POSTED"));

        api(
                "/api/v1/kyc/referral/set",
                token(referredUserId, List.of("USER"), List.of()),
                """
                        {"referralCode":"ALICE_2026"}
                        """
        ).andExpect(status().isCreated());

        String adminToken = token(UUID.randomUUID(), List.of("ADMIN"), List.of());
        String completion = """
                {
                  "userId":"%s",
                  "status":"APPROVED",
                  "reason":"Documents verified"
                }
                """.formatted(referredUserId);
        api("/api/v1/kyc/complete", adminToken, completion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.referralRewardRequested").value(true));

        api("/api/v1/kyc/complete", adminToken, completion)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralRewardRequested").value(false));

        api(
                "/api/v1/kyc/status/get",
                token(referredUserId, List.of("USER"), List.of()),
                "{}"
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("COMPLETED"));

        assertThat(dsl.fetchCount(KYC_CASES)).isEqualTo(1);
        assertThat(dsl.fetchCount(KYC_CASE_HISTORY)).isEqualTo(1);
        assertThat(dsl.fetchCount(REFERRAL_REWARDS)).isEqualTo(1);
        assertThat(dsl.select(REFERRALS.STATUS).from(REFERRALS).fetchOne(REFERRALS.STATUS))
                .isEqualTo("REWARDED");
        verify(referralRewardClient, times(1)).requestReward(any());
    }

    @Test
    void disabledReferralRewardFlagKeepsPreparedRewardPending() throws Exception {
        UUID referredUserId = UUID.randomUUID();
        UUID referrerUserId = UUID.randomUUID();
        when(referrerAccountClient.resolveByCode(anyString()))
                .thenReturn(new ResolvedReferrer(referrerUserId, "Alice"));
        when(featureFlagClient.isEnabled("referral.reward.enabled")).thenReturn(false);

        api(
                "/api/v1/kyc/referral/set",
                token(referredUserId, List.of("USER"), List.of()),
                """
                        {"referralCode":"ALICE_2026"}
                        """
        ).andExpect(status().isCreated());

        api(
                "/api/v1/kyc/complete",
                token(UUID.randomUUID(), List.of("ADMIN"), List.of()),
                """
                        {
                          "userId":"%s",
                          "status":"APPROVED",
                          "reason":"Documents verified"
                        }
                        """.formatted(referredUserId)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referralRewardRequested").value(false));

        assertThat(dsl.select(REFERRAL_REWARDS.STATUS)
                .from(REFERRAL_REWARDS)
                .fetchOne(REFERRAL_REWARDS.STATUS)).isEqualTo("PENDING");
        verify(referralRewardClient, times(0)).requestReward(any());
    }

    @Test
    void rejectionRequiresReasonAndNormalUserCannotCompleteKyc() throws Exception {
        UUID userId = UUID.randomUUID();
        String request = """
                {
                  "userId":"%s",
                  "status":"REJECTED",
                  "reason":null
                }
                """.formatted(userId);

        api("/api/v1/kyc/complete", token(UUID.randomUUID(), List.of("ADMIN"), List.of()), request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("VALIDATION_ERROR"));

        api("/api/v1/kyc/complete", token(userId, List.of("USER"), List.of()), request)
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions api(
            String path,
            String token,
            String data
    ) throws Exception {
        var request = post(path)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-kyc-test",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": %s
                        }
                        """.formatted(data));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    private String token(UUID userId, List<String> roles, List<String> scopes) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-develop")
                .audience("red-bank-api")
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("profile_id", null)
                .claim("device_id", UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("scope", scopes)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
