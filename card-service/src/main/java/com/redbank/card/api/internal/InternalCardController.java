package com.redbank.card.api.internal;

import com.redbank.card.api.internal.dto.CreateDefaultCardRequest;
import com.redbank.card.api.internal.dto.CardAccountLifecycleRequest;
import com.redbank.card.api.internal.dto.CardAccountLifecycleResponse;
import com.redbank.card.application.CardService;
import com.redbank.card.application.CardUnauthorizedException;
import com.redbank.card.config.InternalAccessProperties;
import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.envelope.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/cards")
public class InternalCardController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final CardService cardService;
    private final InternalAccessProperties internalAccessProperties;

    public InternalCardController(CardService cardService, InternalAccessProperties internalAccessProperties) {
        this.cardService = cardService;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/default/create")
    public ResponseEntity<ApiResponse<CardSummary>> createDefaultCard(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody CreateDefaultCardRequest request
    ) {
        authorize(serviceToken);
        CardSummary response = cardService.createDefaultCard(request);
        return ResponseEntity.ok(ApiResponse.success("internal", request.profileId(), response));
    }

    @PostMapping("/account/close")
    public ResponseEntity<ApiResponse<CardAccountLifecycleResponse>> closeAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody CardAccountLifecycleRequest request
    ) {
        authorize(serviceToken);
        CardAccountLifecycleResponse response = cardService.closeAccount(request.accountNumber());
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/account/reopen")
    public ResponseEntity<ApiResponse<CardAccountLifecycleResponse>> reopenAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody CardAccountLifecycleRequest request
    ) {
        authorize(serviceToken);
        CardAccountLifecycleResponse response = cardService.reopenAccount(request.accountNumber());
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    private void authorize(String serviceToken) {
        if (!internalAccessProperties.serviceTokenSecret().equals(serviceToken)) {
            throw new CardUnauthorizedException("Unauthorized");
        }
    }
}
