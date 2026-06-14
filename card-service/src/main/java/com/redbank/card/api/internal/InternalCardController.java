package com.redbank.card.api.internal;

import com.redbank.card.api.internal.dto.CreateDefaultCardRequest;
import com.redbank.card.api.internal.dto.CardAccountLifecycleRequest;
import com.redbank.card.api.internal.dto.CardAccountLifecycleResponse;
import com.redbank.card.api.internal.dto.ResolveTransferCardRequest;
import com.redbank.card.api.internal.dto.ResolveTransferCardResponse;
import com.redbank.card.application.CardService;
import com.redbank.card.application.CardBusinessRuleException;
import com.redbank.card.application.InboxEventProcessor;
import com.redbank.card.application.CardUnauthorizedException;
import com.redbank.card.config.InternalAccessProperties;
import com.redbank.contracts.card.CardSummary;
import com.redbank.contracts.envelope.ApiResponse;
import com.redbank.contracts.event.EventHeaders;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/card")
public class InternalCardController {
    public static final String INTERNAL_SERVICE_TOKEN_HEADER = "X-Internal-Service-Token";

    private final CardService cardService;
    private final InboxEventProcessor inboxEventProcessor;
    private final InternalAccessProperties internalAccessProperties;

    public InternalCardController(
            CardService cardService,
            InboxEventProcessor inboxEventProcessor,
            InternalAccessProperties internalAccessProperties
    ) {
        this.cardService = cardService;
        this.inboxEventProcessor = inboxEventProcessor;
        this.internalAccessProperties = internalAccessProperties;
    }

    @PostMapping("/default/create")
    public ResponseEntity<ApiResponse<CardSummary>> createDefaultCard(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestHeader(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @RequestHeader(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Valid @RequestBody CreateDefaultCardRequest request
    ) {
        authorize(serviceToken);
        CardSummary response = processEvent(
                eventId,
                eventType,
                "DEFAULT_CARD_CREATE_REQUESTED",
                "card-default-create",
                CardSummary.class,
                () -> cardService.createDefaultCard(request)
        );
        return ResponseEntity.ok(ApiResponse.success("internal", request.profileId(), response));
    }

    @PostMapping("/account/close")
    public ResponseEntity<ApiResponse<CardAccountLifecycleResponse>> closeAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestHeader(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @RequestHeader(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Valid @RequestBody CardAccountLifecycleRequest request
    ) {
        authorize(serviceToken);
        CardAccountLifecycleResponse response = processEvent(
                eventId,
                eventType,
                "CARD_ACCOUNT_CLOSE_REQUESTED",
                "card-account-close",
                CardAccountLifecycleResponse.class,
                () -> cardService.closeAccount(request.accountNumber())
        );
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/account/reopen")
    public ResponseEntity<ApiResponse<CardAccountLifecycleResponse>> reopenAccount(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @RequestHeader(name = EventHeaders.EVENT_ID, required = false) String eventId,
            @RequestHeader(name = EventHeaders.EVENT_TYPE, required = false) String eventType,
            @Valid @RequestBody CardAccountLifecycleRequest request
    ) {
        authorize(serviceToken);
        CardAccountLifecycleResponse response = processEvent(
                eventId,
                eventType,
                "CARD_ACCOUNT_REOPEN_REQUESTED",
                "card-account-reopen",
                CardAccountLifecycleResponse.class,
                () -> cardService.reopenAccount(request.accountNumber())
        );
        return ResponseEntity.ok(ApiResponse.success("internal", null, response));
    }

    @PostMapping("/transfer/resolve")
    public ResponseEntity<ApiResponse<ResolveTransferCardResponse>> resolveTransferCard(
            @RequestHeader(name = INTERNAL_SERVICE_TOKEN_HEADER, required = false) String serviceToken,
            @Valid @RequestBody ResolveTransferCardRequest request
    ) {
        authorize(serviceToken);
        ResolveTransferCardResponse response = cardService.resolveTransferCard(request);
        return ResponseEntity.ok(ApiResponse.success("internal", response.profileId(), response));
    }

    private void authorize(String serviceToken) {
        if (!internalAccessProperties.serviceTokenSecret().equals(serviceToken)) {
            throw new CardUnauthorizedException("Unauthorized");
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
            throw new CardBusinessRuleException("Invalid event delivery headers");
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
            throw new CardBusinessRuleException("Invalid event delivery headers");
        }
    }
}
