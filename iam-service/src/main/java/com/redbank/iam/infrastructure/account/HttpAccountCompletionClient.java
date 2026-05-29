package com.redbank.iam.infrastructure.account;

import com.redbank.contracts.account.ProfileSummary;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.iam.application.AccountCompletionClient;
import com.redbank.iam.application.AccountCompletionResult;
import com.redbank.iam.application.IamExternalServiceException;
import com.redbank.iam.config.AccountClientProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpAccountCompletionClient implements AccountCompletionClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final AccountClientProperties properties;

    public HttpAccountCompletionClient(RestClient.Builder restClientBuilder, AccountClientProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public AccountCompletionResult getCompletion(UUID userId) {
        try {
            ApiResponse<AccountCompletionResponse> response = restClient.post()
                    .uri("/internal/v1/accounts/completion/get")
                    .header(INTERNAL_SERVICE_TOKEN_HEADER, properties.internalServiceTokenSecret())
                    .body(new AccountCompletionRequest(userId.toString()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.data() == null) {
                throw new IamExternalServiceException("Account service returned an empty completion response");
            }
            return new AccountCompletionResult(
                    response.data().accountCompletionStatus(),
                    response.data().activeProfile()
            );
        } catch (RestClientException exception) {
            throw new IamExternalServiceException("Account service is unavailable", exception);
        }
    }

    private record AccountCompletionRequest(
            String userId
    ) {
    }

    private record AccountCompletionResponse(
            AccountCompletionStatus accountCompletionStatus,
            ProfileSummary activeProfile
    ) {
    }
}
