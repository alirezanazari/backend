package com.redbank.featureflag.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileType;
import com.redbank.featureflag.application.FeatureEvaluationContextClient;
import com.redbank.featureflag.domain.FeatureEvaluationContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

import static com.redbank.featureflag.jooq.tables.FeatureFlagAuditLogs.FEATURE_FLAG_AUDIT_LOGS;
import static com.redbank.featureflag.jooq.tables.FeatureFlagRuleConditions.FEATURE_FLAG_RULE_CONDITIONS;
import static com.redbank.featureflag.jooq.tables.FeatureFlagTargetingAuditLogs.FEATURE_FLAG_TARGETING_AUDIT_LOGS;
import static com.redbank.featureflag.jooq.tables.FeatureFlagTargetingRules.FEATURE_FLAG_TARGETING_RULES;
import static com.redbank.featureflag.jooq.tables.FeatureFlagUserOverrides.FEATURE_FLAG_USER_OVERRIDES;
import static com.redbank.featureflag.jooq.tables.FeatureFlags.FEATURE_FLAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.internal.service-token-secret=test-internal-service-token"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
@Testcontainers
class FeatureFlagControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";
    private static final String INTERNAL_TOKEN = "test-internal-service-token";

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private FeatureEvaluationContextClient evaluationContextClient;

    @BeforeEach
    void resetFlags() {
        dsl.deleteFrom(FEATURE_FLAG_RULE_CONDITIONS).execute();
        dsl.deleteFrom(FEATURE_FLAG_TARGETING_RULES).execute();
        dsl.deleteFrom(FEATURE_FLAG_USER_OVERRIDES).execute();
        dsl.deleteFrom(FEATURE_FLAG_TARGETING_AUDIT_LOGS).execute();
        dsl.deleteFrom(FEATURE_FLAG_AUDIT_LOGS).execute();
        dsl.update(FEATURE_FLAGS)
                .set(FEATURE_FLAGS.ENABLED, true)
                .set(FEATURE_FLAGS.CLIENT_VISIBLE, false)
                .set(FEATURE_FLAGS.UPDATED_BY, (UUID) null)
                .set(FEATURE_FLAGS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
        dsl.update(FEATURE_FLAGS)
                .set(FEATURE_FLAGS.CLIENT_VISIBLE, true)
                .where(FEATURE_FLAGS.FLAG_KEY.in(
                        "transfer.card-to-card.enabled",
                        "transfer.account-to-account.enabled",
                        "transfer.card-to-account.enabled",
                        "transfer.account-to-card.enabled",
                        "card.order.enabled"
                ))
                .execute();
        reset(evaluationContextClient);
        when(evaluationContextClient.resolve(
                any(UUID.class),
                nullable(UUID.class),
                nullable(String.class)
        )).thenAnswer(invocation -> context(
                invocation.getArgument(0),
                invocation.getArgument(1),
                ProfileType.PERSONAL,
                new BigDecimal("0")
        ));
    }

    @Test
    void adminCanListSeededFlagsAndFilterByKey() throws Exception {
        String adminToken = token(UUID.randomUUID(), List.of("ADMIN"));

        api("/api/v1/feature-flags/list", adminToken, null, """
                {"keys":null,"includeDisabled":false}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags.length()").value(6))
                .andExpect(jsonPath("$.data.flags[0].key").value("card.order.enabled"));

        api("/api/v1/feature-flags/list", adminToken, null, """
                {"keys":["referral.reward.enabled"],"includeDisabled":true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags.length()").value(1))
                .andExpect(jsonPath("$.data.flags[0].enabled").value(true));
    }

    @Test
    void internalServiceCanListFlagsWithoutAdminJwt() throws Exception {
        api("/api/v1/feature-flags/list", null, INTERNAL_TOKEN, """
                {"keys":["card.order.enabled"],"includeDisabled":true}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags[0].key").value("card.order.enabled"));
    }

    @Test
    void updateChangesFlagAndStoresAuditDetails() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminToken = token(adminId, List.of("ADMIN"));

        api("/api/v1/feature-flags/update", adminToken, null, """
                {
                  "key":"CARD.ORDER.ENABLED",
                  "enabled":false,
                  "description":null,
                  "clientVisible":false,
                  "reason":"Pause card fulfillment"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flag.key").value("card.order.enabled"))
                .andExpect(jsonPath("$.data.flag.enabled").value(false))
                .andExpect(jsonPath("$.data.flag.description").value("Allows customers to order physical cards."))
                .andExpect(jsonPath("$.data.flag.clientVisible").value(false))
                .andExpect(jsonPath("$.data.updatedAt").exists());

        assertThat(dsl.fetchCount(FEATURE_FLAG_AUDIT_LOGS)).isEqualTo(1);
        assertThat(dsl.select(FEATURE_FLAG_AUDIT_LOGS.CHANGED_BY)
                .from(FEATURE_FLAG_AUDIT_LOGS)
                .fetchOne(FEATURE_FLAG_AUDIT_LOGS.CHANGED_BY)).isEqualTo(adminId);
        assertThat(dsl.select(FEATURE_FLAG_AUDIT_LOGS.REQUEST_ID)
                .from(FEATURE_FLAG_AUDIT_LOGS)
                .fetchOne(FEATURE_FLAG_AUDIT_LOGS.REQUEST_ID)).isEqualTo("req-feature-flag-test");
        assertThat(dsl.select(FEATURE_FLAG_AUDIT_LOGS.OLD_ENABLED, FEATURE_FLAG_AUDIT_LOGS.NEW_ENABLED)
                .from(FEATURE_FLAG_AUDIT_LOGS)
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record.value1()).isTrue();
                    assertThat(record.value2()).isFalse();
                });
        assertThat(dsl.select(
                        FEATURE_FLAG_AUDIT_LOGS.OLD_CLIENT_VISIBLE,
                        FEATURE_FLAG_AUDIT_LOGS.NEW_CLIENT_VISIBLE
                )
                .from(FEATURE_FLAG_AUDIT_LOGS)
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record.value1()).isTrue();
                    assertThat(record.value2()).isFalse();
                });

        api("/api/v1/feature-flags/list", adminToken, null, """
                {"keys":["card.order.enabled"],"includeDisabled":false}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags.length()").value(0));
    }

    @Test
    void normalUserCannotListOrUpdateFlags() throws Exception {
        String userToken = token(UUID.randomUUID(), List.of("USER"));

        api("/api/v1/feature-flags/list", userToken, null, """
                {"keys":null,"includeDisabled":true}
                """)
                .andExpect(status().isUnauthorized());

        api("/api/v1/feature-flags/update", userToken, null, """
                {
                  "key":"card.order.enabled",
                  "enabled":false,
                  "description":null,
                  "reason":"Not allowed"
                }
                """)
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userCanEvaluateOnlyClientVisibleFlagsAndCannotChooseSubject() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String userToken = token(userId, profileId, List.of("USER"));

        api("/api/v1/feature-flags/evaluate", userToken, null, """
                {
                  "keys":["card.order.enabled","referral.reward.enabled"],
                  "subjectUserId":null,
                  "profileId":null,
                  "accountNumber":null
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags.length()").value(1))
                .andExpect(jsonPath("$.data.flags[0].key").value("card.order.enabled"))
                .andExpect(jsonPath("$.data.flags[0].enabled").value(true));

        api("/api/v1/feature-flags/evaluate", userToken, null, """
                {
                  "keys":["card.order.enabled"],
                  "subjectUserId":"%s",
                  "profileId":null,
                  "accountNumber":null
                }
                """.formatted(UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void targetingUsesOverrideThenFirstRuleThenGlobalDefault() throws Exception {
        UUID adminId = UUID.randomUUID();
        String adminToken = token(adminId, List.of("ADMIN"));
        UUID overrideUser = UUID.randomUUID();
        UUID businessUser = UUID.randomUUID();
        UUID highBalanceUser = UUID.randomUUID();
        UUID defaultUser = UUID.randomUUID();

        api("/api/v1/feature-flags/update", adminToken, null, """
                {
                  "key":"card.order.enabled",
                  "enabled":false,
                  "description":null,
                  "clientVisible":true,
                  "reason":"Use targeted rollout"
                }
                """).andExpect(status().isOk());

        api("/api/v1/feature-flags/targeting/update", adminToken, null, """
                {
                  "key":"card.order.enabled",
                  "userOverrides":[
                    {"userId":"%s","enabled":true}
                  ],
                  "rules":[
                    {
                      "name":"Hold business profiles",
                      "priority":10,
                      "enabled":false,
                      "matchType":"ALL",
                      "active":true,
                      "conditions":[
                        {"attribute":"PROFILE_TYPE","operator":"EQUALS","values":["BUSINESS"]}
                      ]
                    },
                    {
                      "name":"High balance rollout",
                      "priority":20,
                      "enabled":true,
                      "matchType":"ALL",
                      "active":true,
                      "conditions":[
                        {
                          "attribute":"AVAILABLE_BALANCE",
                          "operator":"GREATER_THAN_OR_EQUAL",
                          "values":["100000"]
                        }
                      ]
                    }
                  ],
                  "reason":"Target business and funded profiles"
                }
                """.formatted(overrideUser))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userOverrides.length()").value(1))
                .andExpect(jsonPath("$.data.rules.length()").value(2));

        when(evaluationContextClient.resolve(
                any(UUID.class),
                nullable(UUID.class),
                nullable(String.class)
        )).thenAnswer(invocation -> {
            UUID userId = invocation.getArgument(0);
            ProfileType profileType = userId.equals(overrideUser) || userId.equals(businessUser)
                    ? ProfileType.BUSINESS
                    : ProfileType.PERSONAL;
            BigDecimal balance = userId.equals(defaultUser)
                    ? BigDecimal.ZERO
                    : new BigDecimal("100000.00");
            return context(userId, invocation.getArgument(1), profileType, balance);
        });

        evaluateInternal(overrideUser).andExpect(jsonPath("$.data.flags[0].enabled").value(true));
        evaluateInternal(businessUser).andExpect(jsonPath("$.data.flags[0].enabled").value(false));
        evaluateInternal(highBalanceUser).andExpect(jsonPath("$.data.flags[0].enabled").value(true));
        evaluateInternal(defaultUser).andExpect(jsonPath("$.data.flags[0].enabled").value(false));

        api("/api/v1/feature-flags/targeting/get", adminToken, null, """
                {"key":"card.order.enabled"}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userOverrides[0].userId").value(overrideUser.toString()))
                .andExpect(jsonPath("$.data.rules[0].priority").value(10))
                .andExpect(jsonPath("$.data.rules[1].priority").value(20));

        assertThat(dsl.fetchCount(FEATURE_FLAG_TARGETING_AUDIT_LOGS)).isEqualTo(1);
    }

    @Test
    void internalServiceCanEvaluateNonClientFlag() throws Exception {
        UUID userId = UUID.randomUUID();

        api("/api/v1/feature-flags/evaluate", null, INTERNAL_TOKEN, """
                {
                  "keys":["referral.reward.enabled"],
                  "subjectUserId":"%s",
                  "profileId":null,
                  "accountNumber":null
                }
                """.formatted(userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flags[0].key").value("referral.reward.enabled"))
                .andExpect(jsonPath("$.data.flags[0].enabled").value(true));
    }

    @Test
    void updateValidatesReasonAndReturnsNotFoundForUnknownKey() throws Exception {
        String adminToken = token(UUID.randomUUID(), List.of("ADMIN"));

        api("/api/v1/feature-flags/update", adminToken, null, """
                {
                  "key":"card.order.enabled",
                  "enabled":false,
                  "description":null,
                  "reason":" "
                }
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("VALIDATION_ERROR"));

        api("/api/v1/feature-flags/update", adminToken, null, """
                {
                  "key":"unknown.flow.enabled",
                  "enabled":false,
                  "description":null,
                  "reason":"Operational test"
                }
                """)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.context.status.code").value("NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions api(
            String path,
            String token,
            String internalToken,
            String data
    ) throws Exception {
        var request = post(path)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-feature-flag-test",
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
        if (internalToken != null) {
            request.header("X-Internal-Service-Token", internalToken);
        }
        return mockMvc.perform(request);
    }

    private org.springframework.test.web.servlet.ResultActions evaluateInternal(UUID userId) throws Exception {
        return api("/api/v1/feature-flags/evaluate", null, INTERNAL_TOKEN, """
                {
                  "keys":["card.order.enabled"],
                  "subjectUserId":"%s",
                  "profileId":null,
                  "accountNumber":null
                }
                """.formatted(userId))
                .andExpect(status().isOk());
    }

    private String token(UUID actorId, List<String> roles) throws JOSEException {
        return token(actorId, null, roles);
    }

    private String token(UUID actorId, UUID profileId, List<String> roles) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-develop")
                .audience("red-bank-api")
                .subject(actorId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("profile_id", profileId == null ? null : profileId.toString())
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    private FeatureEvaluationContext context(
            UUID userId,
            UUID profileId,
            ProfileType profileType,
            BigDecimal balance
    ) {
        return new FeatureEvaluationContext(
                userId,
                profileId == null ? UUID.randomUUID() : profileId,
                profileType,
                "job-engineer",
                "123456789012345678",
                AccountType.SAVING,
                AccountStatus.ACTIVE,
                "IRR",
                balance,
                balance
        );
    }
}
