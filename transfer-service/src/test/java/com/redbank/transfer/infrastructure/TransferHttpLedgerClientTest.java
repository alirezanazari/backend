package com.redbank.transfer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.common.Money;
import com.redbank.transfer.application.LedgerTransferResult;
import com.redbank.transfer.application.TransferInsufficientFundsException;
import com.redbank.transfer.config.LedgerClientProperties;
import com.redbank.transfer.infrastructure.ledger.HttpLedgerTransferClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TransferHttpLedgerClientTest {
    private static final String INTERNAL_TRANSFER_TOKEN = "test-internal-transfer-token";

    @Test
    void postsInternalTransferAndReturnsLedgerReference() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpLedgerTransferClient client = client(builder);

        server.expect(requestTo("http://ledger.test/internal/v1/ledger/transactions/internal-transfer"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", INTERNAL_TRANSFER_TOKEN))
                .andExpect(jsonPath("$.referenceId").value("transfer-1"))
                .andExpect(jsonPath("$.sourceExternalAccountNumber").value("111111111111111111"))
                .andExpect(jsonPath("$.destinationExternalAccountNumber").value("222222222222222222"))
                .andExpect(jsonPath("$.amount.amount").value(1000))
                .andExpect(jsonPath("$.amount.currency").value("IRR"))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-1"))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "ledgerTransactionId": "ledger-1",
                            "status": "POSTED"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        LedgerTransferResult result = client.post(
                "transfer-1",
                "111111111111111111",
                "222222222222222222",
                new Money(new BigDecimal("1000.00"), "IRR"),
                "idem-1"
        );

        assertThat(result.ledgerTransactionId()).isEqualTo("ledger-1");
        assertThat(result.status()).isEqualTo("POSTED");
        server.verify();
    }

    @Test
    void mapsLedgerInsufficientFundsStatus() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpLedgerTransferClient client = client(builder);

        server.expect(requestTo("http://ledger.test/internal/v1/ledger/transactions/internal-transfer"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "context": {
                                    "requestId": "internal",
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

        assertThatThrownBy(() -> client.post(
                "transfer-1",
                "111111111111111111",
                "222222222222222222",
                new Money(new BigDecimal("1000.00"), "IRR"),
                "idem-1"
        ))
                .isInstanceOf(TransferInsufficientFundsException.class)
                .hasMessage("Insufficient funds");
        server.verify();
    }

    private HttpLedgerTransferClient client(RestClient.Builder builder) {
        return new HttpLedgerTransferClient(
                builder,
                new LedgerClientProperties(true, "http://ledger.test", INTERNAL_TRANSFER_TOKEN),
                new ObjectMapper()
        );
    }
}
