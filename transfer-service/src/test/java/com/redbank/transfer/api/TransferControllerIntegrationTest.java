package com.redbank.transfer.api;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferDestinationSummary;
import com.redbank.transfer.application.FeatureFlagClient;
import com.redbank.transfer.application.FeatureFlagSubject;
import com.redbank.transfer.application.LedgerTransferClient;
import com.redbank.transfer.application.LedgerTransferResult;
import com.redbank.transfer.application.ResolvedTransferParties;
import com.redbank.transfer.application.TransferInsufficientFundsException;
import com.redbank.transfer.application.TransferPartyClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
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

import static com.redbank.transfer.jooq.tables.IdempotencyKeys.IDEMPOTENCY_KEYS;
import static com.redbank.transfer.jooq.tables.TransferStatusHistory.TRANSFER_STATUS_HISTORY;
import static com.redbank.transfer.jooq.tables.Transfers.TRANSFERS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:transfertest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "redbank.jwt.signing-secret=test-jwt-signing-secret-change-me-32bytes",
        "redbank.feature-flag-client.enabled=true",
        "redbank.party-client.enabled=true",
        "redbank.ledger-client.enabled=true"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class TransferControllerIntegrationTest {
    private static final String JWT_SECRET = "test-jwt-signing-secret-change-me-32bytes";
    private static final String SOURCE_ACCOUNT = "111111111111111111";
    private static final String DESTINATION_ACCOUNT = "222222222222222222";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DSLContext dsl;

    @MockitoBean
    private FeatureFlagClient featureFlagClient;

    @MockitoBean
    private TransferPartyClient transferPartyClient;

    @MockitoBean
    private LedgerTransferClient ledgerTransferClient;

    @BeforeEach
    void clean() {
        dsl.deleteFrom(TRANSFER_STATUS_HISTORY).execute();
        dsl.deleteFrom(IDEMPOTENCY_KEYS).execute();
        dsl.deleteFrom(TRANSFERS).execute();
        reset(featureFlagClient, transferPartyClient, ledgerTransferClient);
        when(featureFlagClient.evaluate(any(), any(FeatureFlagSubject.class)))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(0);
                    return keys.stream()
                            .collect(java.util.stream.Collectors.toMap(key -> key, key -> true));
                });
        when(transferPartyClient.resolve(any(), any(), any())).thenAnswer(invocation -> {
            var request = (com.redbank.transfer.api.v1.dto.CreateTransferRequest) invocation.getArgument(0);
            String destinationAccount = request.destinationAccountNumber();
            return parties(destinationAccount, "Destination " + destinationAccount.substring(0, 3));
        });
        when(ledgerTransferClient.post(anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(new LedgerTransferResult("ledger-transaction-1", "POSTED"));
    }

    @Test
    void methodListRespectsFeatureFlags() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(featureFlagClient.evaluate(any(), any(FeatureFlagSubject.class)))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(0);
                    return keys.stream()
                            .collect(java.util.stream.Collectors.toMap(
                                    key -> key,
                                    key -> !"transfer.account-to-account.enabled".equals(key)
                            ));
                });

        api(
                "/api/v1/transfers/methods/list",
                token(userId, profileId),
                profileId,
                null,
                """
                {"sourceType":null,"destinationType":null}
                """
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.methods.length()").value(4))
                .andExpect(jsonPath(
                        "$.data.methods[?(@.method == 'ACCOUNT_TO_ACCOUNT')].enabled"
                ).value(false));
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsSameTransferWithoutPostingAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        String firstResponse = api(
                "/api/v1/transfers/create",
                token(userId, profileId),
                profileId,
                "transfer-key-1",
                createData("transfer-key-1", DESTINATION_ACCOUNT, "1000")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.ledgerTransactionId").value("ledger-transaction-1"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String secondResponse = api(
                "/api/v1/transfers/create",
                token(userId, profileId),
                profileId,
                "transfer-key-1",
                createData("transfer-key-1", DESTINATION_ACCOUNT, "1000")
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCESS"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(firstResponse).contains(extractTransferId(secondResponse));
        assertThat(dsl.fetchCount(TRANSFERS)).isEqualTo(1);
        assertThat(dsl.fetchCount(IDEMPOTENCY_KEYS)).isEqualTo(1);
        assertThat(dsl.fetchCount(TRANSFER_STATUS_HISTORY)).isEqualTo(3);
        verify(ledgerTransferClient, times(1))
                .post(anyString(), eq(SOURCE_ACCOUNT), eq(DESTINATION_ACCOUNT), any(), eq("transfer-key-1"));
    }

    @Test
    void sameIdempotencyKeyWithDifferentRequestReturnsDuplicateRequest() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String jwt = token(userId, profileId);

        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "transfer-key-2",
                createData("transfer-key-2", DESTINATION_ACCOUNT, "1000")
        ).andExpect(status().isOk());

        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "transfer-key-2",
                createData("transfer-key-2", DESTINATION_ACCOUNT, "2000")
        )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.context.status.code").value("DUPLICATE_REQUEST"));

        assertThat(dsl.fetchCount(TRANSFERS)).isEqualTo(1);
        verify(ledgerTransferClient, times(1))
                .post(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void insufficientFundsIsPersistedAndReplayedWithoutCallingLedgerAgain() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String jwt = token(userId, profileId);
        when(ledgerTransferClient.post(anyString(), anyString(), anyString(), any(), anyString()))
                .thenThrow(new TransferInsufficientFundsException("Insufficient funds"));

        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "transfer-key-3",
                createData("transfer-key-3", DESTINATION_ACCOUNT, "1000")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("INSUFFICIENT_FUNDS"));

        assertThat(dsl.select(TRANSFERS.STATUS)
                .from(TRANSFERS)
                .fetchOne(TRANSFERS.STATUS)).isEqualTo("FAILED");
        assertThat(dsl.fetchCount(TRANSFER_STATUS_HISTORY)).isEqualTo(3);

        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "transfer-key-3",
                createData("transfer-key-3", DESTINATION_ACCOUNT, "1000")
        )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("INSUFFICIENT_FUNDS"));

        verify(ledgerTransferClient, times(1))
                .post(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void disabledMethodDoesNotCreateTransferOrIdempotencyRecord() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        when(featureFlagClient.evaluate(any(), any(FeatureFlagSubject.class)))
                .thenReturn(Map.of("transfer.account-to-account.enabled", false));

        api(
                "/api/v1/transfers/create",
                token(userId, profileId),
                profileId,
                "transfer-key-4",
                createData("transfer-key-4", DESTINATION_ACCOUNT, "1000")
        )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.context.status.code").value("FEATURE_DISABLED"));

        assertThat(dsl.fetchCount(TRANSFERS)).isZero();
        assertThat(dsl.fetchCount(IDEMPOTENCY_KEYS)).isZero();
    }

    @Test
    void recentListContainsOnlySuccessfulUniqueDestinations() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String jwt = token(userId, profileId);
        String anotherDestination = "333333333333333333";

        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "recent-key-1",
                createData("recent-key-1", DESTINATION_ACCOUNT, "1000")
        ).andExpect(status().isOk());
        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "recent-key-2",
                createData("recent-key-2", DESTINATION_ACCOUNT, "2000")
        ).andExpect(status().isOk());
        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "recent-key-3",
                createData("recent-key-3", anotherDestination, "3000")
        ).andExpect(status().isOk());

        when(ledgerTransferClient.post(anyString(), anyString(), anyString(), any(), eq("recent-key-4")))
                .thenThrow(new TransferInsufficientFundsException("Insufficient funds"));
        api(
                "/api/v1/transfers/create",
                jwt,
                profileId,
                "recent-key-4",
                createData("recent-key-4", "444444444444444444", "4000")
        ).andExpect(status().isBadRequest());

        api(
                "/api/v1/transfers/recent/list",
                jwt,
                profileId,
                null,
                """
                {"limit":10}
                """
        )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].destinationAccountOrCardNumber")
                        .value(anotherDestination))
                .andExpect(jsonPath("$.data.items[1].destinationAccountOrCardNumber")
                        .value(DESTINATION_ACCOUNT));
    }

    private ResolvedTransferParties parties(String destinationAccount, String destinationName) {
        return new ResolvedTransferParties(
                "source-account-id",
                SOURCE_ACCOUNT,
                "destination-account-id-" + destinationAccount,
                destinationAccount,
                new TransferDestinationSummary(
                        DestinationType.ACCOUNT,
                        destinationName,
                        null,
                        destinationAccount,
                        null,
                        null
                )
        );
    }

    private org.springframework.test.web.servlet.ResultActions api(
            String path,
            String token,
            UUID profileId,
            String idempotencyKey,
            String data
    ) throws Exception {
        var request = post(path)
                .contentType("application/json")
                .header("Authorization", "Bearer " + token)
                .content("""
                        {
                          "context": {
                            "requestId": "req-transfer-test",
                            "account": {
                              "accountNumber": "%s",
                              "profileId": "%s"
                            },
                            "os": null,
                            "app": null,
                            "device": null,
                            "network": null
                          },
                          "data": %s
                        }
                        """.formatted(SOURCE_ACCOUNT, profileId, data));
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mockMvc.perform(request);
    }

    private String createData(String idempotencyKey, String destinationAccount, String amount) {
        return """
                {
                  "idempotencyKey":"%s",
                  "method":"ACCOUNT_TO_ACCOUNT",
                  "sourceType":"ACCOUNT",
                  "sourceAccountNumber":"%s",
                  "sourceCardNumber":null,
                  "destinationType":"ACCOUNT",
                  "destinationAccountNumber":"%s",
                  "destinationIban":null,
                  "destinationCardNumber":null,
                  "amount":{"amount":%s,"currency":"IRR"},
                  "description":"Test transfer"
                }
                """.formatted(idempotencyKey, SOURCE_ACCOUNT, destinationAccount, amount);
    }

    private String extractTransferId(String response) {
        int start = response.indexOf("\"transferId\":\"") + "\"transferId\":\"".length();
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
