package com.redbank.kyc.infrastructure.iam;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.envelope.Status;
import com.redbank.contracts.envelope.StatusCode;
import com.redbank.kyc.application.KycConflictException;
import com.redbank.kyc.application.KycExternalServiceException;
import com.redbank.kyc.application.KycLimitExceededException;
import com.redbank.kyc.application.OtpChallengeClient;
import com.redbank.kyc.application.PhoneOtpChallenge;
import com.redbank.kyc.config.IamClientProperties;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@ConditionalOnProperty(
        prefix = "redbank.iam-client",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class HttpOtpChallengeClient implements OtpChallengeClient {
    private static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final RestClient restClient;

    public HttpOtpChallengeClient(
            RestClient.Builder restClientBuilder,
            IamClientProperties properties
    ) {
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .defaultHeader(INTERNAL_SERVICE_TOKEN_HEADER, properties.otpChallengeCreateToken())
                .build();
    }

    @Override
    public PhoneOtpChallenge createPhoneChallenge(UUID onboardingSessionId, String phoneNumber) {
        try {
            ApiResponse<ChallengeResponse> response = restClient.post()
                    .uri("/internal/v1/iam/otp-challenge/create")
                    .body(new ChallengeRequest(onboardingSessionId.toString(), phoneNumber))
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            ChallengeResponse data = response == null ? null : response.data();
            if (data == null) {
                throw new KycExternalServiceException("IAM service response was empty", null);
            }
            return new PhoneOtpChallenge(
                    data.challengeId(),
                    Instant.parse(data.expiresAt()),
                    Instant.parse(data.resendAvailableAt())
            );
        } catch (HttpClientErrorException exception) {
            throw mapClientError(exception);
        } catch (RuntimeException exception) {
            if (exception instanceof RestClientException restException) {
                throw new KycExternalServiceException("IAM service call failed", restException);
            }
            throw exception;
        }
    }

    private RuntimeException mapClientError(HttpClientErrorException exception) {
        StatusCode statusCode = statusCode(exception);
        if (statusCode == StatusCode.CONFLICT) {
            return new KycConflictException("Phone number already exists");
        }
        if (statusCode == StatusCode.LIMIT_EXCEEDED) {
            return new KycLimitExceededException("OTP resend is not available yet");
        }
        return new KycExternalServiceException("IAM service rejected the OTP challenge request", exception);
    }

    private StatusCode statusCode(HttpClientErrorException exception) {
        try {
            ApiResponse<Void> body = exception.getResponseBodyAs(new ParameterizedTypeReference<>() {
            });
            Status status = body == null || body.context() == null ? null : body.context().status();
            return status == null ? null : status.code();
        } catch (RuntimeException parseFailure) {
            return null;
        }
    }

    private record ChallengeRequest(String onboardingSessionId, String phoneNumber) {
    }

    private record ChallengeResponse(String challengeId, String expiresAt, String resendAvailableAt) {
    }
}
