package com.redbank.ledger.api.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.event.EventHeaders;
import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.jooq.DSLContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.redbank.ledger.jooq.tables.LedgerAccounts.LEDGER_ACCOUNTS;
import static com.redbank.ledger.jooq.tables.LedgerBalances.LEDGER_BALANCES;
import static com.redbank.ledger.jooq.tables.InboxEvents.INBOX_EVENTS;
import static com.redbank.ledger.jooq.tables.OutboxEvents.OUTBOX_EVENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "redbank.events.publisher.enabled=false"
})
@ActiveProfiles("develop")
@AutoConfigureMockMvc
@Testcontainers
class InternalLedgerControllerIntegrationTest {
    private static final String INTERNAL_TOKEN = "local-develop-internal-service-token-change-me";
    private static final String INTERNAL_TRANSFER_TOKEN = "local-develop-internal-service-token-change-me";
    private static final AtomicLong ACCOUNT_SEQUENCE = new AtomicLong(100000000000000000L);

    @Container
    @ServiceConnection
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DSLContext dsl;

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
    void duplicateAccountEventReturnsStoredResultWithoutRepeatingProcessing() throws Exception {
        String accountNumber = nextAccountNumber();
        UUID ownerUserId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();

        JsonNode first = objectMapper.readTree(createAccountEvent(accountNumber, ownerUserId, eventId)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());

        createAccountEvent(accountNumber, ownerUserId, eventId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId")
                        .value(first.path("data").path("ledgerAccountId").asText()));

        assertThat(dsl.fetchCount(
                INBOX_EVENTS,
                INBOX_EVENTS.EVENT_ID.eq(eventId)
                        .and(INBOX_EVENTS.CONSUMER_NAME.eq("ledger-account-create"))
                        .and(INBOX_EVENTS.STATUS.eq("PROCESSED"))
        )).isEqualTo(1);
        assertThat(dsl.fetchCount(
                OUTBOX_EVENTS,
                OUTBOX_EVENTS.AGGREGATE_ID.eq(first.path("data").path("ledgerAccountId").asText())
                        .and(OUTBOX_EVENTS.EVENT_TYPE.eq("LEDGER_ACCOUNT_CREATED"))
        )).isEqualTo(1);
    }

    @Test
    void closeAndReopenAccountAreIdempotent() throws Exception {
        String accountNumber = nextAccountNumber();
        UUID ownerUserId = UUID.randomUUID();
        JsonNode created = objectMapper.readTree(createAccount(accountNumber, ownerUserId)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        String ledgerAccountId = created.path("data").path("ledgerAccountId").asText();

        closeAccount(accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId").value(ledgerAccountId))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));
        closeAccount(accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId").value(ledgerAccountId))
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        getBalance(accountNumber)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));

        reopenAccount(accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId").value(ledgerAccountId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
        reopenAccount(accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ledgerAccountId").value(ledgerAccountId))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));

        getBalance(accountNumber)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.availableBalance.amount").value(0.0))
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(0.0));
    }

    @Test
    void closeRejectsNonzeroAvailableOrCurrentBalance() throws Exception {
        String availableAccount = nextAccountNumber();
        createAccount(availableAccount, UUID.randomUUID()).andExpect(status().isOk());
        setBalances(availableAccount, "1.00", "0.00");

        closeAccount(availableAccount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));

        String currentAccount = nextAccountNumber();
        createAccount(currentAccount, UUID.randomUUID()).andExpect(status().isOk());
        setBalances(currentAccount, "0.00", "-1.00");

        closeAccount(currentAccount)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.context.status.code").value("BUSINESS_RULE_VIOLATION"));
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

    @Test
    void concurrentTransfersKeepBalancesConsistent() throws Exception {
        String sourceAccount = nextAccountNumber();
        String firstDestination = nextAccountNumber();
        String secondDestination = nextAccountNumber();
        createAccount(sourceAccount, UUID.randomUUID()).andExpect(status().isOk());
        createAccount(firstDestination, UUID.randomUUID()).andExpect(status().isOk());
        createAccount(secondDestination, UUID.randomUUID()).andExpect(status().isOk());
        referralReward("reward-" + UUID.randomUUID(), sourceAccount, "100.00").andExpect(status().isOk());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Void> first = executor.submit(transferTask(
                    ready,
                    start,
                    "transfer-" + UUID.randomUUID(),
                    sourceAccount,
                    firstDestination,
                    "40.00",
                    "idem-" + UUID.randomUUID()
            ));
            Future<Void> second = executor.submit(transferTask(
                    ready,
                    start,
                    "transfer-" + UUID.randomUUID(),
                    sourceAccount,
                    secondDestination,
                    "30.00",
                    "idem-" + UUID.randomUUID()
            ));

            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        getBalance(sourceAccount)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(30.0));
        getBalance(firstDestination)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(40.0));
        getBalance(secondDestination)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance.currentBalance.amount").value(30.0));
    }

