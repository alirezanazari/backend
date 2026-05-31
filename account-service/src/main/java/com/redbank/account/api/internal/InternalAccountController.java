package com.redbank.account.api.internal;

import com.redbank.account.api.internal.dto.GetAccountCompletionRequest;
import com.redbank.account.api.internal.dto.GetAccountCompletionResponse;
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
        if (!internalAccessProperties.serviceTokenSecret().equals(serviceToken)) {
            throw new AccountUnauthorizedException("Unauthorized");
        }

        GetAccountCompletionResponse response = accountService.getCompletion(request);
        return ResponseEntity.ok(ApiResponse.success("internal", response.activeProfileId(), response));
    }
}
