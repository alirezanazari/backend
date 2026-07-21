package com.redbank.iam.infrastructure.kyc;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.identity.AccountCompletionStatus;
import com.redbank.iam.application.IamExternalServiceException;
import com.redbank.iam.application.KycCompletionClient;
import com.redbank.iam.config.KycClientProperties;
import java.util.UUID;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpKycCompletionClient implements KycCompletionClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;
    private final KycClientProperties properties;

    public HttpKycCompletionClient(RestClient.Builder restClientBuilder, KycClientProperties properties) {
        this.restClient = restClientBuilder.baseUrl(properties.baseUrl()).build();
        this.properties = properties;
    }

    @Override
    public AccountCompletionStatus getCompletionStatus(UUID userId) {
        try {
            ApiResponse<KycCompletionResponse> response = restClient.post()
                    .uri("/internal/v1/kyc/completion/get")
                    .header(INTERNAL_SERVICE_TOKEN_HEADER, properties.completionGetTokenSecret())
                    .body(new KycCompletionRequest(userId.toString()))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });

            if (response == null || response.data() == null
                    || response.data().accountCompletionStatus() == null) {
                throw new IamExternalServiceException("KYC service returned an empty completion response");
            }
            return response.data().accountCompletionStatus();
        } catch (RestClientException exception) {
            throw new IamExternalServiceException("KYC service is unavailable", exception);
        }
    }

    private record KycCompletionRequest(String userId) {
    }

    private record KycCompletionResponse(AccountCompletionStatus accountCompletionStatus) {
    }
}
