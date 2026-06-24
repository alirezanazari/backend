package com.redbank.featureflag.infrastructure;

import com.redbank.featureflag.config.AccountContextClientProperties;
import com.redbank.featureflag.domain.FeatureEvaluationContext;
import com.redbank.featureflag.infrastructure.account.HttpFeatureEvaluationContextClient;
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

class FeatureFlagHttpAccountContextClientTest {
    private static final String SERVICE_TOKEN = "test-internal-service-token";
    private static final String FEATURE_EVALUATION_CONTEXT_GET_TOKEN = "test-feature-evaluation-context-get-token";

    @Test
    void sendsFeatureEvaluationContextScopedTokenToAccountService() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        UUID userId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        HttpFeatureEvaluationContextClient client = new HttpFeatureEvaluationContextClient(
                builder,
                new AccountContextClientProperties(
                        true,
                        "http://account.test",
                        SERVICE_TOKEN,
                        FEATURE_EVALUATION_CONTEXT_GET_TOKEN
                )
        );

        server.expect(once(), requestTo("http://account.test/internal/v1/account/feature-evaluation-context/get"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Service-Token", FEATURE_EVALUATION_CONTEXT_GET_TOKEN))
                .andExpect(jsonPath("$.userId").value(userId.toString()))
                .andExpect(jsonPath("$.profileId").value(profileId.toString()))
                .andExpect(jsonPath("$.accountNumber").value("123456789012345678"))
                .andRespond(withSuccess("""
                        {
                          "context": null,
                          "data": {
                            "userId": "%s",
                            "profileId": "%s",
                            "profileType": "PERSONAL",
                            "jobId": "job-engineer",
                            "accountNumber": "123456789012345678",
                            "accountType": "SAVING",
                            "accountStatus": "ACTIVE",
                            "currency": "IRR",
                            "availableBalance": 1000.00,
                            "currentBalance": 1000.00
                          }
                        }
                        """.formatted(userId, profileId), MediaType.APPLICATION_JSON));

        FeatureEvaluationContext context = client.resolve(userId, profileId, "123456789012345678");

        assertThat(context.userId()).isEqualTo(userId);
        assertThat(context.profileId()).isEqualTo(profileId);
        assertThat(context.accountNumber()).isEqualTo("123456789012345678");
        server.verify();
    }
}
