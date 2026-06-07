package com.redbank.card.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.card.api.internal.InternalCardController;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:cardtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.account-client.enabled=true"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class CardControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";
    private static final String INTERNAL_TOKEN = "local-develop-internal-service-token-change-me";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void defaultCardCreationIsIdempotentAndStoresOnlyMaskedPan() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String accountNumber = "123456789012345678";

        JsonNode first = objectMapper.readTree(createDefaultCard(userId, profileId, accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.maskedPan").value(org.hamcrest.Matchers.matchesPattern("\\d{6}\\*{6}\\d{4}")))
                .andReturn()
                .getResponse()
                .getContentAsString());

        createDefaultCard(userId, profileId, accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardId").value(first.path("data").path("cardId").asText()));

        Integer cards = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM cards WHERE account_number = ?",
                Integer.class,
                accountNumber
        );
        Integer histories = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM card_status_history
                        WHERE card_id = ?
                        """,
                Integer.class,
                UUID.fromString(first.path("data").path("cardId").asText())
        );
        assertThat(cards).isEqualTo(1);
        assertThat(histories).isEqualTo(1);
    }

    @Test
    void completedStatusTransitionsAreValidatedAndAudited() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String token = token(userId, profileId, List.of("USER"));
        String adminToken = token(UUID.randomUUID(), null, List.of("ADMIN"));
        JsonNode created = objectMapper.readTree(createDefaultCard(userId, profileId, "223456789012345678")
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String cardId = created.path("data").path("cardId").asText();

        cardAction("/api/v1/card/freeze", token, profileId, """
                {"cardId":"%s","reason":"Lost temporarily"}
                """.formatted(cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FROZEN"));

        cardAction("/api/v1/card/unfreeze", token, profileId, """
                {"cardId":"%s"}
                """.formatted(cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        cardAction("/api/v1/card/block", token, profileId, """
                {"cardId":"%s","reason":"Compromised","reorderRequested":true}
                """.formatted(cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BLOCKED"))
                .andExpect(jsonPath("$.data.reorderRequired").value(true));

        cardAction("/api/v1/card/unblock", token, profileId, """
                {"cardId":"%s","reason":"User cannot do this"}
                """.formatted(cardId))
                .andExpect(status().isUnauthorized());

        cardAction("/api/v1/card/unblock", adminToken, profileId, """
                {"cardId":"%s","reason":"Risk review completed"}
                """.formatted(cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        cardAction("/api/v1/card/expire", adminToken, profileId, """
                {"cardId":"%s","reason":"System expiry"}
                """.formatted(cardId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));

        cardAction("/api/v1/card/freeze", token, profileId, """
                {"cardId":"%s","reason":"Too late"}
                """.formatted(cardId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));

        Integer histories = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM card_status_history WHERE card_id = ?",
                Integer.class,
                UUID.fromString(cardId)
        );
        assertThat(histories).isEqualTo(6);
    }

    @Test
    void userCannotChangeAnotherUsersCard() throws Exception {
        UUID ownerUserId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        JsonNode created = objectMapper.readTree(createDefaultCard(
                ownerUserId,
                profileId,
                "323456789012345678"
        ).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        cardAction(
                "/api/v1/card/freeze",
                token(UUID.randomUUID(), profileId, List.of("USER")),
                profileId,
                """
                        {"cardId":"%s","reason":"Not mine"}
                        """.formatted(created.path("data").path("cardId").asText())
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.context.status.code").value("NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions createDefaultCard(
            UUID userId,
            UUID profileId,
            String accountNumber
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/cards/default/create")
                .header(InternalCardController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "accountNumber": "%s",
                          "userId": "%s",
                          "profileId": "%s"
                        }
                        """.formatted(accountNumber, userId, profileId)));
    }

    private org.springframework.test.web.servlet.ResultActions cardAction(
            String path,
            String token,
            UUID profileId,
            String data
    ) throws Exception {
        return mockMvc.perform(post(path)
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-card-action",
                            "account": {
                              "accountNumber": null,
                              "profileId": "%s"
                            },
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": %s
                        }
                        """.formatted(profileId, data)));
    }

    private String token(UUID userId, UUID profileId, List<String> roles) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-develop")
                .audience("red-bank-api")
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("profile_id", profileId == null ? null : profileId.toString())
                .claim("device_id", UUID.randomUUID().toString())
                .claim("roles", roles)
                .claim("scope", List.of())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
