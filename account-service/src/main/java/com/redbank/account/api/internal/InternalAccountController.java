package com.redbank.account.api.internal;

import com.redbank.account.api.internal.dto.GetAccountCompletionRequest;
import com.redbank.account.api.internal.dto.GetAccountCompletionResponse;
import com.redbank.account.api.internal.dto.GetCardAccountRequest;
import com.redbank.account.api.internal.dto.GetCardAccountResponse;
import com.redbank.account.api.internal.dto.GetFeatureEvaluationContextRequest;
import com.redbank.account.api.internal.dto.GetFeatureEvaluationContextResponse;
import com.redbank.account.api.internal.dto.GetReferralRewardAccountRequest;
import com.redbank.account.api.internal.dto.GetReferralRewardAccountResponse;
import com.redbank.account.api.internal.dto.ResolveTransferAccountRequest;
import com.redbank.account.api.internal.dto.ResolveTransferAccountResponse;
import com.redbank.account.application.AccountService;
import com.redbank.account.application.AccountUnauthorizedException;
import com.redbank.account.config.InternalAccessProperties;
import com.redbank.contracts.envelope.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/account")
public class InternalAccountController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final AccountService accountService;
    private final InternalAccessProperties internalAccessProperties;

    public InternalAccountController(AccountService accountService, InternalAccessProperties internalAccessProperties) {
        this.accountService = accountService;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/completion/get")
    public ResponseEntity<ApiResponse<GetAccountCompletionResponse>> getCompletion(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetAccountCompletionRequest request
    ) {
        authorize(serviceToken);
        GetAccountCompletionResponse response = accountService.getCompletion(request);
        return ResponseEntity.ok(ApiResponse.success("internal", response.activeProfileId(), response));
    }

    @PostMapping("/card/eligibility/get")
    public ResponseEntity<ApiResponse<GetCardAccountResponse>> getCardAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetCardAccountRequest request
    ) {
        authorize(serviceToken);
        GetCardAccountResponse response = accountService.getCardAccount(request);
        return ResponseEntity.ok(ApiResponse.success("internal", response.profileId(), response));
    }

    @PostMapping("/referral/reward-account/get")
    public ResponseEntity<ApiResponse<GetReferralRewardAccountResponse>> getReferralRewardAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetReferralRewardAccountRequest request
    ) {
        authorize(serviceToken);
        GetReferralRewardAccountResponse response = accountService.getReferralRewardAccount(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/feature-evaluation-context/get")
    public ResponseEntity<ApiResponse<GetFeatureEvaluationContextResponse>> getFeatureEvaluationContext(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetFeatureEvaluationContextRequest request
    ) {
        authorize(serviceToken);
        GetFeatureEvaluationContextResponse response = accountService.getFeatureEvaluationContext(request);
        return ResponseEntity.ok(ApiResponse.success("internal", response.profileId(), response));
    }

    @PostMapping("/transfer/resolve")
    public ResponseEntity<ApiResponse<ResolveTransferAccountResponse>> resolveTransferAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody ResolveTransferAccountRequest request
    ) {
        authorize(serviceToken);
        ResolveTransferAccountResponse response = accountService.resolveTransferAccount(request);
        return ResponseEntity.ok(ApiResponse.success("internal", response.profileId(), response));
    }

    private void authorize(String serviceToken) {
        if (!internalAccessProperties.serviceTokenSecret().equals(serviceToken)) {
            throw new AccountUnauthorizedException("Unauthorized");
        }
    }
}
