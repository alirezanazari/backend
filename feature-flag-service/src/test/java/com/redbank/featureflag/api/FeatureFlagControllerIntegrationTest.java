package com.redbank.featureflag.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static com.redbank.featureflag.jooq.tables.FeatureFlagAuditLogs.FEATURE_FLAG_AUDIT_LOGS;
import static com.redbank.featureflag.jooq.tables.FeatureFlags.FEATURE_FLAGS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:featureflagtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.internal.service-token-secret=test-internal-service-token"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class FeatureFlagControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";
    private static final String INTERNAL_TOKEN = "test-internal-service-token";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @BeforeEach
    void resetFlags() {
        dsl.deleteFrom(FEATURE_FLAG_AUDIT_LOGS).execute();
        dsl.update(FEATURE_FLAGS)
                .set(FEATURE_FLAGS.ENABLED, true)
                .set(FEATURE_FLAGS.UPDATED_BY, (UUID) null)
                .set(FEATURE_FLAGS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
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
                  "reason":"Pause card fulfillment"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.flag.key").value("card.order.enabled"))
                .andExpect(jsonPath("$.data.flag.enabled").value(false))
                .andExpect(jsonPath("$.data.flag.description").value("Allows customers to order physical cards."))
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

    private String token(UUID actorId, List<String> roles) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-develop")
                .audience("red-bank-api")
                .subject(actorId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("roles", roles)
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
