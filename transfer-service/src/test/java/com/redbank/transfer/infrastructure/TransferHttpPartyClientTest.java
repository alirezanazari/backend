package com.redbank.transfer.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.redbank.contracts.common.Money;
import com.redbank.contracts.transfer.DestinationType;
import com.redbank.contracts.transfer.TransferMethod;
import com.redbank.transfer.api.v1.dto.CreateTransferRequest;
import com.redbank.transfer.application.ResolvedTransferParties;
import com.redbank.transfer.config.PartyClientProperties;
import com.redbank.transfer.infrastructure.party.HttpTransferPartyClient;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TransferHttpPartyClientTest {
    private static final String SERVICE_TOKEN = "test-internal-service-token";
    private static final String ACCOUNT_TRANSFER_RESOLVE_TOKEN = "test-account-transfer-resolve-token";

    @Test
    void resolvesOwnedAccountSourceAndCardDestination() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        HttpTransferPartyClient client = new HttpTransferPartyClient(
                builder,
                new PartyClientProperties(
                        true,
                        "http://account.test",
                        "http://card.test",
                        SERVICE_TOKEN,
                        ACCOUNT_TRANSFER_RESOLVE_TOKEN
                ),
                new ObjectMapper()
        );
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();

        server.expect(once(), requestTo("http://account.test/internal/v1/account/transfer/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", ACCOUNT_TRANSFER_RESOLVE_TOKEN))
                .andExpect(jsonPath("$.accountNumber").value("111111111111111111"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value(profileId.toString()))
                .andExpect(jsonPath("$.requireOwnership").value(true))
                .andRespond(accountResponse(
                        "account-1",
                        "111111111111111111",
                        "IR001111111111111111111111",
                        "Source Owner"
                ));
        server.expect(once(), requestTo("http://card.test/internal/v1/card/transfer/resolve"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", SERVICE_TOKEN))
                .andExpect(jsonPath("$.cardNumber").value("destination-token"))
                .andExpect(jsonPath("$.requireOwnership").value(false))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "cardId": "card-2",
                            "userId": "destination-user",
                            "profileId": "destination-profile",
                            "accountNumber": "222222222222222222",
                            "maskedCardNumber": "603799******1234"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));
        server.expect(once(), requestTo("http://account.test/internal/v1/account/transfer/resolve"))
                .andExpect(header("X-Internal-Service-Token", ACCOUNT_TRANSFER_RESOLVE_TOKEN))
                .andExpect(jsonPath("$.accountNumber").value("222222222222222222"))
                .andExpect(jsonPath("$.requireOwnership").value(false))
                .andRespond(accountResponse(
                        "account-2",
                        "222222222222222222",
                        "IR002222222222222222222222",
                        "Destination Owner"
                ));

        ResolvedTransferParties parties = client.resolve(
                new CreateTransferRequest(
                        "idem-1",
                        TransferMethod.ACCOUNT_TO_CARD,
                        DestinationType.ACCOUNT,
                        "111111111111111111",
                        null,
                        DestinationType.CARD,
                        null,
                        null,
                        "destination-token",
                        new Money(new BigDecimal("1000.00"), "IRR"),
                        null
                ),
                userId,
                profileId
        );

        assertThat(parties.sourceReference()).isEqualTo("account-1");
        assertThat(parties.sourceAccountNumber()).isEqualTo("111111111111111111");
        assertThat(parties.destinationReference()).isEqualTo("card-2");
        assertThat(parties.destinationAccountNumber()).isEqualTo("222222222222222222");
        assertThat(parties.destination().destinationType()).isEqualTo(DestinationType.CARD);
        assertThat(parties.destination().destinationName()).isEqualTo("Destination Owner");
        assertThat(parties.destination().maskedCardNumber()).isEqualTo("603799******1234");
        server.verify();
    }

    private org.springframework.test.web.client.ResponseCreator accountResponse(
            String accountId,
            String accountNumber,
            String iban,
            String destinationName
    ) {
        return withSuccess("""
                {
                  "context": null,
                  "data": {
                    "accountId": "%s",
                    "userId": "account-user",
                    "profileId": "account-profile",
                    "accountNumber": "%s",
                    "iban": "%s",
                    "currency": "IRR",
                    "destinationName": "%s",
                    "image": null
                  }
                }
                """.formatted(accountId, accountNumber, iban, destinationName), MediaType.APPLICATION_JSON);
    }
}
