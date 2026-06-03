package com.redbank.ledger.api.internal;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.ledger.api.internal.dto.CreateLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.CreateLedgerAccountResponse;
import com.redbank.ledger.api.internal.dto.GetLedgerBalanceRequest;
import com.redbank.ledger.api.internal.dto.GetLedgerBalanceResponse;
import com.redbank.ledger.api.internal.dto.InternalTransferRequest;
import com.redbank.ledger.api.internal.dto.LedgerTransactionResponse;
import com.redbank.ledger.api.internal.dto.ListLedgerEntriesRequest;
import com.redbank.ledger.api.internal.dto.ListLedgerEntriesResponse;
import com.redbank.ledger.api.internal.dto.ReferralRewardRequest;
import com.redbank.ledger.application.LedgerService;
import com.redbank.ledger.application.LedgerUnauthorizedException;
import com.redbank.ledger.config.InternalAccessProperties;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/ledger")
public class InternalLedgerController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final LedgerService ledgerService;
    private final InternalAccessProperties internalAccessProperties;

    public InternalLedgerController(LedgerService ledgerService, InternalAccessProperties internalAccessProperties) {
        this.ledgerService = ledgerService;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/accounts/create")
    public ResponseEntity<ApiResponse<CreateLedgerAccountResponse>> createAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody CreateLedgerAccountRequest request
    ) {
        authorize(serviceToken);
        CreateLedgerAccountResponse response = ledgerService.createAccount(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/transactions/internal-transfer")
    public ResponseEntity<ApiResponse<LedgerTransactionResponse>> internalTransfer(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody InternalTransferRequest request
    ) {
        authorize(serviceToken);
        LedgerTransactionResponse response = ledgerService.postInternalTransfer(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/transactions/referral-reward")
    public ResponseEntity<ApiResponse<LedgerTransactionResponse>> referralReward(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody ReferralRewardRequest request
    ) {
        authorize(serviceToken);
        LedgerTransactionResponse response = ledgerService.postReferralReward(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/accounts/balance/get")
    public ResponseEntity<ApiResponse<GetLedgerBalanceResponse>> getBalance(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetLedgerBalanceRequest request
    ) {
        authorize(serviceToken);
        GetLedgerBalanceResponse response = ledgerService.getBalance(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/accounts/entries/list")
    public ResponseEntity<ApiResponse<ListLedgerEntriesResponse>> listEntries(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody ListLedgerEntriesRequest request
    ) {
        authorize(serviceToken);
        ListLedgerEntriesResponse response = ledgerService.listEntries(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    private void authorize(String serviceToken) {
        if (!internalAccessProperties.serviceTokenSecret().equals(serviceToken)) {
            throw new LedgerUnauthorizedException("Unauthorized");
        }
    }
}
