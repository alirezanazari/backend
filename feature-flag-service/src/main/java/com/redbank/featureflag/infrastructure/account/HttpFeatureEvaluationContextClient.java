package com.redbank.featureflag.infrastructure.account;

import com.redbank.contracts.account.AccountStatus;
import com.redbank.contracts.account.AccountType;
import com.redbank.contracts.account.ProfileType;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.featureflag.application.FeatureEvaluationContextClient;
import com.redbank.featureflag.application.FeatureFlagExternalServiceException;
import com.redbank.featureflag.application.FeatureFlagNotFoundException;
import com.redbank.featureflag.config.AccountContextClientProperties;
import com.redbank.featureflag.domain.FeatureEvaluationContext;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.account-context-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpFeatureEvaluationContextClient implements FeatureEvaluationContextClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpFeatureEvaluationContextClient(
            RestClient.Builder restClientBuilder,
            AccountContextClientProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.serviceToken())
                .build();
    }

    @Override
    public FeatureEvaluationContext resolve(UUID userId, UUID profileId, String accountNumber) {
        try {
            ApiResponse<AccountContextResponse> response = restClient.post()
                    .uri("/internal/v1/account/feature-evaluation-context/get")
                    .body(new AccountContextRequest(
                            userId.toString(),
                            profileId == null ? null : profileId.toString(),
                            accountNumber
                    ))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            AccountContextResponse data = response == null ? null : response.data();
            if (data == null) {
                throw new FeatureFlagExternalServiceException("Account context response was empty", null);
            }
            UUID responseUserId = parseUuid(data.userId());
            UUID responseProfileId = parseUuid(data.profileId());
            if (!userId.equals(responseUserId)
                    || profileId != null && !profileId.equals(responseProfileId)
                    || accountNumber != null && !Objects.equals(accountNumber, data.accountNumber())) {
                throw new FeatureFlagExternalServiceException("Account context response did not match subject", null);
            }
            return new FeatureEvaluationContext(
                    userId,
                    responseProfileId,
                    data.profileType(),
                    data.jobId(),
                    data.accountNumber(),
                    data.accountType(),
                    data.accountStatus(),
                    data.currency(),
                    data.availableBalance(),
                    data.currentBalance()
                );
        } catch (HttpClientErrorException.NotFound exception) {
            if (profileId != null || accountNumber != null) {
                throw new FeatureFlagNotFoundException("Evaluation subject context was not found");
            }
            return new FeatureEvaluationContext(
                    userId,
                    profileId,
                    null,
                    null,
                    accountNumber,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        } catch (IllegalArgumentException exception) {
            throw new FeatureFlagExternalServiceException("Account context response was invalid", exception);
        } catch (RestClientException exception) {
            throw new FeatureFlagExternalServiceException("Account context service call failed", exception);
        }
    }

    private UUID parseUuid(String value) {
        return value == null || value.isBlank() ? null : UUID.fromString(value);
    }

    private record AccountContextRequest(
            String userId,
            String profileId,
            String accountNumber
    ) {
    }

    private record AccountContextResponse(
            String userId,
            String profileId,
            ProfileType profileType,
            String jobId,
            String accountNumber,
            AccountType accountType,
            AccountStatus accountStatus,
            String currency,
            BigDecimal availableBalance,
            BigDecimal currentBalance
    ) {
    }
}
