package com.redbank.transfer.infrastructure;

import com.redbank.transfer.application.FeatureFlagSubject;
import com.redbank.transfer.config.FeatureFlagClientProperties;
import com.redbank.transfer.infrastructure.featureflag.HttpFeatureFlagClient;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TransferHttpFeatureFlagClientTest {
    private static final String SERVICE_TOKEN = "test-internal-service-token";

    @Test
    void evaluatesTransferFlagsInOneSubjectAwareRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        String accountNumber = "123456789012345678";
        HttpFeatureFlagClient client = new HttpFeatureFlagClient(
                builder,
                new FeatureFlagClientProperties(true, "http://flags.test", SERVICE_TOKEN)
        );

        server.expect(requestTo("http://flags.test/api/v1/feature-flags/evaluate"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", SERVICE_TOKEN))
                .andExpect(jsonPath("$.data.keys.length()").value(2))
                .andExpect(jsonPath("$.data.subjectUserId").value(userId.toString()))
                .andExpect(jsonPath("$.data.profileId").value(profileId.toString()))
                .andExpect(jsonPath("$.data.accountNumber").value(accountNumber))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "flags": [
                              {"key":"transfer.card-to-card.enabled","enabled":true},
                              {"key":"transfer.account-to-account.enabled","enabled":false}
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        Map<String, Boolean> flags = client.evaluate(
                List.of(
                        "transfer.card-to-card.enabled",
                        "transfer.account-to-account.enabled"
                ),
                new FeatureFlagSubject(userId, profileId, accountNumber)
        );

        assertThat(flags).containsEntry("transfer.card-to-card.enabled", true);
        assertThat(flags).containsEntry("transfer.account-to-account.enabled", false);
        server.verify();
    }
}
