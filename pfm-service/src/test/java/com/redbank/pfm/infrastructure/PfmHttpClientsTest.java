package com.redbank.pfm.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.account.AccountSummary;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.common.PageRequest;
import com.redbank.contracts.transfer.TransferStatus;
import com.redbank.pfm.application.LedgerPfmClient;
import com.redbank.pfm.application.PfmTransferResult;
import com.redbank.pfm.config.AccountClientProperties;
import com.redbank.pfm.config.LedgerClientProperties;
import com.redbank.pfm.infrastructure.account.HttpVaultAccountClient;
import com.redbank.pfm.infrastructure.ledger.HttpLedgerPfmClient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.redbank.contracts.envelope.RequestContext;
import com.redbank.pfm.application.PfmInsufficientFundsException;
import com.redbank.pfm.config.TransferClientProperties;
import com.redbank.pfm.infrastructure.transfer.HttpPfmTransferClient;



class PfmHttpClientsTest {
    private static final String SERVICE_TOKEN = "test-internal-service-token";
    private static final String VAULT_CREATE_SERVICE_TOKEN = "test-vault-create-service-token";
    private static final String ACCOUNT_NUMBER = "999999999999999999";
    String description = "Add funds to box";

    @Test
    void createsVaultThroughAccountService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpVaultAccountClient client = new HttpVaultAccountClient(
                builder,
                new AccountClientProperties(
                        true,
                        "http://account.test",
                        SERVICE_TOKEN,
                        VAULT_CREATE_SERVICE_TOKEN
                ),
                new ObjectMapper()
        );
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        server.expect(requestTo("http://account.test/internal/v1/account/vault/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", VAULT_CREATE_SERVICE_TOKEN))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value(profileId.toString()))
                .andExpect(jsonPath("$.currency").value("IRR"))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "account": {
                              "profileId": "%s",
                              "accountNumber": "%s",
                              "iban": "IR009999999999999999999999",
                              "type": "VAULT",
                              "status": "ACTIVE",
                              "currency": "IRR",
                              "balance": null
                            }
                          }
                        }
                        """.formatted(profileId, ACCOUNT_NUMBER), MediaType.APPLICATION_JSON));

        AccountSummary account = client.create(userId, profileId, "IRR");

        assertThat(account.accountNumber()).isEqualTo(ACCOUNT_NUMBER);
        assertThat(account.type().name()).isEqualTo("VAULT");
        server.verify();
    }

    @Test
    void createsLedgerAccountAndReadsBalanceAndEntries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpLedgerPfmClient client = new HttpLedgerPfmClient(
                builder,
                new LedgerClientProperties(true, "http://ledger.test", SERVICE_TOKEN),
                new ObjectMapper().findAndRegisterModules()
        );
        UUID userId = UUID.randomUUID();

        server.expect(once(), requestTo("http://ledger.test/internal/v1/ledger/account/create"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", SERVICE_TOKEN))
                .andExpect(jsonPath("$.externalAccountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.ownerUserId").value(userId.toString()))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "ledgerAccountId": "ledger-account-1",
                            "status": "ACTIVE"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://ledger.test/internal/v1/ledger/account/balance/get"))
                .andExpect(jsonPath("$.externalAccountNumber").value(ACCOUNT_NUMBER))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "externalAccountNumber": "%s",
                            "ledgerAccountId": "ledger-account-1",
                            "balance": {
                              "availableBalance": {"amount": 1000.00, "currency": "IRR"},
                              "currentBalance": {"amount": 1000.00, "currency": "IRR"},
                              "updatedAt": "2026-06-11T12:00:00Z"
                            }
                          }
                        }
                        """.formatted(ACCOUNT_NUMBER), MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://ledger.test/internal/v1/ledger/account/entries/list"))
                .andExpect(jsonPath("$.externalAccountNumber").value(ACCOUNT_NUMBER))
                .andExpect(jsonPath("$.fromDate[0]").value(2026))
                .andExpect(jsonPath("$.fromDate[1]").value(6))
                .andExpect(jsonPath("$.fromDate[2]").value(1))
                .andExpect(jsonPath("$.toDate[0]").value(2026))
                .andExpect(jsonPath("$.toDate[1]").value(6))
                .andExpect(jsonPath("$.toDate[2]").value(11))
                .andExpect(jsonPath("$.page.page").value(0))
                .andExpect(jsonPath("$.page.size").value(20))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "entries": [{
                              "entryId": "entry-1",
                              "ledgerTransactionId": "transaction-1",
                              "direction": "CREDIT",
                              "amount": {"amount": 1000.00, "currency": "IRR"},
                              "createdAt": "2026-06-10T12:00:00Z"
                            }],
                            "page": {
                              "page": 0,
                              "size": 20,
                              "totalItems": 1,
                              "hasNext": false
                            }
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        client.createAccount(ACCOUNT_NUMBER, userId, "IRR");
        assertThat(client.getBalance(ACCOUNT_NUMBER).currentBalance().amount())
                .isEqualByComparingTo("1000.00");
        LedgerPfmClient.LedgerEntries entries = client.listEntries(
                ACCOUNT_NUMBER,
                LocalDate.parse("2026-06-01"),
                LocalDate.parse("2026-06-11"),
                new PageRequest(0, 20)
        );

        assertThat(entries.entries()).hasSize(1);
        assertThat(entries.entries().getFirst().ledgerTransactionId()).isEqualTo("transaction-1");
        assertThat(entries.page().totalItems()).isEqualTo(1);
        server.verify();
    }
    @Test
    void transfersMoneyThroughTransferService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        HttpPfmTransferClient client = new HttpPfmTransferClient(
                builder,
                new TransferClientProperties(true, "http://transfer.test"),
                new ObjectMapper().findAndRegisterModules()
        );

        String authorization = "Bearer test-user-token";
        String idempotencyKey = "test-idempotency-key";

        RequestContext context = new RequestContext(
                "request-123",
                null,
                null,
                null,
                null,
                null
        );

        server.expect(
                        once(),
                        requestTo("http://transfer.test/api/v1/transfers/create")
                )
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", authorization))
                .andExpect(header("Idempotency-Key", idempotencyKey))
                .andExpect(jsonPath("$.context.requestId").value("request-123"))
                .andExpect(jsonPath("$.data.idempotencyKey").value(idempotencyKey))
                .andExpect(jsonPath("$.data.sourceAccountNumber")
                        .value("111111111111111111"))
                .andExpect(jsonPath("$.data.destinationAccountNumber")
                        .value("222222222222222222"))
                .andExpect(jsonPath("$.data.amount.amount").value(1000))
                .andRespond(withSuccess("""
                    {
                      "context": null,
                      "data": {
                        "transferId": "transfer-123",
                        "status": "SUCCESS",
                        "ledgerTransactionId": "ledger-123"
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

        PfmTransferResult result = client.transfer(
                authorization,
                context,
                "111111111111111111",
                "222222222222222222",
                new Money(new BigDecimal("1000"), "IRR"),
                idempotencyKey,
                description
        );

        assertThat(result.transferId()).isEqualTo("transfer-123");
        assertThat(result.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(result.ledgerTransactionId()).isEqualTo("ledger-123");

        server.verify();
    }

    @Test
    void mapsTransferInsufficientFundsStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpPfmTransferClient client = new HttpPfmTransferClient(
                builder,
                new TransferClientProperties(true, "http://transfer.test"),
                new ObjectMapper().findAndRegisterModules()
        );
        RequestContext context = new RequestContext("request-123", null, null, null, null, null);

        server.expect(requestTo("http://transfer.test/api/v1/transfers/create"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "context": {
                                    "requestId": "request-123",
                                    "profileId": null,
                                    "status": {
                                      "code": "INSUFFICIENT_FUNDS",
                                      "message": "Insufficient funds",
                                      "httpStatus": 400,
                                      "details": []
                                    }
                                  },
                                  "data": null
                                }
                                """));

        assertThatThrownBy(() -> client.transfer(
                "Bearer test-user-token",
                context,
                "111111111111111111",
                "222222222222222222",
                new Money(new BigDecimal("1000"), "IRR"),
                "test-idempotency-key",
                description
        ))
                .isInstanceOf(PfmInsufficientFundsException.class)
                .hasMessage("Insufficient funds");
        server.verify();
    }
}
