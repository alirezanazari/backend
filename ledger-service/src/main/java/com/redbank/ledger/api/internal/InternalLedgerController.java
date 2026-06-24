package com.redbank.ledger.api.internal;

import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.event.EventHeaders;
import com.redbank.ledger.api.internal.dto.CloseLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.CloseLedgerAccountResponse;
import com.redbank.ledger.api.internal.dto.CreateLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.CreateLedgerAccountResponse;
import com.redbank.ledger.api.internal.dto.GetLedgerBalanceRequest;
import com.redbank.ledger.api.internal.dto.GetLedgerBalanceResponse;
import com.redbank.ledger.api.internal.dto.InternalTransferRequest;
import com.redbank.ledger.api.internal.dto.LedgerTransactionResponse;
import com.redbank.ledger.api.internal.dto.ListLedgerEntriesRequest;
import com.redbank.ledger.api.internal.dto.ListLedgerEntriesResponse;
import com.redbank.ledger.api.internal.dto.ReferralRewardRequest;
import com.redbank.ledger.api.internal.dto.ReopenLedgerAccountRequest;
import com.redbank.ledger.api.internal.dto.ReopenLedgerAccountResponse;
import com.redbank.ledger.application.LedgerService;
import com.redbank.ledger.application.InboxEventProcessor;
import com.redbank.ledger.application.LedgerBusinessRuleException;
import com.redbank.ledger.application.LedgerUnauthorizedException;
import com.redbank.ledger.config.InternalAccessProperties;
import jakarta.validation.Valid;
import java.util.UUID;
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
    private final InboxEventProcessor inboxEventProcessor;
    private final InternalAccessProperties internalAccessProperties;

    public InternalLedgerController(
            LedgerService ledgerService,
            InboxEventProcessor inboxEventProcessor,
            InternalAccessProperties internalAccessProperties
    ) {
        this.ledgerService = ledgerService;
        this.inboxEventProcessor = inboxEventProcessor;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/account/create")
    public ResponseEntity<ApiResponse<CreateLedgerAccountResponse>> createAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestHeader(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @RequestHeader(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Valid @RequestBody CreateLedgerAccountRequest request
    ) {
        authorize(serviceToken);
        CreateLedgerAccountResponse response = processEvent(
                eventId,
                eventType,
                "LEDGER_ACCOUNT_CREATE_REQUESTED",
                "ledger-account-create",
                CreateLedgerAccountResponse.class,
                () -> ledgerService.createAccount(request)
        );
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/account/close")
    public ResponseEntity<ApiResponse<CloseLedgerAccountResponse>> closeAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestHeader(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @RequestHeader(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Valid @RequestBody CloseLedgerAccountRequest request
    ) {
        authorize(serviceToken);
        CloseLedgerAccountResponse response = processEvent(
                eventId,
                eventType,
                "LEDGER_ACCOUNT_CLOSE_REQUESTED",
                "ledger-account-close",
                CloseLedgerAccountResponse.class,
                () -> ledgerService.closeAccount(request)
        );
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/account/reopen")
    public ResponseEntity<ApiResponse<ReopenLedgerAccountResponse>> reopenAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestHeader(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @RequestHeader(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Valid @RequestBody ReopenLedgerAccountRequest request
    ) {
        authorize(serviceToken);
        ReopenLedgerAccountResponse response = processEvent(
                eventId,
                eventType,
                "LEDGER_ACCOUNT_REOPEN_REQUESTED",
                "ledger-account-reopen",
                ReopenLedgerAccountResponse.class,
                () -> ledgerService.reopenAccount(request)
        );
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
        authorizeReferralReward(serviceToken);
        LedgerTransactionResponse response = ledgerService.postReferralReward(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/account/balance/get")
    public ResponseEntity<ApiResponse<GetLedgerBalanceResponse>> getBalance(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody GetLedgerBalanceRequest request
    ) {
        authorize(serviceToken);
        GetLedgerBalanceResponse response = ledgerService.getBalance(request);
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/account/entries/list")
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

    private void authorizeReferralReward(String serviceToken) {
        if (!internalAccessProperties.referralRewardTokenSecret().equals(serviceToken)) {
            throw new LedgerUnauthorizedException("Unauthorized");
        }
    }

    private <T> T processEvent(
            String eventId,
            String eventType,
            String expectedEventType,
            String consumerName,
            Class<T> responseType,
            java.util.function.Supplier<T> handler
    ) {
        if (eventId == null && eventType == null) {
            return handler.get();
        }
        if (eventId == null || !expectedEventType.equals(eventType)) {
            throw new LedgerBusinessRuleException("Invalid event delivery headers");
        }
        try {
            return inboxEventProcessor.process(
                    UUID.fromString(eventId),
                    eventType,
                    consumerName,
                    responseType,
                    handler
            );
        } catch (IllegalArgumentException exception) {
            throw new LedgerBusinessRuleException("Invalid event delivery headers");
        }
    }
}