    private org.springframework.test.web.servlet.ResultActions createAccount(String accountNumber, UUID ownerUserId) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/account/create")
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

    private org.springframework.test.web.servlet.ResultActions createAccountEvent(
            String accountNumber,
            UUID ownerUserId,
            UUID eventId
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/account/create")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .header(EventHeaders.EVENT_ID, eventId.toString())
                .header(EventHeaders.EVENT_TYPE, "LEDGER_ACCOUNT_CREATE_REQUESTED")
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

    private org.springframework.test.web.servlet.ResultActions closeAccount(String accountNumber) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/account/close")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "externalAccountNumber": "%s"
                        }
                        """.formatted(accountNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions reopenAccount(String accountNumber) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/account/reopen")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "externalAccountNumber": "%s"
                        }
                        """.formatted(accountNumber)));
    }

    private void setBalances(String accountNumber, String availableBalance, String currentBalance) {
        dsl.update(LEDGER_BALANCES)
                .set(LEDGER_BALANCES.AVAILABLE_BALANCE, new BigDecimal(availableBalance))
                .set(LEDGER_BALANCES.CURRENT_BALANCE, new BigDecimal(currentBalance))
                .where(LEDGER_BALANCES.LEDGER_ACCOUNT_ID.eq(
                        dsl.select(LEDGER_ACCOUNTS.ID)
                                .from(LEDGER_ACCOUNTS)
                                .where(LEDGER_ACCOUNTS.EXTERNAL_ACCOUNT_NUMBER.eq(accountNumber))
                ))
                .execute();
    }

    private org.springframework.test.web.servlet.ResultActions internalTransfer(
            String referenceId,
            String sourceAccount,
            String destinationAccount,
            String amount,
            String idempotencyKey
    ) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/transactions/internal-transfer")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TRANSFER_TOKEN)
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

    private Callable<Void> transferTask(
            CountDownLatch ready,
            CountDownLatch start,
            String referenceId,
            String sourceAccount,
            String destinationAccount,
            String amount,
            String idempotencyKey
    ) {
        return () -> {
            ready.countDown();
            start.await(5, TimeUnit.SECONDS);
            internalTransfer(referenceId, sourceAccount, destinationAccount, amount, idempotencyKey)
                    .andExpect(status().isOk());
            return null;
        };
    }

    private org.springframework.test.web.servlet.ResultActions getBalance(String accountNumber) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/account/balance/get")
                .header(InternalLedgerController.INTERNAL_SERVICE_TOKEN_HEADER, INTERNAL_TOKEN)
                .contentType("application/json")
                .content("""
                        {
                          "externalAccountNumber": "%s"
                        }
                        """.formatted(accountNumber)));
    }

    private org.springframework.test.web.servlet.ResultActions listEntries(String accountNumber) throws Exception {
        return mockMvc.perform(post("/internal/v1/ledger/account/entries/list")
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
