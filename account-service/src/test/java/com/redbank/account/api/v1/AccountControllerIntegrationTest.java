package com.redbank.account.api.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.account.application.JwtTokenService;
import com.redbank.account.application.LedgerBalanceClient;
import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Money;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:accounttest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.account.allowed-job-ids=job-engineer,job-student",
        "redbank.account.max-profiles-per-user=5"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class AccountControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MutableLedgerBalanceClient ledgerBalanceClient;

    @BeforeEach
    void resetLedgerBalance() {
        ledgerBalanceClient.amount(BigDecimal.ZERO);
    }

    @Test
    void registerCreatesPersonalProfileAndDefaultSavingAccount() throws Exception {
        UUID userId = UUID.randomUUID();

        register(userId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.status.code").value("CREATED"))
                .andExpect(jsonPath("$.data.profile.type").value("PERSONAL"))
                .andExpect(jsonPath("$.data.profile.active").value(true))
                .andExpect(jsonPath("$.data.account.type").value("SAVING"))
                .andExpect(jsonPath("$.data.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.account.accountNumber").value(org.hamcrest.Matchers.matchesPattern("\\d{18}")))
                .andExpect(jsonPath("$.data.account.iban").value(org.hamcrest.Matchers.startsWith("IR")))
                .andExpect(jsonPath("$.data.ledgerAccountCreated").value(false))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_REQUIRED"));
    }

    @Test
    void createProfileRejectsMoreThanFiveProfiles() throws Exception {
        UUID userId = UUID.randomUUID();
        register(userId).andExpect(status().isCreated());
        String userToken = userToken(userId, null);

        for (int index = 1; index <= 4; index++) {
            createProfile(userToken, "Profile " + index)
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.context.status.code").value("CREATED"));
        }

        createProfile(userToken, "Profile 5")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("LIMIT_EXCEEDED"));
    }

    @Test
    void closeRejectsAccountWhenLedgerBalanceIsNotZero() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();
        String userToken = userToken(userId, UUID.fromString(profileId));

        ledgerBalanceClient.amount(new BigDecimal("100.00"));

        closeAccount(userToken, profileId, accountNumber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));
    }

    @Test
    void closeAccountStoresClosedStatusWhenLedgerBalanceIsZero() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();
        String userToken = userToken(userId, UUID.fromString(profileId));

        closeAccount(userToken, profileId, accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    private org.springframework.test.web.servlet.ResultActions register(UUID userId) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/register")
                .header("Authorization", "Bearer " + onboardingToken(userId))
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-account-register",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "jobId": "job-engineer",
                            "profileDisplayName": "Personal",
                            "profileImage": null,
                            "currency": "IRR"
                          }
                        }
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions createProfile(String token, String displayName) throws Exception {
        return mockMvc.perform(post("/api/v1/profiles/create")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-profile-create",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "type": "BUSINESS",
                            "displayName": "%s",
                            "image": null
                          }
                        }
                        """.formatted(displayName)));
    }

    private org.springframework.test.web.servlet.ResultActions closeAccount(
            String token,
            String profileId,
            String accountNumber
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/accounts/close")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-account-close",
                            "account": {
                              "accountNumber": "%s",
                              "profileId": "%s"
                            },
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "accountNumber": "%s",
                            "reasonText": "test close"
                          }
                        }
                        """.formatted(accountNumber, profileId, accountNumber)));
    }

    private String onboardingToken(UUID userId) throws JOSEException {
        return token(userId, null, List.of("ONBOARDING"), List.of(JwtTokenService.SCOPE_ACCOUNT_REGISTER));
    }

    private String userToken(UUID userId, UUID profileId) throws JOSEException {
        return token(userId, profileId, List.of("USER"), List.of("USER"));
    }

    private String token(UUID userId, UUID profileId, List<String> roles, List<String> scopes) throws JOSEException {
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
                .claim("scope", scopes)
                .build();

        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }

    @TestConfiguration
    static class LedgerTestConfiguration {
        @Bean
        @Primary
        MutableLedgerBalanceClient mutableLedgerBalanceClient(Clock clock) {
            return new MutableLedgerBalanceClient(clock);
        }
    }

    static class MutableLedgerBalanceClient implements LedgerBalanceClient {
        private final Clock clock;
        private BigDecimal amount = BigDecimal.ZERO;

        MutableLedgerBalanceClient(Clock clock) {
            this.clock = clock;
        }

        void amount(BigDecimal amount) {
            this.amount = amount;
        }

        @Override
        public BalanceSummary getBalance(String accountNumber, String currency) {
            Money money = new Money(amount, currency);
            return new BalanceSummary(money, money, clock.instant());
        }
    }
}
