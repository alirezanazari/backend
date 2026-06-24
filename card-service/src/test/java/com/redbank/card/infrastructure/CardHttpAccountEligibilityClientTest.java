package com.redbank.card.infrastructure;

import com.redbank.card.application.EligibleAccount;
import com.redbank.card.config.AccountClientProperties;
import com.redbank.card.infrastructure.account.HttpAccountEligibilityClient;
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

class CardHttpAccountEligibilityClientTest {
    private static final String SERVICE_TOKEN = "test-internal-service-token";
    private static final String CARD_ELIGIBILITY_GET_TOKEN = "test-card-eligibility-get-token";

    @Test
    void sendsCardEligibilityScopedTokenToAccountService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        HttpAccountEligibilityClient client = new HttpAccountEligibilityClient(
                builder,
                new AccountClientProperties(
                        true,
                        "http://account.test",
                        SERVICE_TOKEN,
                        CARD_ELIGIBILITY_GET_TOKEN
                )
        );

        server.expect(once(), requestTo("http://account.test/internal/v1/account/card/eligibility/get"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", CARD_ELIGIBILITY_GET_TOKEN))
                .andExpect(jsonPath("$.accountNumber").value("123456789012345678"))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value(profileId.toString()))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "accountNumber": "123456789012345678",
                            "userId": "%s",
                            "profileId": "%s",
                            "type": "SAVING",
                            "status": "ACTIVE",
                            "currency": "IRR",
                            "eligible": true
                          }
                        }
                        """.formatted(userId, profileId), MediaType.APPLICATION_JSON));

        EligibleAccount account = client.requireEligible("123456789012345678", userId, profileId);

        assertThat(account.accountNumber()).isEqualTo("123456789012345678");
        assertThat(account.currency()).isEqualTo("IRR");
        server.verify();
    }
}
