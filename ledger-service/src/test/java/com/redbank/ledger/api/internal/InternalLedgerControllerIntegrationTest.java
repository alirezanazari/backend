package com.redbank.ledger.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:ledgertest;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
class InternalLedgerControllerIntegrationTest {
    private static final String INTERNAL_TOKEN = "local-develop-internal-service-token-change-me";
    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(100000000000000000L);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void createAccountIsIdempotentByExternalAccountNumber() throws Exception {
        String accountNumber = nextAccountNumber();
        UUID ownerUserId = UUID.randomUUID();

        JsonNode first = objectMapper.readTree(createAccount(accountNumber, ownerUserId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.context.status.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn()
                .getResponse()
                .getContentAsString());

        createAccount(accountNumber, ownerUserId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId").value(first.path("data").path("ledgerAccountId").asText()))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rewardAndTransferMoveBalancesWithEntries() throws Exception {
        String sourceAccount = nextAccountNumber();
        String destinationAccount = nextAccountNumber();
        createAccount(sourceAccount, UUID.randomUUID()).andExpect(status().isOk());
        createAccount(destinationAccount, UUID.randomUUID()).andExpect(status().isOk());

        referralReward("reward-" + UUID.randomUUID(), sourceAccount, "100.00")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        internalTransfer("transfer-" + UUID.randomUUID(), sourceAccount, destinationAccount, "25.00", "idem-" + UUID.randomUUID())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("POSTED"));

        getBalance(sourceAccount)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.availableBalance.amount").value(75.0))
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(75.0));

        getBalance(destinationAccount)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.availableBalance.amount").value(25.0))
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(25.0));

        listEntries(sourceAccount)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.length()").value(2))
                .andExpect(jsonPath("$.data.page.totalItems").value(2));
    }

    @Test
    void transferRejectsInsufficientFunds() throws Exception {
        String sourceAccount = nextAccountNumber();
        String destinationAccount = nextAccountNumber();
        createAccount(sourceAccount, UUID.randomUUID()).andExpect(status().isOk());
        createAccount(destinationAccount, UUID.randomUUID()).andExpect(status().isOk());

        internalTransfer("transfer-" + UUID.randomUUID(), sourceAccount, destinationAccount, "1.00", "idem-" + UUID.randomUUID())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("INSUFFICIENT_FUNDS"));
    }

    @Test
    void duplicateTransferReferenceDoesNotMoveBalanceTwice() throws Exception {
        String sourceAccount = nextAccountNumber();
        String destinationAccount = nextAccountNumber();
        String referenceId = "transfer-" + UUID.randomUUID();
        String idempotencyKey = "idem-" + UUID.randomUUID();
        createAccount(sourceAccount, UUID.randomUUID()).andExpect(status().isOk());
        createAccount(destinationAccount, UUID.randomUUID()).andExpect(status().isOk());
        referralReward("reward-" + UUID.randomUUID(), sourceAccount, "100.00").andExpect(status().isOk());

        JsonNode first = objectMapper.readTree(internalTransfer(referenceId, sourceAccount, destinationAccount, "10.00", idempotencyKey)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        internalTransfer(referenceId, sourceAccount, destinationAccount, "10.00", idempotencyKey)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerTransactionId").value(first.path("data").path("ledgerTransactionId").asText()));

        getBalance(destinationAccount)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(10.0));
    }

    private org.springframework.test.web.servlet.ResultActions createAccount(String accountNumber, UUID ownerUserId) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/accounts/create")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "externalAccountNumber": "%s",
                          "ownerUserId": "%s",
                          "currency": "IRR"
                        }
                        """.formatted(accountNumber, ownerUserId)));
    }

    private org.springframework.test.web.servlet.ResultActions referralReward(
            String referralRewardId,
            String referrerAccount,
            String amount
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/transactions/referral-reward")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "referralRewardId": "%s",
                          "referrerExternalAccountNumber": "%s",
                          "amount": {
                            "amount": %s,
                            "currency": "IRR"
                          }
                        }
                        """.formatted(referralRewardId, referrerAccount, amount)));
    }

    private org.springframework.test.web.servlet.ResultActions internalTransfer(
            String referenceId,
            String sourceAccount,
            String destinationAccount,
            String amount,
            String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/transactions/internal-transfer")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "referenceId": "%s",
                          "sourceExternalAccountNumber": "%s",
                          "destinationExternalAccountNumber": "%s",
                          "amount": {
                            "amount": %s,
                            "currency": "IRR"
                          },
                          "idempotencyKey": "%s"
                        }
                        """.formatted(referenceId, sourceAccount, destinationAccount, amount, idempotencyKey)));
    }

    private org.springframework.test.web.servlet.ResultActions getBalance(String accountNumber) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/accounts/balance/get")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "externalAccountNumber": "%s"
                        }
                        """.formatted(accountNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions listEntries(String accountNumber) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/accounts/entries/list")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "externalAccountNumber": "%s",
                          "page": {
                            "page": 0,
                            "size": 20
                          }
                        }
                        """.formatted(accountNumber)));
    }

    private String nextAccountNumber() {
        return Long.toString(ACCOUNT_SEQUENCE.getAndIncrement());
    }
}
