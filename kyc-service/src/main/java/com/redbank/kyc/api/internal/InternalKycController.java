package com.redbank.kyc.api.internal;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.kyc.api.internal.dto.GetKycCompletionRequest;
import com.redbank.kyc.api.internal.dto.GetKycCompletionResponse;
import com.redbank.kyc.application.KycService;
import com.redbank.kyc.application.KycUnauthorizedException;
import com.redbank.kyc.config.InternalAccessProperties;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/kyc")
public class InternalKycController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final KycService kycService;
    private final InternalAccessProperties internalAccessProperties;

    public InternalKycController(KycService kycService, InternalAccessProperties internalAccessProperties) {
        this.kycService = kycService;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/completion/get")
    public ResponseEntity<ApiResponse<GetKycCompletionResponse>> getCompletion(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetKycCompletionRequest request
    ) {
        authorize(serviceToken, internalAccessProperties.completionGetTokenSecret());
        GetKycCompletionResponse response = kycService.getCompletion(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    private void authorize(String serviceToken, String expectedToken) {
        if (!expectedToken.equals(serviceToken)) {
            throw new KycUnauthorizedException("Unauthorized");
        }
    }
}
