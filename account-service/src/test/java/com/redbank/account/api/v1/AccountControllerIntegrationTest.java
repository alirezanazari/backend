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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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
    private JdbcTemplate jdbcTemplate;

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
    void internalReferralRewardAccountReturnsActiveAccountForRequestedCurrency() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();

        internalReferralRewardAccount(userId, "IRR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.data.currency").value("IRR"));

        internalReferralRewardAccount(userId, "USD")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.context.status.code").value("NOT_FOUND"));
    }

    @Test
    void internalFeatureEvaluationContextReturnsTrustedProfileAccountAndBalance() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();
        ledgerBalanceClient.amount(new BigDecimal("250000.00"));

        internalFeatureEvaluationContext(userId, profileId, accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.profileId").value(profileId))
                .andExpect(jsonPath("$.data.profileType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.jobId").value("job-engineer"))
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.data.accountType").value("SAVING"))
                .andExpect(jsonPath("$.data.accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.data.currency").value("IRR"))
                .andExpect(jsonPath("$.data.availableBalance").value(250000.00))
                .andExpect(jsonPath("$.data.currentBalance").value(250000.00));
    }

    @Test
    void internalTransferResolutionValidatesOwnershipAndSupportsIban() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();
        String iban = registered.path("data").path("account").path("iban").asText();

        internalTransferAccount(accountNumber, null, userId.toString(), profileId, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(userId.toString()))
                .andExpect(jsonPath("$.data.profileId").value(profileId))
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.data.iban").value(iban))
                .andExpect(jsonPath("$.data.currency").value("IRR"))
                .andExpect(jsonPath("$.data.destinationName").value("Personal"));

        internalTransferAccount(null, iban.toLowerCase(), null, null, false)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber));

        internalTransferAccount(accountNumber, null, UUID.randomUUID().toString(), profileId, true)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.context.status.code").value("NOT_FOUND"));
    }

    @Test
    void internalVaultCreationCreatesActiveVaultForOwnedProfile() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();

        internalCreateVault(userId, profileId, "IRR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.account.profileId").value(profileId))
                .andExpect(jsonPath("$.data.account.type").value("VAULT"))
                .andExpect(jsonPath("$.data.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.account.currency").value("IRR"))
                .andExpect(jsonPath("$.data.account.accountNumber")
                        .value(org.hamcrest.Matchers.matchesPattern("\\d{18}")));

        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM bank_accounts
                        WHERE user_id = ?
                          AND profile_id = ?
                          AND type = 'VAULT'
                        """,
                Integer.class,
                userId,
                UUID.fromString(profileId)
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void registerStoresLedgerAccountCreationOutboxEvent() throws Exception {
        UUID userId = UUID.randomUUID();

        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();

        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_type = 'BANK_ACCOUNT'
                          AND aggregate_id = ?
                          AND event_type = 'LEDGER_ACCOUNT_CREATE_REQUESTED'
                          AND status = 'PENDING'
                          AND payload LIKE ?
                        """,
                Integer.class,
                accountNumber,
                "%" + accountNumber + "%"
        );
        assertThat(count).isEqualTo(1);
    }

    @Test
    void registerStoresDefaultCardCreationOutboxEvent() throws Exception {
        UUID userId = UUID.randomUUID();

        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();

        assertThat(outboxEventCount(accountNumber, "DEFAULT_CARD_CREATE_REQUESTED")).isEqualTo(1);
        assertThat(outboxPayload(accountNumber, "DEFAULT_CARD_CREATE_REQUESTED")).contains("\"form\":\"PHYSICAL\"");
    }

    @Test
    void createProfileCreatesSavingAccountAndDigitalCardRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        register(userId).andExpect(status().isCreated());
        JsonNode created = objectMapper.readTree(createProfile(userToken(userId, null), "Business")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = created.path("data").path("profile").path("profileId").asText();
        String accountNumber = jdbcTemplate.queryForObject(
                "SELECT account_number FROM bank_accounts WHERE profile_id = ?",
                String.class,
                UUID.fromString(profileId)
        );

        assertThat(accountNumber).matches("\\d{18}");
        assertThat(outboxEventCount(accountNumber, "LEDGER_ACCOUNT_CREATE_REQUESTED")).isEqualTo(1);
        assertThat(outboxEventCount(accountNumber, "DEFAULT_CARD_CREATE_REQUESTED")).isEqualTo(1);
        assertThat(outboxPayload(accountNumber, "DEFAULT_CARD_CREATE_REQUESTED")).contains("\"form\":\"DIGITAL\"");
    }

    @Test
    void internalCompletionReturnsKycRequiredWhenProfileAndAccountExist() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();

        internalCompletion(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_REQUIRED"))
                .andExpect(jsonPath("$.data.activeProfile.profileId").value(profileId));
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
    void createProfileRejectsDuplicateTypeAndDisplayName() throws Exception {
        UUID userId = UUID.randomUUID();
        register(userId).andExpect(status().isCreated());
        String userToken = userToken(userId, null);

        createProfile(userToken, "Business")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.status.code").value("CREATED"));

        createProfile(userToken, " business ")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.context.status.code").value("CONFLICT"));
    }

    @Test
    void editProfileUpdatesDisplayNameImageAndAllowsClearingImage() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String userToken = userToken(userId, UUID.fromString(profileId));

        editProfile(userToken, profileId, " باشگاه بدنسازی ", """
                {
                  "lightUrl": "https://cdn.redbank.test/profile-light.png",
                  "darkUrl": "https://cdn.redbank.test/profile-dark.png",
                  "alt": "باشگاه بدنسازی"
                }
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.context.profileId").value(profileId))
                .andExpect(jsonPath("$.data.profile.profileId").value(profileId))
                .andExpect(jsonPath("$.data.profile.displayName").value("باشگاه بدنسازی"))
                .andExpect(jsonPath("$.data.profile.image.lightUrl").value("https://cdn.redbank.test/profile-light.png"))
                .andExpect(jsonPath("$.data.profile.image.darkUrl").value("https://cdn.redbank.test/profile-dark.png"))
                .andExpect(jsonPath("$.data.profile.image.alt").value("باشگاه بدنسازی"));

        editProfile(userToken, profileId, "باشگاه بدنسازی", "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profile.displayName").value("باشگاه بدنسازی"))
                .andExpect(jsonPath("$.data.profile.image").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void editProfileRejectsDuplicateTypeAndDisplayName() throws Exception {
        UUID userId = UUID.randomUUID();
        register(userId).andExpect(status().isCreated());
        String userToken = userToken(userId, null);
        createProfile(userToken, "Business").andExpect(status().isCreated());
        JsonNode shopProfile = objectMapper.readTree(createProfile(userToken, "Shop")
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String shopProfileId = shopProfile.path("data").path("profile").path("profileId").asText();

        editProfile(userToken, shopProfileId, " business ", "null")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.context.status.code").value("CONFLICT"));
    }

    @Test
    void editProfileRejectsInactiveProfile() throws Exception {
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
                .andExpect(status().isOk());

        editProfile(userToken, profileId, "Closed profile", "null")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));
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
                .andExpect(jsonPath("$.data.status").value("CLOSED"))
                .andExpect(jsonPath("$.data.profileId").value(profileId))
                .andExpect(jsonPath("$.data.profileType").value("PERSONAL"))
                .andExpect(jsonPath("$.data.profileClosed").value(true));

        listProfiles(userToken, accountNumber, true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.profiles[0].profileId").value(profileId))
                .andExpect(jsonPath("$.data.profiles[0].active").value(false));

        internalCompletion(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("ACCOUNT_RE_OPEN_REQUIRED"))
                .andExpect(jsonPath("$.data.activeProfile").value(org.hamcrest.Matchers.nullValue()));

        assertThat(outboxEventCount(accountNumber, "LEDGER_ACCOUNT_CLOSE_REQUESTED")).isEqualTo(1);
        assertThat(outboxEventCount(accountNumber, "CARD_ACCOUNT_CLOSE_REQUESTED")).isEqualTo(1);
    }

    @Test
    void openAccountReopensClosedPersonalProfileAndAccount() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();

        closeAccount(userToken(userId, UUID.fromString(profileId)), profileId, accountNumber)
                .andExpect(status().isOk());

        openAccount(userToken(userId, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.context.profileId").value(profileId))
                .andExpect(jsonPath("$.data.profile.profileId").value(profileId))
                .andExpect(jsonPath("$.data.profile.active").value(true))
                .andExpect(jsonPath("$.data.account.accountNumber").value(accountNumber))
                .andExpect(jsonPath("$.data.account.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_REQUIRED"));

        internalCompletion(userId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountCompletionStatus").value("KYC_REQUIRED"))
                .andExpect(jsonPath("$.data.activeProfile.profileId").value(profileId));

        assertThat(outboxEventCount(accountNumber, "LEDGER_ACCOUNT_CLOSE_REQUESTED")).isEqualTo(1);
        assertThat(outboxEventCount(accountNumber, "LEDGER_ACCOUNT_REOPEN_REQUESTED")).isEqualTo(1);
        assertThat(outboxEventCount(accountNumber, "CARD_ACCOUNT_CLOSE_REQUESTED")).isEqualTo(1);
        assertThat(outboxEventCount(accountNumber, "CARD_ACCOUNT_REOPEN_REQUESTED")).isEqualTo(1);
    }

    @Test
    void closeRejectsPersonalProfileWhenBusinessProfileExists() throws Exception {
        UUID userId = UUID.randomUUID();
        JsonNode registered = objectMapper.readTree(register(userId)
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String profileId = registered.path("data").path("profile").path("profileId").asText();
        String accountNumber = registered.path("data").path("account").path("accountNumber").asText();
        String userToken = userToken(userId, UUID.fromString(profileId));

        createProfile(userToken, "Business")
                .andExpect(status().isCreated());

        closeAccount(userToken, profileId, accountNumber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.context.status.message").value("Personal profile cannot be closed while business profiles exist"));
    }

    private org.springframework.test.web.servlet.ResultActions register(UUID userId) throws Exception {
        return mockMvc.perform(post("/api/v1/account/register")
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
        return mockMvc.perform(post("/api/v1/profile/create")
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

    private org.springframework.test.web.servlet.ResultActions editProfile(
            String token,
            String profileId,
            String displayName,
            String imageJson
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/profile/edit")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-profile-edit",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "profileId": "%s",
                            "displayName": "%s",
                            "image": %s
                          }
                        }
                        """.formatted(profileId, displayName, imageJson)));
    }

    private org.springframework.test.web.servlet.ResultActions closeAccount(
            String token,
            String profileId,
            String accountNumber
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/account/close")
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

    private org.springframework.test.web.servlet.ResultActions openAccount(String token) throws Exception {
        return mockMvc.perform(post("/api/v1/account/open")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-account-open",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "reasonText": "test reopen"
                          }
                        }
                        """));
    }

    private org.springframework.test.web.servlet.ResultActions listProfiles(
            String token,
            String accountNumber,
            boolean includeInactive
    ) throws Exception {
        return mockMvc.perform(post("/api/v1/profile/list")
                .header("Authorization", "Bearer " + token)
                .contentType("application/json")
                .content("""
                        {
                          "context": {
                            "requestId": "req-profile-list",
                            "account": null,
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": {
                            "accountNumber": "%s",
                            "includeInactive": %s
                          }
                        }
                        """.formatted(accountNumber, includeInactive)));
    }

    private org.springframework.test.web.servlet.ResultActions internalCompletion(UUID userId) throws Exception {
        return mockMvc.perform(post("/internal/v1/account/completion/get")
                .header("X-Internal-Service-Token", "local-develop-internal-service-token-change-me")
                .contentType("application/json")
                .content("""
                        {
                          "userId": "%s"
                        }
                        """.formatted(userId)));
    }

    private org.springframework.test.web.servlet.ResultActions internalReferralRewardAccount(
            UUID userId,
            String currency
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/account/referral/reward-account/get")
                .header("X-Internal-Service-Token", "local-develop-internal-service-token-change-me")
                .contentType("application/json")
                .content("""
                        {
                          "userId": "%s",
                          "currency": "%s"
                        }
                        """.formatted(userId, currency)));
    }

    private org.springframework.test.web.servlet.ResultActions internalFeatureEvaluationContext(
            UUID userId,
            String profileId,
            String accountNumber
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/account/feature-evaluation-context/get")
                .header("X-Internal-Service-Token", "local-develop-internal-service-token-change-me")
                .contentType("application/json")
                .content("""
                        {
                          "userId": "%s",
                          "profileId": "%s",
                          "accountNumber": "%s"
                        }
                        """.formatted(userId, profileId, accountNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions internalTransferAccount(
            String accountNumber,
            String iban,
            String userId,
            String profileId,
            boolean requireOwnership
    ) throws Exception {
        com.fasterxml.jackson.databind.node.ObjectNode data = objectMapper.createObjectNode();
        data.put("accountNumber", accountNumber);
        data.put("iban", iban);
        data.put("userId", userId);
        data.put("profileId", profileId);
        data.put("requireOwnership", requireOwnership);
        return mockMvc.perform(post("/internal/v1/account/transfer/resolve")
                .header("X-Internal-Service-Token", "local-develop-internal-service-token-change-me")
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(data)));
    }

    private org.springframework.test.web.servlet.ResultActions internalCreateVault(
            UUID userId,
            String profileId,
            String currency
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/account/vault/create")
                .header("X-Internal-Service-Token", "local-develop-internal-service-token-change-me")
                .contentType("application/json")
                .content("""
                        {
                          "userId": "%s",
                          "profileId": "%s",
                          "currency": "%s"
                        }
                        """.formatted(userId, profileId, currency)));
    }

    private int outboxEventCount(String accountNumber, String eventType) {
        Integer count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(*)
                        FROM outbox_events
                        WHERE aggregate_type = 'BANK_ACCOUNT'
                          AND aggregate_id = ?
                          AND event_type = ?
                          AND status = 'PENDING'
                        """,
                Integer.class,
                accountNumber,
                eventType
        );
        return count == null ? 0 : count;
    }

    private String outboxPayload(String accountNumber, String eventType) {
        return jdbcTemplate.queryForObject("""
                        SELECT payload
                        FROM outbox_events
                        WHERE aggregate_type = 'BANK_ACCOUNT'
                          AND aggregate_id = ?
                          AND event_type = ?
                        ORDER BY sequence_number DESC
                        LIMIT 1
                        """,
                String.class,
                accountNumber,
                eventType
        );
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
