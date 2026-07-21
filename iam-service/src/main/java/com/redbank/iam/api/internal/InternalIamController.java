package com.redbank.iam.api.internal;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.iam.api.internal.dto.CreateOtpChallengeRequest;
import com.redbank.iam.api.internal.dto.CreateOtpChallengeResponse;
import com.redbank.iam.application.IamUnauthorizedException;
import com.redbank.iam.application.PhoneOtpChallengeService;
import com.redbank.iam.config.InternalAccessProperties;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/iam")
public class InternalIamController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final PhoneOtpChallengeService phoneOtpChallengeService;
    private final InternalAccessProperties internalAccessProperties;

    public InternalIamController(
            PhoneOtpChallengeService phoneOtpChallengeService,
            InternalAccessProperties internalAccessProperties
    ) {
        this.phoneOtpChallengeService = phoneOtpChallengeService;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/otp-challenge/create")
    public ResponseEntity<ApiResponse<CreateOtpChallengeResponse>> createOtpChallenge(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody CreateOtpChallengeRequest request
    ) {
        authorize(serviceToken, internalAccessProperties.otpChallengeCreateTokenSecret());
        CreateOtpChallengeResponse response = phoneOtpChallengeService.createPhoneChallenge(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    private void authorize(String serviceToken, String expectedToken) {
        if (!expectedToken.equals(serviceToken)) {
            throw new IamUnauthorizedException("Unauthorized");
        }
    }
}
