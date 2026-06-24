package com.redbank.kyc.infrastructure.account;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.kyc.application.ActiveReferrerAccount;
import com.redbank.kyc.application.KycExternalServiceException;
import com.redbank.kyc.application.ReferrerAccountClient;
import com.redbank.kyc.config.ReferrerAccountClientProperties;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.referrer-account-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpReferrerAccountClient implements ReferrerAccountClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpReferrerAccountClient(
            RestClient.Builder restClientBuilder,
            ReferrerAccountClientProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.referralRewardAccountGetToken())
                .build();
    }

    @Override
    public ActiveReferrerAccount findActiveAccount(UUID referrerUserId, String currency) {
        try {
            ApiResponse<AccountResponse> response = restClient.post()
                    .uri("/internal/v1/account/referral/reward-account/get")
                    .body(new AccountRequest(referrerUserId.toString(), currency))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            AccountResponse data = response == null ? null : response.data();
            if (data == null) {
                throw new KycExternalServiceException("Account service response was empty", null);
            }
            return new ActiveReferrerAccount(
                    UUID.fromString(data.userId()),
                    data.accountNumber(),
                    data.currency()
            );
        } catch (HttpClientErrorException.NotFound exception) {
            return null;
        } catch (IllegalArgumentException exception) {
            throw new KycExternalServiceException("Account service returned invalid data", exception);
        } catch (RestClientException exception) {
            throw new KycExternalServiceException("Account service call failed", exception);
        }
    }

    private record AccountRequest(String userId, String currency) {
    }

    private record AccountResponse(String userId, String accountNumber, String currency) {
    }
}
