package com.redbank.pfm.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.BalanceSummary;
import com.redbank.contracts.common.Money;
import com.redbank.pfm.application.LedgerPfmClient;
import com.redbank.pfm.application.VaultAccountClient;
import java.math.BigDecimal;
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

import static com.redbank.pfm.jooq.tables.Boxes.BOXES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:pfmtest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.account-client.enabled=true",
        "redbank.ledger-client.enabled=true"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class PfmControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";
    private static final String VAULT_ACCOUNT = "999999999999999999";
    private static final String PRESET_ID = "10000000-0000-0000-0000-000000000001";
    private static final String TRAVEL_PRESET_ID = "10000000-0000-0000-0000-000000000002";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private VaultAccountClient vaultAccountClient;

    @MockitoBean
    private LedgerPfmClient ledgerPfmClient;

    @BeforeEach
    void clean() {
        dsl.deleteFrom(BOXES).execute();
        reset(vaultAccountClient, ledgerPfmClient);
        when(ledgerPfmClient.getBalance(anyString())).thenReturn(balance("250000.00"));
    }

    @Test
    void listsActivePresetsWithDefaultLanguageFallback() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        api(
                "/api/v1/pfm/box-presets/list",
                token(userId, profileId),
                profileId,
                """
                {"language":"fa-IR"}
                """
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.presets.length()").value(3))
                .andExpect(jsonPath("$.data.presets[0].presetId").value(PRESET_ID))
                .andExpect(jsonPath("$.data.presets[0].title").value("Emergency fund"));
    }

    @Test
    void createsVaultBackedBoxListsBalanceAndEditsMetadata() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String jwt = token(userId, profileId);
        when(vaultAccountClient.create(userId, profileId, "IRR"))
                .thenReturn(vault(profileId, VAULT_ACCOUNT));

        String response = api(
                "/api/v1/pfm/boxes/create",
                jwt,
                profileId,
                """
                {
                  "title":"Rainy day",
                  "presetId":"%s",
                  "image":null,
                  "currency":"irr"
                }
                """.formatted(PRESET_ID)
        )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.context.status.code").value("CREATED"))
                .andExpect(jsonPath("$.data.account.type").value("VAULT"))
                .andExpect(jsonPath("$.data.box.accountNumber").value(VAULT_ACCOUNT))
                .andExpect(jsonPath("$.data.box.title").value("Rainy day"))
                .andExpect(jsonPath("$.data.box.image.alt").value("Emergency fund"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String boxId = extract(response, "\"boxId\":\"");
        assertThat(dsl.select(BOXES.ACCOUNT_NUMBER, BOXES.PRESET_ID)
                .from(BOXES)
                .where(BOXES.ID.eq(UUID.fromString(boxId)))
                .fetchOne())
                .satisfies(record -> {
                    assertThat(record.get(BOXES.ACCOUNT_NUMBER)).isEqualTo(VAULT_ACCOUNT);
                    assertThat(record.get(BOXES.PRESET_ID)).isEqualTo(UUID.fromString(PRESET_ID));
                });
        verify(ledgerPfmClient).createAccount(VAULT_ACCOUNT, userId, "IRR");

        api(
                "/api/v1/pfm/boxes/list",
                jwt,
                profileId,
                """
                {"statuses":["ACTIVE"],"includeBalance":true}
                """
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.boxes.length()").value(1))
                .andExpect(jsonPath("$.data.boxes[0].balance.currentBalance.amount")
                        .value(250000.00));

        api(
                "/api/v1/pfm/boxes/edit",
                jwt,
                profileId,
                """
                {
                  "boxId":"%s",
                  "title":"Summer trip",
                  "presetId":"%s",
                  "image":null
                }
                """.formatted(boxId, TRAVEL_PRESET_ID)
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.box.title").value("Summer trip"))
                .andExpect(jsonPath("$.data.box.image.alt").value("Travel"));
    }

    @Test
    void anotherProfileCannotEditBox() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(vaultAccountClient.create(userId, profileId, "IRR"))
                .thenReturn(vault(profileId, VAULT_ACCOUNT));

        String response = api(
                "/api/v1/pfm/boxes/create",
                token(userId, profileId),
                profileId,
                """
                {"title":"Private","presetId":null,"image":null,"currency":"IRR"}
                """
        ).andReturn().getResponse().getContentAsString();
        String boxId = extract(response, "\"boxId\":\"");

        UUID otherProfileId = UUID.randomUUID();
        api(
                "/api/v1/pfm/boxes/edit",
                token(userId, otherProfileId),
                otherProfileId,
                """
                {"boxId":"%s","title":"Changed","presetId":null,"image":null}
                """.formatted(boxId)
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.context.status.code").value("NOT_FOUND"));
    }

    private org.springframework.test.web.servlet.ResultActions api(
            String path,
            String token,
            UUID profileId,
            String data
    ) throws Exception {
        return mockMvc.perform(post(path)
                .contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .content("""
                        {
                          "context": {
                            "requestId": "req-pfm-test",
                            "account": {
                              "accountNumber": null,
                              "profileId": "%s"
                            },
                            "os": null,
                            "app": {
                              "version": "1.0.0",
                              "buildNumber": "1",
                              "isRooted": false,
                              "language": "en-US",
                              "theme": "dark"
                            },
                            "device": null,
                            "network": null
                          },
                          "data": %s
                        }
                        """.formatted(profileId, data)));
    }

    private AccountSummary vault(UUID profileId, String accountNumber) {
        return new AccountSummary(
                profileId.toString(),
                accountNumber,
                "IR009999999999999999999999",
                AccountType.VAULT,
                AccountStatus.ACTIVE,
                "IRR",
                null
        );
    }

    private BalanceSummary balance(String amount) {
        Money money = new Money(new BigDecimal(amount), "IRR");
        return new BalanceSummary(money, money, Instant.parse("2026-06-11T12:00:00Z"));
    }

    private String extract(String response, String marker) {
        int start = response.indexOf(marker) + marker.length();
        return response.substring(start, response.indexOf('"', start));
    }

    private String token(UUID userId, UUID profileId) throws JOSEException {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("red-bank-develop")
                .audience("red-bank-api")
                .subject(userId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plusSeconds(900)))
                .claim("profile_id", profileId.toString())
                .claim("device_id", UUID.randomUUID().toString())
                .claim("roles", List.of("USER"))
                .claim("scope", List.of())
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(JWT_SECRET.getBytes(StandardCharsets.UTF_8)));
        return jwt.serialize();
    }
}
